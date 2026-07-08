package com.auto_switch_ime.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.ColorPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.xmlb.XmlSerializerUtil
import com.auto_switch_ime.core.ime.ImeStateDetector
import com.auto_switch_ime.core.ime.WeaselPathDetector
import com.auto_switch_ime.util.AutoSwitchIMELogger
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import java.util.regex.Pattern
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.JTextArea

@State(
    name = "AutoSwitchIMESettings",
    storages = [Storage("auto_switch_ime.xml")]
)
class AutoSwitchIMESettings : PersistentStateComponent<AutoSwitchIMESettings> {

    var enabled: Boolean = true
    var weaselServerPath: String = ""
    var englishColor: String = "#FFFFFF"
    var chineseColor: String = "#00CC66"
    var capsLockColor: String = "#FFCC00"

    // Insert 模式自动切换规则
    // 默认：光标前以中文字符结尾，或光标后以中文字符开头时切换为中文模式
    var insertModeChineseBeforeRegex: String = ".*[\u4e00-\u9fa5]$"
    var insertModeChineseAfterRegex: String = "^[\u4e00-\u9fa5].*"
    // 默认：光标前以大写/数字/下划线结尾时切换为大写模式；光标后规则默认不启用
    var insertModeCapsBeforeRegex: String = ".*[A-Z]{2,}[0-9_]?\$"
    var insertModeCapsAfterRegex: String = ""

    // 日志开关（默认全部关闭）
    var logError: Boolean = false
    var logWarn: Boolean = false
    var logInfo: Boolean = false
    var logDebug: Boolean = false

    override fun getState(): AutoSwitchIMESettings = this

    override fun loadState(state: AutoSwitchIMESettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: AutoSwitchIMESettings
            get() = ApplicationManager.getApplication().getService(AutoSwitchIMESettings::class.java)
    }
}

class AutoSwitchIMESettingsConfigurable : Configurable {

    private var settingsPanel: JPanel? = null
    private var enabledCheckBox: JCheckBox? = null
    private var pathField: JBTextField? = null
    private var englishColorPanel: ColorPanel? = null
    private var chineseColorPanel: ColorPanel? = null
    private var capsLockColorPanel: ColorPanel? = null
    private var insertModeChineseBeforeRegexField: JBTextField? = null
    private var insertModeChineseAfterRegexField: JBTextField? = null
    private var insertModeCapsBeforeRegexField: JBTextField? = null
    private var insertModeCapsAfterRegexField: JBTextField? = null
    private var logErrorCheckBox: JCheckBox? = null
    private var logWarnCheckBox: JCheckBox? = null
    private var logInfoCheckBox: JCheckBox? = null
    private var logDebugCheckBox: JCheckBox? = null

    // 调试区域
    private var testConfigButton: JButton? = null
    private var configStatusArea: JTextArea? = null
    private var regexBeforeField: JBTextField? = null
    private var regexAfterField: JBTextField? = null
    private var regexTestButton: JButton? = null
    private var regexResultArea: JTextArea? = null

    @Nls(capitalization = Nls.Capitalization.Title)
    override fun getDisplayName(): String = "自动切换输入"

