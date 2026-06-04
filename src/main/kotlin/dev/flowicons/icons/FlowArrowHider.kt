package dev.flowicons.icons

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.ui.EmptyIcon
import dev.flowicons.settings.FlowIconsSettings
import java.awt.Window
import javax.swing.Icon
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * Implements the "Hides explorer arrows" setting by replacing the tree
 * expand/collapse control icons with transparent, same-size icons.
 *
 * The platform paints tree twisties from overridable `UIManager` icons
 * (`DefaultControl` -> `UIUtil.getTree(Collapsed|Expanded)Icon` -> `UIManager`),
 * including under the new UI. Since `DefaultControl` caches its icons at
 * construction, tree UIs must be recreated after a change.
 *
 * Hiding **captures the original icons** first; un-hiding **restores those exact
 * icons** (rather than relying on a LaF reload, which does not clear
 * `UIManager.put` overrides — that was why un-hiding previously needed a
 * restart). A Look-and-Feel change discards our overrides and repopulates the
 * theme icons, so [FlowLafListener] re-captures and re-applies afterward.
 *
 * A same-size transparent icon is used (not a 0x0 empty) so the control still
 * occupies its slot and indentation is preserved.
 *
 * Note: like VSCode's explorer-wide `hidesExplorerArrows`, this affects every
 * tree in the IDE, not only the Project view.
 */
object FlowArrowHider {

    private val KEYS = listOf(
        "Tree.collapsedIcon",
        "Tree.expandedIcon",
        "Tree.collapsedSelectedIcon",
        "Tree.expandedSelectedIcon",
    )

    /** Original icons captured at hide time, restored on un-hide. */
    private val originals = HashMap<String, Icon?>()

    @Volatile
    private var applied = false

    /** Apply or clear the override based on [hide] (on the EDT). */
    fun update(hide: Boolean) {
        ApplicationManager.getApplication().invokeLater {
            if (hide) applyHide() else restore()
        }
    }

    /**
     * After a Look-and-Feel change the theme repopulates the arrow icons and our
     * override is gone, so reset the applied flag and re-apply if still enabled.
     */
    fun reapplyIfHidden() {
        applied = false
        if (FlowIconsSettings.getInstance().hidesExplorerArrows) {
            ApplicationManager.getApplication().invokeLater { applyHide() }
        }
    }

    private fun applyHide() {
        if (applied) return
        // Capture the current (real) icons before overriding them.
        for (key in KEYS) originals[key] = UIManager.getIcon(key)
        for (key in KEYS) {
            val current = originals[key]
            val w = current?.iconWidth?.takeIf { it > 0 } ?: 16
            val h = current?.iconHeight?.takeIf { it > 0 } ?: 16
            UIManager.put(key, EmptyIcon.create(w, h))
        }
        applied = true
        recreateTreeUIs()
    }

    private fun restore() {
        // Put the captured originals back (a null value clears the key, which is
        // the correct original state for undefined selected-variant icons).
        for (key in KEYS) {
            if (originals.containsKey(key)) UIManager.put(key, originals[key])
        }
        applied = false
        recreateTreeUIs()
    }

    private fun recreateTreeUIs() {
        for (window in Window.getWindows()) {
            if (window.isDisplayable) {
                runCatching { SwingUtilities.updateComponentTreeUI(window) }
            }
        }
    }
}
