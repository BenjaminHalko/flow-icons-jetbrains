package dev.flowicons.icons

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.thisLogger
import java.nio.charset.StandardCharsets

/**
 * Applies the VSCode `activeIconPack` setting — a map of `pack-name -> boolean`
 * that toggles framework-specific icon sets (angular, bashly, nest, next,
 * roblox).
 *
 * The base mapping already reflects the *default* pack states (e.g. `nest` is
 * off by default), so:
 * - turning a pack OFF removes its associations from the mapping, and
 * - turning a default-off pack ON adds them back from the bundled `icons.json`
 *   association table.
 *
 * Data tables (`/flow/packs.json`, `/flow/icons.json`) are extracted from the
 * VSIX and kept in sync by `tools/update-icons.cjs`.
 */
object IconPackApplier {

    private data class PacksData(
        val packs: Map<String, List<String>>,
        val defaults: Map<String, Boolean>,
    )

    private data class Assoc(val ext: List<String>, val names: List<String>)

    private val packsData: PacksData? by lazy { loadPacks() }
    private val iconAssoc: Map<String, Assoc> by lazy { loadIcons() }

    fun apply(
        activeIconPackJson: String,
        ext: MutableMap<String, String>,
        names: MutableMap<String, String>,
    ) {
        val data = packsData ?: return
        val user = parseObject(activeIconPackJson)
        for ((pack, ids) in data.packs) {
            val defaultOn = data.defaults[pack] ?: true
            val userVal = user?.get(pack)
            val on = if (userVal != null && userVal.isJsonPrimitive) userVal.asBoolean else defaultOn
            if (on == defaultOn) continue

            if (!on) {
                // Turn off: drop associations that resolve to this pack's icons.
                val idSet = ids.toHashSet()
                ext.entries.removeIf { it.value in idSet }
                names.entries.removeIf { it.value in idSet }
            } else {
                // Turn on a default-off pack: add its associations from icons.json.
                for (id in ids) {
                    val assoc = iconAssoc[id] ?: continue
                    for (e in assoc.ext) if (e.isNotBlank()) ext[e.lowercase()] = id
                    for (n in assoc.names) if (n.isNotBlank()) names[n.lowercase()] = id
                }
            }
        }
    }

    private fun parseObject(json: String): JsonObject? {
        if (json.isBlank()) return null
        return try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            null
        }
    }

    private fun loadPacks(): PacksData? {
        val text = resource("/flow/packs.json") ?: return null
        return try {
            val obj = JsonParser.parseString(text).asJsonObject
            val packsObj = obj.getAsJsonObject("packs") ?: return null
            val packs = packsObj.entrySet().associate { (k, v) ->
                k to v.asJsonArray.map { it.asString }
            }
            val defObj = obj.getAsJsonObject("defaults")?.getAsJsonObject("packs")
            val defaults = defObj?.entrySet()?.associate { (k, v) -> k to v.asBoolean } ?: emptyMap()
            PacksData(packs, defaults)
        } catch (e: Exception) {
            thisLogger().warn("Failed to parse packs.json", e)
            null
        }
    }

    private fun loadIcons(): Map<String, Assoc> {
        val text = resource("/flow/icons.json") ?: return emptyMap()
        return try {
            val files = JsonParser.parseString(text).asJsonObject.getAsJsonObject("files")
                ?: return emptyMap()
            files.entrySet().associate { (id, v) ->
                val o = v.asJsonObject
                id to Assoc(
                    ext = o.getAsJsonArray("e")?.map { it.asString } ?: emptyList(),
                    names = o.getAsJsonArray("n")?.map { it.asString } ?: emptyList(),
                )
            }
        } catch (e: Exception) {
            thisLogger().warn("Failed to parse icons.json", e)
            emptyMap()
        }
    }

    private fun resource(path: String): String? =
        javaClass.getResourceAsStream(path)?.readBytes()?.toString(StandardCharsets.UTF_8)
}