    override fun createComponent(): JComponent {
        settingsPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.anchor = GridBagConstraints.WEST

        // 启用开关
        enabledCheckBox = JCheckBox("启用自动切换输入")
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

        // 中文规则 - 光标前
        gbc.gridy = 7; gbc.gridx = 0; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE
        settingsPanel!!.add(JBLabel("中文规则 (光标前):"), gbc)
        insertModeChineseBeforeRegexField = JBTextField()
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        settingsPanel!!.add(insertModeChineseBeforeRegexField, gbc)

        // 中文规则 - 光标后
        gbc.gridy = 8; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE
        settingsPanel!!.add(JBLabel("中文规则 (光标后):"), gbc)
        insertModeChineseAfterRegexField = JBTextField()
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL
        settingsPanel!!.add(insertModeChineseAfterRegexField, gbc)

        // 大写规则 - 光标前
        gbc.gridy = 9; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE
        settingsPanel!!.add(JBLabel("大写规则 (光标前):"), gbc)
        insertModeCapsBeforeRegexField = JBTextField()
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL
        settingsPanel!!.add(insertModeCapsBeforeRegexField, gbc)

        // 大写规则 - 光标后
        gbc.gridy = 10; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE
        settingsPanel!!.add(JBLabel("大写规则 (光标后):"), gbc)
        insertModeCapsAfterRegexField = JBTextField()
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL
        settingsPanel!!.add(insertModeCapsAfterRegexField, gbc)

        // 分隔线
        gbc.gridy = 11; gbc.gridx = 0; gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        settingsPanel!!.add(JSeparator(), gbc)

        // 日志开关
        gbc.gridy = 12; gbc.gridx = 0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE
        settingsPanel!!.add(JBLabel("日志输出开关:"), gbc)
        
        gbc.gridy = 13; gbc.gridx = 0; gbc.gridwidth = 1
        logErrorCheckBox = JCheckBox("错误 (ERROR)")
        settingsPanel!!.add(logErrorCheckBox, gbc)
        
        gbc.gridx = 1
        logWarnCheckBox = JCheckBox("警告 (WARN)")
        settingsPanel!!.add(logWarnCheckBox, gbc)
        
        gbc.gridy = 14; gbc.gridx = 0
        logInfoCheckBox = JCheckBox("信息 (INFO)")
        settingsPanel!!.add(logInfoCheckBox, gbc)
        
        gbc.gridx = 1
        logDebugCheckBox = JCheckBox("调试 (DEBUG)")
        settingsPanel!!.add(logDebugCheckBox, gbc)

        // 分隔线
        gbc.gridy = 15; gbc.gridx = 0; gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        settingsPanel!!.add(JSeparator(), gbc)

        // 调试区域标题
        gbc.gridy = 16; gbc.gridx = 0; gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.NONE
        settingsPanel!!.add(JBLabel("调试工具:"), gbc)

        // 配置检测按钮
        gbc.gridy = 17; gbc.gridx = 0; gbc.gridwidth = 1
        testConfigButton = JButton("检测配置状态")
        settingsPanel!!.add(testConfigButton, gbc)

        // 配置状态显示区域
        configStatusArea = JTextArea(4, 50)
        configStatusArea!!.isEditable = false
        configStatusArea!!.lineWrap = true
        configStatusArea!!.wrapStyleWord = true
        gbc.gridy = 18; gbc.gridx = 0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        settingsPanel!!.add(configStatusArea, gbc)

        // 正则测试区域
        gbc.gridy = 19; gbc.gridx = 0; gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.NONE
        settingsPanel!!.add(JBLabel("正则规则测试 (分别输入光标前/后文本):"), gbc)

        gbc.gridy = 20; gbc.gridx = 0; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE
        settingsPanel!!.add(JBLabel("光标前:"), gbc)
        regexBeforeField = JBTextField()
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        settingsPanel!!.add(regexBeforeField, gbc)

        gbc.gridy = 21; gbc.gridx = 0; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE
        settingsPanel!!.add(JBLabel("光标后:"), gbc)
        regexAfterField = JBTextField()
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL
        settingsPanel!!.add(regexAfterField, gbc)

        gbc.gridy = 22; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE
        regexTestButton = JButton("测试匹配")
        settingsPanel!!.add(regexTestButton, gbc)

        // 正则测试结果
        regexResultArea = JTextArea(3, 50)
        regexResultArea!!.isEditable = false
        regexResultArea!!.lineWrap = true
        regexResultArea!!.wrapStyleWord = true
        gbc.gridy = 23; gbc.gridx = 0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        settingsPanel!!.add(regexResultArea, gbc)

        // 使用 BorderLayout 包装，消除顶部空白
        val wrapper = JPanel(BorderLayout())
        wrapper.add(settingsPanel!!, BorderLayout.NORTH)

        // 按钮监听器（必须在组件创建后添加）
        testConfigButton?.addActionListener {
            testConfiguration()
        }
        regexTestButton?.addActionListener {
            testRegex()
        }

        return wrapper
    }

    override fun isModified(): Boolean {
        val settings = AutoSwitchIMESettings.instance
        return enabledCheckBox?.isSelected != settings.enabled ||
                pathField?.text != settings.weaselServerPath ||
                englishColorPanel?.selectedColor?.let { toHex(it) } != settings.englishColor ||
                chineseColorPanel?.selectedColor?.let { toHex(it) } != settings.chineseColor ||
                capsLockColorPanel?.selectedColor?.let { toHex(it) } != settings.capsLockColor ||
                insertModeChineseBeforeRegexField?.text != settings.insertModeChineseBeforeRegex ||
                insertModeChineseAfterRegexField?.text != settings.insertModeChineseAfterRegex ||
                insertModeCapsBeforeRegexField?.text != settings.insertModeCapsBeforeRegex ||
                insertModeCapsAfterRegexField?.text != settings.insertModeCapsAfterRegex ||
                logErrorCheckBox?.isSelected != settings.logError ||
                logWarnCheckBox?.isSelected != settings.logWarn ||
                logInfoCheckBox?.isSelected != settings.logInfo ||
                logDebugCheckBox?.isSelected != settings.logDebug
    }

