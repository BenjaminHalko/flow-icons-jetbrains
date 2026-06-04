package dev.flowicons.icons

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener

/**
 * A Look-and-Feel change repopulates the tree control icons from the new theme,
 * which would bring the expand arrows back. This re-applies the "Hides explorer
 * arrows" override afterward when the setting is on.
 */
class FlowLafListener : LafManagerListener {
    override fun lookAndFeelChanged(source: LafManager) {
        FlowArrowHider.reapplyIfHidden()
    }
}
