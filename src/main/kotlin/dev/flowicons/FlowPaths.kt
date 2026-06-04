package dev.flowicons

import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writable locations for runtime-generated / downloaded assets. The plugin jar
 * is read-only, so premium icons and custom Flow You icons live under the
 * per-user IDE config directory instead, all rooted at `<config>/flow-icons`:
 * downloaded premium icons in `premium/<version>/<variant>`, the premium lookup
 * table in `premium/mapping.json`, and generated Flow You SVGs in the `you` and
 * `you-light` folders.
 */
object FlowPaths {
    val root: Path
        get() = PathManager.getConfigDir().resolve("flow-icons")

    val premiumDir: Path get() = root.resolve("premium")
    val premiumMapping: Path get() = premiumDir.resolve("mapping.json")

    /** Directory holding generated SVGs for a Flow You variant folder name. */
    fun youDir(folder: String): Path = root.resolve(folder)

    fun ensureDir(path: Path): Path {
        Files.createDirectories(path)
        return path
    }
}
