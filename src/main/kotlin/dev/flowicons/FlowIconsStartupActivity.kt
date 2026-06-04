package dev.flowicons

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.flowicons.icons.FlowArrowHider
import dev.flowicons.premium.PremiumIconManager
import dev.flowicons.settings.FlowIconsSettings
import dev.flowicons.settings.LicenseStore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On IDE startup, runs once per session: re-applies the "Hides explorer arrows"
 * override (the theme repopulates the arrow icons at launch) and, if a license
 * key is present, silently checks the server for a newer premium icon set and
 * installs it in the background.
 *
 * The premium check is silent unless a new version is actually installed, in
 * which case a notification is shown (matching the VSCode extension).
 */
class FlowIconsStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (!CHECKED.compareAndSet(false, true)) return

        if (FlowIconsSettings.getInstance().hidesExplorerArrows) {
            FlowArrowHider.update(true)
        }
        if (LicenseStore.hasLicense) {
            PremiumIconManager.startDownload(project, interactive = false)
        }
    }

    private companion object {
        /** Ensures the premium check runs only once per IDE session. */
        val CHECKED = AtomicBoolean(false)
    }
}
