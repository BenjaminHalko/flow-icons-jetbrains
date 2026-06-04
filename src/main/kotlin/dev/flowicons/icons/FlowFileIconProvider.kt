package dev.flowicons.icons

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * Supplies Flow Icons for editor tabs and other file-icon call sites outside the
 * Project View tree. Folders are intentionally left to
 * [FlowProjectViewNodeDecorator] so the two mechanisms don't fight over
 * directory icons. Queried even in dumb mode, so it stays index-free.
 */
class FlowFileIconProvider : FileIconProvider {

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (file.isDirectory) return null
        return FlowIconService.getInstance().resolveFileOnly(file)
    }
}
