package dev.flowicons.flowyou

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.flowicons.FlowPaths
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Regenerates the `you` / `you-light` SVGs from the bundled templates by
 * substituting `--<colorName>` placeholders with the user's palette, mirroring
 * the VSCode extension's "Rebuild Icons" command (and the Zed port's
 * `buildYouIcons`).
 *
 * Output is written to the writable config dir (see [FlowPaths]); the plugin jar
 * itself is read-only.
 */
object FlowYouBuilder {

    private const val TEMPLATE_ROOT = "/flow-you-templates"

    /** Color keys the user is allowed to override. Anything else is dropped. */
    private val ALLOWED = setOf(
        "white", "black", "blue", "brown", "gray", "green", "lime", "orange",
        "pink", "purple", "red", "sky", "teal", "yellow",
        "border", "contrast", "borderOpacity",
    )

    private val BASE_DARK = baseSlate(
        white = "#f8fafc", black = "#1e293b", slate = "#94a3b8", contrast = "#f8fafc",
    )

    private val BASE_LIGHT = baseSlate(
        white = "#f1f5f9", black = "#0f172a", slate = "#64748b", contrast = "#0f172a",
    )

    private fun baseSlate(white: String, black: String, slate: String, contrast: String): Map<String, String> =
        linkedMapOf(
            "white" to white, "black" to black,
            "blue" to slate, "brown" to slate, "gray" to slate, "green" to slate,
            "lime" to slate, "orange" to slate, "pink" to slate, "purple" to slate,
            "red" to slate, "sky" to slate, "teal" to slate, "yellow" to slate,
            "borderOpacity" to "0.1", "contrast" to contrast, "border" to contrast,
        )

    /**
     * Build both Flow You variants for the given palette JSON (may be blank for
     * the default slate).
     *
     * @return total number of SVGs written.
     * @throws com.google.gson.JsonSyntaxException if [youColorsJson] is invalid.
     */
    fun build(youColorsJson: String): Int {
        val userColors = parsePalette(youColorsJson)
        val lightInput = (userColors["light"] as? JsonObject)?.let { filterAllowed(it) } ?: mutableMapOf()
        val dark = filterAllowed(userColors).also { it.remove("light") }
        val light = lightInput

        // Auto-derive missing light entries from dark (darken hex; copy others).
        for ((key, value) in dark) {
            if (key == "border" || key == "contrast") continue
            if (light.containsKey(key)) continue
            light[key] = if (value.startsWith("#")) Colors.darken(value) else value
        }

        // Ensure contrast/border exist (extension's fillColors rule).
        if (!dark.containsKey("contrast") && dark.containsKey("white")) dark["contrast"] = dark["white"]!!
        if (!dark.containsKey("border") && dark.containsKey("contrast")) dark["border"] = dark["contrast"]!!
        if (!light.containsKey("contrast") && light.containsKey("black")) light["contrast"] = light["black"]!!
        if (!light.containsKey("border") && dark.containsKey("border")) {
            light["border"] = Colors.invert(dark["border"]!!)
        } else if (!light.containsKey("border") && light.containsKey("contrast")) {
            light["border"] = light["contrast"]!!
        }

        val templateNames = readManifest()
        var written = 0
        written += writeVariant("you", BASE_DARK + dark, templateNames)
        written += writeVariant("you-light", BASE_LIGHT + light, templateNames)
        return written
    }

    private fun writeVariant(folder: String, merged: Map<String, String>, templateNames: List<String>): Int {
        val destDir = FlowPaths.youDir(folder)
        // Rebuild from scratch so stale icons from a previous palette are removed.
        if (Files.exists(destDir)) destDir.toFile().deleteRecursively()
        Files.createDirectories(destDir)

        // Replace longest names first so `--borderOpacity` is consumed before `--border`.
        val sorted = merged.entries.sortedByDescending { it.key.length }

        var count = 0
        for (name in templateNames) {
            val template = readTemplate(name) ?: continue
            var content = template
            for ((colorName, value) in sorted) {
                content = content.replace("--$colorName", value)
            }
            Files.write(destDir.resolve(name), content.toByteArray(StandardCharsets.UTF_8))
            count++
        }
        return count
    }

    private fun parsePalette(json: String): MutableMap<String, Any> {
        if (json.isBlank()) return mutableMapOf()
        val obj = JsonParser.parseString(json).asJsonObject
        val out = mutableMapOf<String, Any>()
        for ((k, v) in obj.entrySet()) {
            if (k == "light" && v.isJsonObject) {
                out[k] = v.asJsonObject
            } else if (v.isJsonPrimitive) {
                out[k] = v.asString
            }
        }
        return out
    }

    private fun filterAllowed(input: Map<String, Any>): MutableMap<String, String> {
        val out = linkedMapOf<String, String>()
        for ((k, v) in input) {
            if (k in ALLOWED && v is String) out[k] = v
        }
        return out
    }

    private fun filterAllowed(obj: JsonObject): MutableMap<String, String> {
        val out = linkedMapOf<String, String>()
        for ((k, v) in obj.entrySet()) {
            if (k in ALLOWED && v.isJsonPrimitive) out[k] = v.asString
        }
        return out
    }

    private fun readManifest(): List<String> {
        val stream = javaClass.getResourceAsStream("$TEMPLATE_ROOT/index.txt")
            ?: error("Flow You template manifest missing from plugin resources.")
        InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
            return reader.readText().split('\n').map { it.trim() }.filter { it.endsWith(".svg") }
        }
    }

    private fun readTemplate(name: String): String? {
        val stream = javaClass.getResourceAsStream("$TEMPLATE_ROOT/$name") ?: return null
        InputStreamReader(stream, StandardCharsets.UTF_8).use { return it.readText() }
    }
}