    override fun apply() {
        val settings = AutoSwitchIMESettings.instance
        settings.enabled = enabledCheckBox?.isSelected ?: true
        
        // 验证 WeaselServer 路径
        val pathText = pathField?.text?.trim() ?: ""
        if (pathText.isNotBlank()) {
            val file = File(pathText)
            if (!file.exists()) {
                throw ConfigurationException("WeaselServer.exe 不存在: $pathText")
            }
            if (file.isDirectory) {
                throw ConfigurationException("路径应为 WeaselServer.exe 文件，而非目录: $pathText\n\n正确示例: D:\\Program Files\\Rime\\weasel-0.17.4\\WeaselServer.exe")
            }
            if (!file.name.equals("WeaselServer.exe", ignoreCase = true)) {
                throw ConfigurationException("文件名应为 WeaselServer.exe，当前: ${file.name}")
            }
        }
        settings.weaselServerPath = pathText
        
        englishColorPanel?.selectedColor?.let { settings.englishColor = toHex(it) }
        chineseColorPanel?.selectedColor?.let { settings.chineseColor = toHex(it) }
        capsLockColorPanel?.selectedColor?.let { settings.capsLockColor = toHex(it) }
        settings.insertModeChineseBeforeRegex = insertModeChineseBeforeRegexField?.text ?: ""
        settings.insertModeChineseAfterRegex = insertModeChineseAfterRegexField?.text ?: ""
        settings.insertModeCapsBeforeRegex = insertModeCapsBeforeRegexField?.text ?: ""
        settings.insertModeCapsAfterRegex = insertModeCapsAfterRegexField?.text ?: ""
        settings.logError = logErrorCheckBox?.isSelected ?: true
        settings.logWarn = logWarnCheckBox?.isSelected ?: true
        settings.logInfo = logInfoCheckBox?.isSelected ?: true
        settings.logDebug = logDebugCheckBox?.isSelected ?: false
    }

