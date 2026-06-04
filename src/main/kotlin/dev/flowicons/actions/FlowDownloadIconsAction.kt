package dev.flowicons.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import dev.flowicons.premium.PremiumIconManager

/** Mirrors the VSCode `flow-icons.downloadIcons` command. */
class FlowDownloadIconsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        PremiumIconManager.startDownload(e.project, interactive = true)
    }
}
