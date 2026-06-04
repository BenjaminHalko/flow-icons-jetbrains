package dev.flowicons.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level persisted settings for Flow Icons.
 *
 * Field set mirrors the VSCode extension's `flow-icons.*` configuration keys
 * one-to-one (plus [themeVariant], which JetBrains needs because — unlike VSCode
 * — the platform has no built-in file-icon-theme picker).
 *
 * The license key is a secret and is NOT stored here; see [LicenseStore]
 * (backed by [com.intellij.ide.passwordSafe.PasswordSafe]).
 */
@State(
    name = "FlowIconsSettings",
    storages = [Storage("flow-icons.xml")],
)
class FlowIconsSettings :
    SimplePersistentStateComponent<FlowIconsSettings.FlowState>(FlowState()) {

    class FlowState : BaseState() {
        /** Active palette (JetBrains-only; VSCode uses its native icon-theme picker). */
        var themeVariant by string(ThemeVariant.DEFAULT.name)

        // --- mirrors of the VSCode flow-icons.* settings ---
        var hidesExplorerArrows by property(false)
        var hidesExplorerFolders by property(false)
        var specificFolders by property(true)
        var activeIconPackJson by string("")
        var filesAssociationsJson by string("")
        var foldersAssociationsJson by string("")
        var filesReplacementsJson by string("")
        var foldersReplacementsJson by string("")
        var youColorsJson by string("")
        var folderColor by string("gray")

        // --- premium install bookkeeping ---
        var premiumIconsVersion by string("")
        var premiumIconsPath by string("")
    }

    var themeVariant: ThemeVariant
        get() = ThemeVariant.fromNameOrDefault(state.themeVariant)
        set(value) { state.themeVariant = value.name }

    var hidesExplorerArrows: Boolean
        get() = state.hidesExplorerArrows
        set(value) { state.hidesExplorerArrows = value }

    var hidesExplorerFolders: Boolean
        get() = state.hidesExplorerFolders
        set(value) { state.hidesExplorerFolders = value }

    var specificFolders: Boolean
        get() = state.specificFolders
        set(value) { state.specificFolders = value }

    var activeIconPackJson: String
        get() = state.activeIconPackJson.orEmpty()
        set(value) { state.activeIconPackJson = value }

    var filesAssociationsJson: String
        get() = state.filesAssociationsJson.orEmpty()
        set(value) { state.filesAssociationsJson = value }

    var foldersAssociationsJson: String
        get() = state.foldersAssociationsJson.orEmpty()
        set(value) { state.foldersAssociationsJson = value }

    var filesReplacementsJson: String
        get() = state.filesReplacementsJson.orEmpty()
        set(value) { state.filesReplacementsJson = value }

    var foldersReplacementsJson: String
        get() = state.foldersReplacementsJson.orEmpty()
        set(value) { state.foldersReplacementsJson = value }

    var youColorsJson: String
        get() = state.youColorsJson.orEmpty()
        set(value) { state.youColorsJson = value }

    var folderColor: String
        get() = state.folderColor.orEmpty().ifBlank { "gray" }
        set(value) { state.folderColor = value }

    var premiumIconsVersion: String
        get() = state.premiumIconsVersion.orEmpty()
        set(value) { state.premiumIconsVersion = value }

    var premiumIconsPath: String
        get() = state.premiumIconsPath.orEmpty()
        set(value) { state.premiumIconsPath = value }

    val hasPremium: Boolean get() = premiumIconsVersion.isNotBlank() && premiumIconsPath.isNotBlank()

    companion object {
        /** Folder colors offered by the VSCode `flow-icons.folderColor` enum. */
        val FOLDER_COLORS = listOf(
            "gray", "blue", "brown", "green", "lime", "orange",
            "pink", "purple", "red", "sky", "teal", "yellow",
        )

        @JvmStatic
        fun getInstance(): FlowIconsSettings =
            ApplicationManager.getApplication().getService(FlowIconsSettings::class.java)
    }
}
