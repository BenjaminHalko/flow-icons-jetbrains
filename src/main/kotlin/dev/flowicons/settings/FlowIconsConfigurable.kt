package dev.flowicons.settings

import com.google.gson.JsonSyntaxException
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import dev.flowicons.FlowIconsBundle
import dev.flowicons.flowyou.FlowYouBuilder
import dev.flowicons.icons.FlowArrowHider
import dev.flowicons.icons.FlowIconRefresher
import dev.flowicons.premium.PremiumIconManager
import javax.swing.JComponent

/**
 * Settings → Appearance & Behavior → Flow Icons.
 *
 * The control list mirrors the VSCode extension's settings one-to-one (same
 * labels, descriptions and order), with the "Icon theme" picker added on top
 * because JetBrains — unlike VSCode — has no built-in file-icon-theme selector.
 *
 * Like VSCode, there are no buttons here: entering a license key auto-downloads
 * the premium set on apply, and editing the Flow You palette rebuilds it on
 * apply. The equivalent of VSCode's two commands is exposed as IDE actions
 * ("Flow Icons: Download Icons" / "Flow Icons: Rebuild Icons").
 */
class FlowIconsConfigurable : Configurable {

    private val settings = FlowIconsSettings.getInstance()

    private lateinit var variantCombo: ComboBox<ThemeVariant>
    private lateinit var licenseField: JBPasswordField
    private lateinit var hidesArrowsCb: JBCheckBox
    private lateinit var hidesFoldersCb: JBCheckBox
    private lateinit var specificFoldersCb: JBCheckBox
    private lateinit var activeIconPackArea: JBTextArea
    private lateinit var filesAssocArea: JBTextArea
    private lateinit var foldersAssocArea: JBTextArea
    private lateinit var filesReplArea: JBTextArea
    private lateinit var foldersReplArea: JBTextArea
    private lateinit var youColorsArea: JBTextArea
    private lateinit var folderColorCombo: ComboBox<String>

    override fun getDisplayName(): String = msg("settings.displayName")

    override fun createComponent(): JComponent {
        variantCombo = ComboBox(ThemeVariant.entries.toTypedArray())
        licenseField = JBPasswordField().apply { columns = 30 }
        hidesArrowsCb = JBCheckBox(msg("settings.label.hidesArrows"))
        hidesFoldersCb = JBCheckBox(msg("settings.label.hidesFolders"))
        specificFoldersCb = JBCheckBox(msg("settings.label.specificFolders"))
        activeIconPackArea = jsonArea()
        filesAssocArea = jsonArea()
        foldersAssocArea = jsonArea()
        filesReplArea = jsonArea()
        foldersReplArea = jsonArea()
        youColorsArea = jsonArea(rows = 8)
        folderColorCombo = ComboBox(FlowIconsSettings.FOLDER_COLORS.toTypedArray())

        val component = panel {
            // JetBrains-only theme picker (VSCode uses its native icon-theme selector).
            row(msg("settings.label.theme")) { cell(variantCombo) }

            row(msg("settings.label.folderColor")) { cell(folderColorCombo) }
                .rowComment(msg("settings.desc.folderColor"))

            row(msg("settings.label.license")) { cell(licenseField) }
                .rowComment(msg("settings.desc.license"))

            row { cell(hidesArrowsCb) }.rowComment(msg("settings.desc.hidesArrows"))
            row { cell(hidesFoldersCb) }.rowComment(msg("settings.desc.hidesFolders"))
            row { cell(specificFoldersCb) }.rowComment(msg("settings.desc.specificFolders"))

            jsonRow(filesAssocArea, "settings.label.filesAssociations", "settings.desc.filesAssociations")
            jsonRow(foldersAssocArea, "settings.label.foldersAssociations", "settings.desc.foldersAssociations")
            jsonRow(filesReplArea, "settings.label.filesReplacements", "settings.desc.filesReplacements")
            jsonRow(foldersReplArea, "settings.label.foldersReplacements", "settings.desc.foldersReplacements")
            jsonRow(activeIconPackArea, "settings.label.activeIconPack", "settings.desc.activeIconPack")
            jsonRow(youColorsArea, "settings.label.youColors", "settings.desc.youColors")
        }
        reset()
        return component
    }

