package dev.flowicons.icons

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.IconLoader

/**
 * Forces the Project View and editor tabs to re-query icons after the variant,
 * license, premium set, or Flow You palette changes.
 */
object FlowIconRefresher {

    fun refreshAll() {
        FlowIconService.getInstance().invalidate()

        ApplicationManager.getApplication().invokeLater {
            // Flow You regeneration overwrites the same file URLs, so drop the
            // platform icon cache as well. Premium uses versioned dirs and would
            // not strictly need this, but a single explicit clear is cheap on a
            // user-triggered refresh.
            runCatching { IconLoader.clearCache() }

            for (project in ProjectManager.getInstance().openProjects) {
                if (project.isDisposed) continue
                runCatching { ProjectView.getInstance(project).refresh() }
                runCatching { FileEditorManagerEx.getInstanceEx(project).refreshIcons() }
            }
        }
    }
}
