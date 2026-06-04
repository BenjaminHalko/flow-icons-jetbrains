package dev.flowicons.premium

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import dev.flowicons.FlowIconsBundle
import dev.flowicons.FlowPaths
import dev.flowicons.icons.FlowIconRefresher
import dev.flowicons.settings.FlowIconsSettings
import dev.flowicons.settings.LicenseStore
import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.math.ceil

/**
 * Downloads and installs the premium Flow Icons set at runtime, mirroring the
 * original VSCode extension: the license key authorizes a download from the Flow
 * Icons API; the brotli-compressed tar is unpacked into a versioned directory
 * under the IDE config dir, then the icon caches are refreshed.
 */
object PremiumIconManager {

    private const val OPENVSX_API = "https://open-vsx.org/api/thang-nm/flow-icons"
    private const val API_BASE = "https://legit-i9lq.onrender.com/flow-icons"
    private const val USER_AGENT = "Flow Icons"

    private val VARIANT_FOLDERS = setOf(
        "deep", "deep-light", "dim", "dim-light",
        "dawn", "dawn-light", "you", "you-light", "icons",
    )

    /**
     * @param interactive true for a user-triggered download (notify on every
     *   outcome, including "up to date" and errors); false for the silent
     *   startup check (notify only when a new premium set is actually installed).
     */
    fun startDownload(project: Project?, interactive: Boolean = true) {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, FlowIconsBundle.message("notify.premium.downloading"), true) {
                override fun run(indicator: ProgressIndicator) = doDownload(indicator, interactive)
                override fun onThrowable(error: Throwable) {
                    thisLogger().warn("Premium icon download failed", error)
                    if (interactive) {
                        notify(NotificationType.ERROR, FlowIconsBundle.message("notify.premium.failed", error.message ?: "error"))
                    }
                }
            },
        )
    }

    private fun doDownload(indicator: ProgressIndicator, interactive: Boolean) {
        val license = LicenseStore.get()
        if (license.isBlank()) {
            if (interactive) notify(NotificationType.WARNING, FlowIconsBundle.message("notify.license.invalid"))
            return
        }
        val settings = FlowIconsSettings.getInstance()

        indicator.text = "Resolving Flow Icons version..."
        val extVersion = fetchExtensionVersion()
        val machineId = machineId()

        indicator.text = "Checking license..."
        val (iconsVersion, downloadUrl) = fetchPremiumVersion(license, machineId, extVersion)
        val remoteVersion = "$extVersion-$iconsVersion"

        val versionedDir = FlowPaths.premiumDir.resolve(remoteVersion)
        if (settings.premiumIconsVersion == remoteVersion && Files.isDirectory(versionedDir)) {
            // Nothing new. Only surface this for an explicit, user-triggered check.
            if (interactive) {
                notify(NotificationType.INFORMATION, FlowIconsBundle.message("notify.premium.upToDate"))
            }
            return
        }

        indicator.text = "Downloading premium icons..."
        val compressed = HttpRequests.request(downloadUrl)
            .userAgent(USER_AGENT)
            .readBytes(indicator)

        indicator.text = "Extracting icons..."
        val tarBytes = BrotliInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }

        // Extract into a temp dir, then atomically move into place.
        val tmpDir = Files.createTempDirectory(FlowPaths.ensureDir(FlowPaths.premiumDir), "tmp-")
        val extracted = try {
            val n = extractTar(tarBytes, tmpDir)
            if (n == 0) error("Archive contained no icons.")
            installAtomically(tmpDir, versionedDir)
            n
        } finally {
            runCatching { if (Files.exists(tmpDir)) tmpDir.toFile().deleteRecursively() }
        }

        // Build the premium name->icon-id mapping from the bundled theme JSON.
        writePremiumMapping(versionedDir)

        settings.premiumIconsVersion = remoteVersion
        settings.premiumIconsPath = versionedDir.toString()

        cleanupOldVersions(versionedDir)
        FlowIconRefresher.refreshAll()
        notify(NotificationType.INFORMATION, FlowIconsBundle.message("notify.premium.success", extracted))
    }

    // -- API ------------------------------------------------------------------

    private fun fetchExtensionVersion(): String {
        val json = HttpRequests.request(OPENVSX_API).userAgent(USER_AGENT).readString()
        return Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
            ?: error("Could not read extension version from Open VSX.")
    }

    /** @return Pair(iconsVersion, downloadUrl) */
    private fun fetchPremiumVersion(license: String, machineId: String, extVersion: String): Pair<String, String> {
        val url = "$API_BASE/version-3?v=$extVersion"
        val json = HttpRequests.request(url)
            .tuner { conn ->
                conn.setRequestProperty("authorization", license)
                conn.setRequestProperty("machine-id", machineId)
            }
            .userAgent("$USER_AGENT/$extVersion")
            .readString()
        val obj = JsonParser.parseString(json).asJsonObject
        val version = obj.get("version")?.asString ?: error("License server did not return a version.")
        val downloadUrl = obj.get("url")?.asString ?: error("License server did not return a download URL.")
        return version to downloadUrl
    }

    private fun machineId(): String {
        val host = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()
            ?: System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: "unknown"
        val digest = MessageDigest.getInstance("MD5").digest(host.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // -- tar extraction (path-traversal guarded, .svg + theme json only) ------

    private fun extractTar(buffer: ByteArray, outDir: Path): Int {
        var offset = 0
        var count = 0
        val outRoot = outDir.toAbsolutePath().normalize()
        while (offset + 512 <= buffer.size) {
            val name = String(buffer, offset, 100, StandardCharsets.UTF_8).substringBefore('\u0000').trim()
            if (name.isEmpty()) break
            val sizeOctal = String(buffer, offset + 124, 12, StandardCharsets.US_ASCII)
                .substringBefore('\u0000').trim()
            val size = sizeOctal.ifBlank { "0" }.toLongOrNull(8) ?: 0L
            offset += 512

            if (size > 0) {
                if (isWanted(name)) {
                    val target = outRoot.resolve(name).normalize()
                    if (target.startsWith(outRoot)) {
                        Files.createDirectories(target.parent)
                        Files.write(target, buffer.copyOfRange(offset, (offset + size).toInt()))
                        count++
                    } else {
                        thisLogger().warn("Skipping tar entry outside output dir: $name")
                    }
                }
                offset += ceil(size / 512.0).toInt() * 512
            }
        }
        return count
    }

    private fun isWanted(name: String): Boolean {
        if (name.contains("/._") || name.startsWith("._") || name.contains("PaxHeader")) return false
        val first = name.substringBefore('/')
        if (name.endsWith(".svg")) return first in VARIANT_FOLDERS
        // Keep the dark theme JSON so we can derive the premium mapping table.
        return name == "deep.json"
    }

    private fun installAtomically(tmpDir: Path, versionedDir: Path) {
        if (Files.exists(versionedDir)) versionedDir.toFile().deleteRecursively()
        Files.createDirectories(versionedDir.parent)
        try {
            Files.move(tmpDir, versionedDir, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            // ATOMIC_MOVE can fail across stores; fall back to a plain move/copy.
            Files.move(tmpDir, versionedDir)
        }
    }

    // -- premium mapping ------------------------------------------------------

    private fun writePremiumMapping(versionedDir: Path) {
        val themeJson = versionedDir.resolve("deep.json")
        if (!Files.exists(themeJson)) return
        val theme = JsonParser.parseString(Files.readString(themeJson, StandardCharsets.UTF_8)).asJsonObject
        val mapping = JsonObject().apply {
            add("defaults", JsonObject().apply {
                addProperty("file", theme.get("file")?.asString ?: "file")
                addProperty("folder", theme.get("folder")?.asString ?: "folder_gray")
            })
            add("fileExtensions", theme.getAsJsonObject("fileExtensions") ?: JsonObject())
            add("fileNames", theme.getAsJsonObject("fileNames") ?: JsonObject())
            add("folderNames", theme.getAsJsonObject("folderNames") ?: JsonObject())
        }
        Files.writeString(FlowPaths.premiumMapping, mapping.toString(), StandardCharsets.UTF_8)
    }

    private fun cleanupOldVersions(keep: Path) {
        runCatching {
            Files.list(FlowPaths.premiumDir).use { stream ->
                stream.filter { Files.isDirectory(it) && it != keep }
                    .forEach { runCatching { it.toFile().deleteRecursively() } }
            }
        }
    }

    private fun countSvgs(dir: Path): Int = runCatching {
        Files.walk(dir).use { s -> s.filter { it.toString().endsWith(".svg") }.count().toInt() }
    }.getOrDefault(0)

    private fun notify(type: NotificationType, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Flow Icons")
            .createNotification(content, type)
            .notify(null)
    }
}
