package dev.flowicons.icons

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import dev.flowicons.FlowPaths
import dev.flowicons.settings.FlowIconsSettings
import dev.flowicons.settings.ThemeVariant
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

/**
 * Resolves a [VirtualFile] to a Flow Icons SVG and caches the loaded [Icon].
 *
 * Lookup is O(1): exact filename → file extension (longest suffix first) for
 * files, folder-name for directories, with a default fallback. Icons are loaded
 * from the bundled classpath set, the downloaded premium set, or the generated
 * Flow You set depending on the active variant.
 *
 * The cache is keyed by `(variant, generation, iconId)`; [invalidate] bumps the
 * generation so a settings/license/palette change re-resolves everything.
 */
@Service(Service.Level.APP)
class FlowIconService {

    @Volatile
    private var mapping: Mapping = Mapping.EMPTY

    @Volatile
    var generation: Int = 0
        private set

    private val cache = ConcurrentHashMap<String, Optional<Icon>>()

    init {
        reload()
    }

    private val settings: FlowIconsSettings get() = FlowIconsSettings.getInstance()

    /** Reload the mapping table (base vs premium) and clear the icon cache. */
    fun invalidate() {
        reload()
    }

    private fun reload() {
        mapping = loadMapping()
        generation++
        cache.clear()
    }

    /** Icon for any file or directory; never returns the platform default for unknown files. */
    fun resolve(file: VirtualFile): Icon? {
        val variant = settings.themeVariant
        val isDir = file.isDirectory
        val id = if (isDir) folderId(file.name) else fileId(file.name)
        return iconForId(id, variant, isDir)
    }

    /** Icon for files only (used by the editor-tab provider); directories return null. */
    fun resolveFileOnly(file: VirtualFile): Icon? =
        if (file.isDirectory) null else resolve(file)

    /** Mirrors VSCode `flow-icons.hidesExplorerFolders`: when true, folders show no icon. */
    val hidesFolderIcons: Boolean get() = settings.hidesExplorerFolders

    // -- name -> icon id -----------------------------------------------------

    private fun folderId(name: String): String =
        mapping.folderNames[name.lowercase()] ?: mapping.defaults.folder

    private fun fileId(name: String): String {
        val lower = name.lowercase()
        mapping.fileNames[lower]?.let { return it }
        // Try progressively shorter extensions: "component.test.tsx" -> "test.tsx" -> "tsx".
        var dot = lower.indexOf('.')
        while (dot in 0 until lower.length - 1) {
            mapping.fileExtensions[lower.substring(dot + 1)]?.let { return it }
            dot = lower.indexOf('.', dot + 1)
        }
        return mapping.defaults.file
    }

    // -- icon id -> Icon -----------------------------------------------------

    private fun iconForId(id: String, variant: ThemeVariant, isDir: Boolean): Icon? {
        val key = "${variant.folder}|$generation|$id"
        cache[key]?.let { return it.orElse(null) }

        var icon = loadVariantIcon(id, variant)
        if (icon == null) {
            val fallback = if (isDir) mapping.defaults.folder else mapping.defaults.file
            if (fallback != id) icon = loadVariantIcon(fallback, variant)
        }
        cache[key] = Optional.ofNullable(icon)
        return icon
    }

    private fun loadVariantIcon(id: String, variant: ThemeVariant): Icon? {
        val fileName = "$id.svg"

        // 1. Generated Flow You icons (custom palette) live in the config dir.
        if (variant.isYou) {
            fsIcon(FlowPaths.youDir(variant.folder).resolve(fileName))?.let { return it }
        } else if (settings.hasPremium) {
            // 2. Downloaded premium icons (versioned config dir).
            val base = settings.premiumIconsPath
            if (base.isNotBlank()) {
                fsIcon(Path.of(base).resolve(variant.folder).resolve(fileName))?.let { return it }
            }
        }

        // 3. Bundled base set on the classpath. Explicit paths (no `_dark` sibling)
        //    keep each palette fixed and avoid automatic dark-variant substitution.
        return IconLoader.findIcon("/icons/${variant.folder}/$fileName", javaClass)
    }

    private fun fsIcon(path: Path): Icon? =
        if (Files.exists(path)) {
            try {
                IconLoader.findIcon(path.toUri().toURL())
            } catch (e: Exception) {
                thisLogger().warn("Failed to load Flow icon from $path", e)
                null
            }
        } else {
            null
        }

    // -- mapping loading ------------------------------------------------------

    private fun loadMapping(): Mapping {
        val text = premiumMappingText() ?: bundledMappingText() ?: return Mapping.EMPTY
        val s = settings
        val customization = Customization(
            folderColor = s.folderColor,
            specificFolders = s.specificFolders,
            filesReplacements = s.filesReplacementsJson,
            foldersReplacements = s.foldersReplacementsJson,
            filesAssociations = s.filesAssociationsJson,
            foldersAssociations = s.foldersAssociationsJson,
            activeIconPack = s.activeIconPackJson,
        )
        return try {
            Mapping.parse(text, customization)
        } catch (e: Exception) {
            thisLogger().error("Failed to parse Flow Icons mapping; icons disabled", e)
            Mapping.EMPTY
        }
    }

    /**
     * Effective icon customizations, assembled from the individual VSCode-style
     * settings. (`activeIconPack` is applied via [IconPackApplier].)
     */
    private class Customization(
        val folderColor: String,
        val specificFolders: Boolean,
        val filesReplacements: String,
        val foldersReplacements: String,
        val filesAssociations: String,
        val foldersAssociations: String,
        val activeIconPack: String,
    )

