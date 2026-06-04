package dev.flowicons.icons

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.util.ui.EmptyIcon

/**
 * Replaces Project View tree icons (files AND folders) with Flow Icons.
 *
 * Per the current platform API, [PresentationData] has no distinct open/closed
 * folder icon, so directories use the single collapsed `folder_<id>.svg`.
 *
 * Kept index-/IO-/network-free: it only reads the node's [com.intellij.openapi.vfs.VirtualFile]
 * name and looks up a cached icon.
 */
class FlowProjectViewNodeDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val file = node.virtualFile ?: return
        if (!file.isValid) return
        val service = FlowIconService.getInstance()
        if (file.isDirectory && service.hidesFolderIcons) {
            data.setIcon(EmptyIcon.ICON_16)
            return
        }
        val icon = service.resolve(file) ?: return
        data.setIcon(icon)
    }
}