    override fun isModified(): Boolean =
        variantCombo.selectedItem != settings.themeVariant ||
            String(licenseField.password) != LicenseStore.get() ||
            hidesArrowsCb.isSelected != settings.hidesExplorerArrows ||
            hidesFoldersCb.isSelected != settings.hidesExplorerFolders ||
            specificFoldersCb.isSelected != settings.specificFolders ||
            activeIconPackArea.text != settings.activeIconPackJson ||
            filesAssocArea.text != settings.filesAssociationsJson ||
            foldersAssocArea.text != settings.foldersAssociationsJson ||
            filesReplArea.text != settings.filesReplacementsJson ||
            foldersReplArea.text != settings.foldersReplacementsJson ||
            youColorsArea.text != settings.youColorsJson ||
            (folderColorCombo.selectedItem as? String) != settings.folderColor

    override fun apply() {
        val newLicense = String(licenseField.password)
        val licenseChanged = newLicense != LicenseStore.get()
        val youChanged = youColorsArea.text != settings.youColorsJson
        val newArrows = hidesArrowsCb.isSelected
        val arrowsChanged = newArrows != settings.hidesExplorerArrows

        settings.themeVariant = variantCombo.selectedItem as ThemeVariant
        settings.hidesExplorerArrows = hidesArrowsCb.isSelected
        settings.hidesExplorerFolders = hidesFoldersCb.isSelected
        settings.specificFolders = specificFoldersCb.isSelected
        settings.activeIconPackJson = activeIconPackArea.text
        settings.filesAssociationsJson = filesAssocArea.text
        settings.foldersAssociationsJson = foldersAssocArea.text
        settings.filesReplacementsJson = filesReplArea.text
        settings.foldersReplacementsJson = foldersReplArea.text
        settings.youColorsJson = youColorsArea.text
        settings.folderColor = (folderColorCombo.selectedItem as? String) ?: "gray"
        if (licenseChanged) LicenseStore.set(newLicense)

        if (youChanged) rebuildYou()
        // refreshAll() -> invalidate() reloads the mapping, picking up every change above.
        FlowIconRefresher.refreshAll()

        if (arrowsChanged) FlowArrowHider.update(newArrows)

        // Auto-download the premium set as soon as a key is entered (like VSCode).
        if (licenseChanged && newLicense.isNotBlank()) {
            PremiumIconManager.startDownload(null)
        }
    }

    override fun reset() {
        variantCombo.selectedItem = settings.themeVariant
        licenseField.text = LicenseStore.get()
        hidesArrowsCb.isSelected = settings.hidesExplorerArrows
        hidesFoldersCb.isSelected = settings.hidesExplorerFolders
        specificFoldersCb.isSelected = settings.specificFolders
        activeIconPackArea.text = settings.activeIconPackJson
        filesAssocArea.text = settings.filesAssociationsJson
        foldersAssocArea.text = settings.foldersAssociationsJson
        filesReplArea.text = settings.filesReplacementsJson
        foldersReplArea.text = settings.foldersReplacementsJson
        youColorsArea.text = settings.youColorsJson
        folderColorCombo.selectedItem = settings.folderColor
    }

    private fun rebuildYou() {
        try {
            FlowYouBuilder.build(settings.youColorsJson)
            notify(NotificationType.INFORMATION, msg("notify.you.rebuilt"))
        } catch (e: JsonSyntaxException) {
            notify(NotificationType.WARNING, msg("notify.you.invalidJson"))
        }
    }

    private fun notify(type: NotificationType, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Flow Icons")
            .createNotification(content, type)
            .notify(null)
    }

    private fun jsonArea(rows: Int = 4): JBTextArea =
        JBTextArea(rows, 50).apply { lineWrap = false }

    private fun com.intellij.ui.dsl.builder.Panel.jsonRow(
        area: JBTextArea,
        labelKey: String,
        descKey: String,
    ) {
        row {
            cell(JBScrollPane(area)).align(Align.FILL).label(msg(labelKey), LabelPosition.TOP)
        }.rowComment(msg(descKey))
    }

    private fun msg(key: String): String = FlowIconsBundle.message(key)
}