    private fun premiumMappingText(): String? {
        if (!settings.hasPremium) return null
        val p = FlowPaths.premiumMapping
        return if (Files.exists(p)) Files.readString(p, StandardCharsets.UTF_8) else null
    }

    private fun bundledMappingText(): String? =
        javaClass.getResourceAsStream("/flow/mapping.json")
            ?.readBytes()
            ?.toString(StandardCharsets.UTF_8)

    // -- mapping model --------------------------------------------------------

    private class Mapping(
        val defaults: Defaults,
        val fileExtensions: Map<String, String>,
        val fileNames: Map<String, String>,
        val folderNames: Map<String, String>,
    ) {
        class Defaults(val file: String, val folder: String)

        companion object {
            val EMPTY = Mapping(
                Defaults("file", "folder_gray"),
                emptyMap(), emptyMap(), emptyMap(),
            )

            fun parse(json: String, customization: Customization): Mapping {
                val obj = JsonParser.parseString(json).asJsonObject
                val defaultsObj = obj.getAsJsonObject("defaults")
                val defFile = defaultsObj?.get("file")?.asString ?: "file"
                var defFolder = defaultsObj?.get("folder")?.asString ?: "folder_gray"

                val fileExtensions = lowerKeyMap(obj.getAsJsonObject("fileExtensions"))
                val fileNames = lowerKeyMap(obj.getAsJsonObject("fileNames"))
                val folderNames = lowerKeyMap(obj.getAsJsonObject("folderNames"))

                defFolder = applyCustomization(customization, defFolder, fileExtensions, fileNames, folderNames)

                // activeIconPack: toggle framework-specific icon sets on/off.
                IconPackApplier.apply(customization.activeIconPack, fileExtensions, fileNames)

                return Mapping(Defaults(defFile, defFolder), fileExtensions, fileNames, folderNames)
            }

            /** Apply the user's overrides in-place; returns the (possibly changed) default folder id. */
            private fun applyCustomization(
                c: Customization,
                defaultFolder: String,
                ext: MutableMap<String, String>,
                names: MutableMap<String, String>,
                folders: MutableMap<String, String>,
            ): String {
                var defFolder = defaultFolder

                // folderColor: recolor the default (unnamed) directory icon.
                if (c.folderColor.isNotBlank()) defFolder = "folder_${c.folderColor}"

                // specificFolders=false: every directory uses the default folder icon.
                if (!c.specificFolders) folders.clear()

                // filesReplacements: swap one file icon id for another (e.g. rust -> rust-alt).
                parseObject(c.filesReplacements)?.let { repl ->
                    for ((s, v) in repl.entrySet()) {
                        val src = normalizeIcon(s)
                        val tgt = normalizeIcon(v.asString)
                        if (src.isBlank() || tgt.isBlank()) continue
                        for (k in ext.keys) if (ext[k] == src) ext[k] = tgt
                        for (k in names.keys) if (names[k] == src) names[k] = tgt
                    }
                }

                // foldersReplacements: keys/values are base names without the folder_ prefix.
                parseObject(c.foldersReplacements)?.let { repl ->
                    for ((s, v) in repl.entrySet()) {
                        val src = "folder_" + normalizeIcon(s)
                        val tgt = "folder_" + normalizeIcon(v.asString)
                        for (k in folders.keys) if (folders[k] == src) folders[k] = tgt
                    }
                }

                // filesAssociations: Material-style. "*.ext"/"**.ext" -> extension; plain -> filename.
                // Empty value removes. Path-scoped keys ("dir/...") aren't matchable by name and are ignored.
                parseObject(c.filesAssociations)?.let { assoc ->
                    for ((key, v) in assoc.entrySet()) {
                        if (key.isBlank()) continue
                        val lower = key.lowercase()
                        val target = if (v.isJsonPrimitive) normalizeIcon(v.asString) else ""
                        when {
                            lower.startsWith("**.") -> putOrRemove(ext, lower.substring(3), target)
                            lower.startsWith("*.") -> putOrRemove(ext, lower.substring(2), target)
                            !lower.contains('/') -> putOrRemove(names, lower, target)
                        }
                    }
                }

                // foldersAssociations: map folder name -> icon base name (without folder_). Empty removes.
                parseObject(c.foldersAssociations)?.let { assoc ->
                    for ((key, v) in assoc.entrySet()) {
                        if (key.isBlank()) continue
                        val lower = key.lowercase()
                        val target = if (v.isJsonPrimitive) normalizeIcon(v.asString) else ""
                        if (target.isBlank()) folders.remove(lower) else folders[lower] = "folder_$target"
                    }
                }

                return defFolder
            }

            private fun parseObject(json: String): JsonObject? {
                if (json.isBlank()) return null
                return try {
                    JsonParser.parseString(json).asJsonObject
                } catch (e: Exception) {
                    null
                }
            }

            private fun putOrRemove(map: MutableMap<String, String>, key: String, value: String) {
                if (key.isBlank()) return
                if (value.isBlank()) map.remove(key) else map[key] = value
            }

            private fun normalizeIcon(name: String?): String = (name ?: "").replace('-', '_')

            private fun lowerKeyMap(obj: JsonObject?): MutableMap<String, String> {
                if (obj == null) return HashMap()
                val out = HashMap<String, String>(obj.size() * 2)
                for ((k, v) in obj.entrySet()) {
                    if (v.isJsonPrimitive) {
                        val value = v.asString
                        if (value.isNotEmpty()) out[k.lowercase()] = value
                    }
                }
                return out
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(): FlowIconService =
            ApplicationManager.getApplication().getService(FlowIconService::class.java)
    }
}
