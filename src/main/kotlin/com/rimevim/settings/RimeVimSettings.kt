package com.rimevim.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.Configurable
import com.intellij.ui.ColorPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.xmlb.XmlSerializerUtil
import com.rimevim.ime.WeaselPathDetector
import org.jetbrains.annotations.Nls
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator

@State(
    name = "RimeVimSettings",
    storages = [Storage("rimevim.xml")]
)
class RimeVimSettings : PersistentStateComponent<RimeVimSettings> {

    var enabled: Boolean = true
    var weaselServerPath: String = ""
    var englishColor: String = "#00CC66"
    var chineseColor: String = "#FF6666"
    var capsLockColor: String = "#FFCC00"

    // Insert 模式自动切换规则
    var insertModeAsciiRegex: String = ""
    var insertModeCapsRegex: String = ""
    var insertModeLowerRegex: String = ""

    override fun getState(): RimeVimSettings = this

    override fun loadState(state: RimeVimSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: RimeVimSettings
            get() = ApplicationManager.getApplication().getService(RimeVimSettings::class.java)
    }
}

class RimeVimSettingsConfigurable : Configurable {

    private var settingsPanel: JPanel? = null
    private var enabledCheckBox: JCheckBox? = null
    private var pathField: JBTextField? = null
    private var englishColorPanel: ColorPanel? = null
    private var chineseColorPanel: ColorPanel? = null
    private var capsLockColorPanel: ColorPanel? = null
    private var insertModeAsciiRegexField: JBTextField? = null
    private var insertModeCapsRegexField: JBTextField? = null
    private var insertModeLowerRegexField: JBTextField? = null

    @Nls(capitalization = Nls.Capitalization.Title)
    override fun getDisplayName(): String = "RimeVim IME"

    override fun createComponent(): JComponent {
        settingsPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.anchor = GridBagConstraints.WEST

        // 启用开关
        enabledCheckBox = JCheckBox("启用 RimeVim IME")
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2
        settingsPanel!!.add(enabledCheckBox, gbc)

        // WeaselServer 路径
        gbc.gridy = 1; gbc.gridwidth = 1
        settingsPanel!!.add(JBLabel("WeaselServer.exe 路径:"), gbc)
        pathField = JBTextField()
        gbc.gridx = 1; gbc.weightx = 1.0
        settingsPanel!!.add(pathField, gbc)

        // 颜色选择
        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 1; gbc.weightx = 0.0
        settingsPanel!!.add(JBLabel("英文模式颜色:"), gbc)
        englishColorPanel = ColorPanel()
        gbc.gridx = 1
        settingsPanel!!.add(englishColorPanel, gbc)

        gbc.gridy = 3; gbc.gridx = 0
        settingsPanel!!.add(JBLabel("中文模式颜色:"), gbc)
        chineseColorPanel = ColorPanel()
        gbc.gridx = 1
        settingsPanel!!.add(chineseColorPanel, gbc)

        gbc.gridy = 4; gbc.gridx = 0
        settingsPanel!!.add(JBLabel("CapsLock 颜色:"), gbc)
        capsLockColorPanel = ColorPanel()
        gbc.gridx = 1
        settingsPanel!!.add(capsLockColorPanel, gbc)

        // 分隔线
        gbc.gridy = 5; gbc.gridx = 0; gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        settingsPanel!!.add(JSeparator(), gbc)

        // Insert 模式自动切换规则标题
        gbc.gridy = 6; gbc.gridx = 0; gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.NONE
        settingsPanel!!.add(JBLabel("Insert 模式自动切换规则（正则表达式）:"), gbc)

        // 自动切换中英文规则
        gbc.gridy = 7; gbc.gridx = 0; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE
        settingsPanel!!.add(JBLabel("中英文切换规则:"), gbc)
        insertModeAsciiRegexField = JBTextField()
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        settingsPanel!!.add(insertModeAsciiRegexField, gbc)

        // 自动切换大写规则
        gbc.gridy = 8; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE
        settingsPanel!!.add(JBLabel("大写切换规则:"), gbc)
        insertModeCapsRegexField = JBTextField()
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL
        settingsPanel!!.add(insertModeCapsRegexField, gbc)

        // 自动切换小写规则
        gbc.gridy = 9; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE
        settingsPanel!!.add(JBLabel("小写切换规则:"), gbc)
        insertModeLowerRegexField = JBTextField()
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL
        settingsPanel!!.add(insertModeLowerRegexField, gbc)

        return settingsPanel!!
    }

    override fun isModified(): Boolean {
        val settings = RimeVimSettings.instance
        return enabledCheckBox?.isSelected != settings.enabled ||
                pathField?.text != settings.weaselServerPath ||
                englishColorPanel?.selectedColor?.let { toHex(it) } != settings.englishColor ||
                chineseColorPanel?.selectedColor?.let { toHex(it) } != settings.chineseColor ||
                capsLockColorPanel?.selectedColor?.let { toHex(it) } != settings.capsLockColor ||
                insertModeAsciiRegexField?.text != settings.insertModeAsciiRegex ||
                insertModeCapsRegexField?.text != settings.insertModeCapsRegex ||
                insertModeLowerRegexField?.text != settings.insertModeLowerRegex
    }

    override fun apply() {
        val settings = RimeVimSettings.instance
        settings.enabled = enabledCheckBox?.isSelected ?: true
        settings.weaselServerPath = pathField?.text ?: ""
        englishColorPanel?.selectedColor?.let { settings.englishColor = toHex(it) }
        chineseColorPanel?.selectedColor?.let { settings.chineseColor = toHex(it) }
        capsLockColorPanel?.selectedColor?.let { settings.capsLockColor = toHex(it) }
        settings.insertModeAsciiRegex = insertModeAsciiRegexField?.text ?: ""
        settings.insertModeCapsRegex = insertModeCapsRegexField?.text ?: ""
        settings.insertModeLowerRegex = insertModeLowerRegexField?.text ?: ""
    }

    override fun reset() {
        val settings = RimeVimSettings.instance
        enabledCheckBox?.isSelected = settings.enabled
        pathField?.text = settings.weaselServerPath.ifBlank { WeaselPathDetector.detect() ?: "" }
        englishColorPanel?.selectedColor = decodeColor(settings.englishColor)
        chineseColorPanel?.selectedColor = decodeColor(settings.chineseColor)
        capsLockColorPanel?.selectedColor = decodeColor(settings.capsLockColor)
        insertModeAsciiRegexField?.text = settings.insertModeAsciiRegex
        insertModeCapsRegexField?.text = settings.insertModeCapsRegex
        insertModeLowerRegexField?.text = settings.insertModeLowerRegex
    }

    private fun toHex(color: java.awt.Color): String {
        return String.format("#%02X%02X%02X", color.red, color.green, color.blue)
    }

    private fun decodeColor(hex: String): java.awt.Color {
        return try {
            java.awt.Color.decode(hex)
        } catch (e: Exception) {
            java.awt.Color(0x00CC66)
        }
    }
}