    override fun reset() {
        val settings = AutoSwitchIMESettings.instance
        enabledCheckBox?.isSelected = settings.enabled
        pathField?.text = settings.weaselServerPath.ifBlank { WeaselPathDetector.detect() ?: "" }
        englishColorPanel?.selectedColor = decodeColor(settings.englishColor)
        chineseColorPanel?.selectedColor = decodeColor(settings.chineseColor)
        capsLockColorPanel?.selectedColor = decodeColor(settings.capsLockColor)
        insertModeChineseBeforeRegexField?.text = settings.insertModeChineseBeforeRegex
        insertModeChineseAfterRegexField?.text = settings.insertModeChineseAfterRegex
        insertModeCapsBeforeRegexField?.text = settings.insertModeCapsBeforeRegex
        insertModeCapsAfterRegexField?.text = settings.insertModeCapsAfterRegex
        logErrorCheckBox?.isSelected = settings.logError
        logWarnCheckBox?.isSelected = settings.logWarn
        logInfoCheckBox?.isSelected = settings.logInfo
        logDebugCheckBox?.isSelected = settings.logDebug
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

    /**
     * 检测当前配置状态并显示结果
     */
    private fun testConfiguration() {
        val sb = StringBuilder()
        val settings = try {
            AutoSwitchIMESettings.instance
        } catch (e: Exception) {
            configStatusArea?.text = "配置加载失败: ${e.message}"
            return
        }

        sb.append("=== 配置状态检测 ===\n\n")

        // 1. 插件启用状态
        sb.append("插件启用: ${if (settings.enabled) "是" else "否"}\n")

        // 2. WeaselServer 路径
        val pathText = pathField?.text?.trim() ?: ""
        if (pathText.isBlank()) {
            val detected = WeaselPathDetector.detect()
            if (detected != null) {
                sb.append("WeaselServer: 未配置 (自动检测: $detected)\n")
            } else {
                sb.append("WeaselServer: 未配置，自动检测失败\n")
            }
        } else {
            val file = File(pathText)
            if (file.isFile) {
                sb.append("WeaselServer: $pathText (有效)\n")
            } else if (file.isDirectory) {
                sb.append("WeaselServer: $pathText (错误: 是目录，不是文件)\n")
            } else {
                sb.append("WeaselServer: $pathText (错误: 文件不存在)\n")
            }
        }

        // 3. IME 当前状态
        try {
            val controller = ApplicationManager.getApplication().getService(com.auto_switch_ime.ime.AutoSwitchIMEController::class.java)
            val imeState = if (controller != null) {
                ImeStateDetector.getCurrentState(controller.stateWatcher, controller.getTrackedState())
            } else {
                com.auto_switch_ime.core.ImeState(true, false)
            }
            sb.append("当前 IME 状态: ${if (imeState.isAsciiMode) "英文(ASCII)" else "中文"}, CapsLock: ${if (imeState.isCapsLock) "开" else "关"}\n")
        } catch (e: Exception) {
            sb.append("IME 状态检测失败: ${e.message}\n")
        }

        // 4. 日志开关
        sb.append("日志: E=${settings.logError} W=${settings.logWarn} I=${settings.logInfo} D=${settings.logDebug}\n")

        // 5. 正则规则
        sb.append("\n=== 自动切换规则 ===\n")
        sb.append("中文(前): ${settings.insertModeChineseBeforeRegex}\n")
        sb.append("中文(后): ${settings.insertModeChineseAfterRegex}\n")
        sb.append("大写(前): ${settings.insertModeCapsBeforeRegex}\n")
        sb.append("大写(后): ${settings.insertModeCapsAfterRegex}\n")

        // 6. 验证正则语法
        sb.append("\n=== 正则语法检查 ===\n")
        validateRegex(sb, "中文(前)", settings.insertModeChineseBeforeRegex)
        validateRegex(sb, "中文(后)", settings.insertModeChineseAfterRegex)
        validateRegex(sb, "大写(前)", settings.insertModeCapsBeforeRegex)
        validateRegex(sb, "大写(后)", settings.insertModeCapsAfterRegex)

        configStatusArea?.text = sb.toString()
    }

    /**
     * 验证正则表达式语法
     */
    private fun validateRegex(sb: StringBuilder, name: String, pattern: String) {
        if (pattern.isBlank()) {
            sb.append("$name: (空)\n")
            return
        }
        try {
            Pattern.compile(pattern)
            sb.append("$name: 语法正确\n")
        } catch (e: Exception) {
            sb.append("$name: 语法错误 - ${e.message}\n")
        }
    }

    /**
     * 测试正则规则匹配（使用独立的光标前/后文本框）
     */
    private fun testRegex() {
        val before = regexBeforeField?.text ?: ""
        val after = regexAfterField?.text ?: ""

        if (before.isBlank() && after.isBlank()) {
            regexResultArea?.text = "请输入光标前或光标后的测试文本"
            return
        }

        val settings = try {
            AutoSwitchIMESettings.instance
        } catch (e: Exception) {
            regexResultArea?.text = "配置加载失败: ${e.message}"
            return
        }

        val sb = StringBuilder()
        sb.append("光标前: \"$before\"\n")
        sb.append("光标后: \"$after\"\n\n")

        // 测试中文规则（前）- 匹配 before 文本
        testRegexMatch(sb, "中文规则(前)", settings.insertModeChineseBeforeRegex, before)
        // 测试中文规则（后）- 匹配 after 文本
        testRegexMatch(sb, "中文规则(后)", settings.insertModeChineseAfterRegex, after)
        // 测试大写规则（前）- 匹配 before 文本
        testRegexMatch(sb, "大写规则(前)", settings.insertModeCapsBeforeRegex, before)
        // 测试大写规则（后）- 匹配 after 文本
        testRegexMatch(sb, "大写规则(后)", settings.insertModeCapsAfterRegex, after)

        sb.append("\n匹配结果: ")
        val chineseBeforeMatch = matches(settings.insertModeChineseBeforeRegex, before)
        val chineseAfterMatch = matches(settings.insertModeChineseAfterRegex, after)
        val capsBeforeMatch = matches(settings.insertModeCapsBeforeRegex, before)
        val capsAfterMatch = matches(settings.insertModeCapsAfterRegex, after)

        if (chineseBeforeMatch || chineseAfterMatch) {
            sb.append("中文模式")
        } else if (capsBeforeMatch || capsAfterMatch) {
            sb.append("大写模式")
        } else {
            sb.append("英文模式(默认)")
        }

        regexResultArea?.text = sb.toString()
    }

    private fun testRegexMatch(sb: StringBuilder, name: String, pattern: String, text: String) {
        if (pattern.isBlank()) {
            sb.append("$name: (未设置)\n")
            return
        }
        val matched = matches(pattern, text)
        sb.append("$name: ${if (matched) "匹配" else "不匹配"}\n")
    }

    private fun matches(pattern: String, text: String): Boolean {
        if (pattern.isBlank()) return false
        return try {
            Pattern.compile(pattern).matcher(text).find()
        } catch (e: Exception) {
            false
        }
    }
}
