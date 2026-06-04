package dev.flowicons.actions

import com.google.gson.JsonSyntaxException
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import dev.flowicons.FlowIconsBundle
import dev.flowicons.flowyou.FlowYouBuilder
import dev.flowicons.icons.FlowIconRefresher
import dev.flowicons.settings.FlowIconsSettings

/** Mirrors the VSCode `flow-icons.rebuildIcons` command. */
class FlowRebuildIconsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val (type, content) = try {
            FlowYouBuilder.build(FlowIconsSettings.getInstance().youColorsJson)
            NotificationType.INFORMATION to FlowIconsBundle.message("notify.you.rebuilt")
        } catch (ex: JsonSyntaxException) {
            NotificationType.WARNING to FlowIconsBundle.message("notify.you.invalidJson")
        }
        FlowIconRefresher.refreshAll()
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Flow Icons")
            .createNotification(content, type)
            .notify(e.project)
    }
}
