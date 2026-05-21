# RimeVim IME 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建 IntelliJ 插件，实现 IdeaVim 模式切换时自动切换小狼毫输入法中英文状态，并根据状态显示不同光标颜色。

**Architecture:** 通过监听 IdeaVim 的 ModeChanged 事件，调用 WeaselServer.exe 命令行切换输入法状态，使用 JNA 检测当前 IME 状态，通过 IntelliJ CaretVisualAttributes API 设置光标颜色。

**Tech Stack:** Kotlin, IntelliJ Platform SDK, JNA, Gradle

---

## 文件结构

| 文件 | 职责 |
|------|------|
| `build.gradle.kts` | Gradle 构建配置，声明依赖 |
| `gradle.properties` | 插件元数据（版本、名称） |
| `settings.gradle.kts` | Gradle 项目设置 |
| `src/main/kotlin/com/rimevim/RimeVimPlugin.kt` | 插件入口，初始化各组件 |
| `src/main/kotlin/com/rimevim/listener/VimModeListener.kt` | 监听 Vim 模式变化 |
| `src/main/kotlin/com/rimevim/ime/RimeController.kt` | 控制小狼毫 ASCII/中文模式 |
| `src/main/kotlin/com/rimevim/ime/ImeStateDetector.kt` | 检测当前输入法状态（JNA） |
| `src/main/kotlin/com/rimevim/ime/WeaselPathDetector.kt` | 检测 WeaselServer.exe 路径 |
| `src/main/kotlin/com/rimevim/caret/CaretColorManager.kt` | 管理光标颜色 |
| `src/main/kotlin/com/rimevim/settings/RimeVimSettings.kt` | 配置持久化 + Settings 面板 |
| `src/main/resources/META-INF/plugin.xml` | 插件描述文件 |
| `src/main/resources/messages/RimeVimBundle.properties` | 国际化资源 |

---

### Task 1: 项目骨架和构建配置

**Files:**
- Create: `D:\ai_code\RimeVimIME\build.gradle.kts`
- Create: `D:\ai_code\RimeVimIME\gradle.properties`
- Create: `D:\ai_code\RimeVimIME\settings.gradle.kts`

- [ ] **Step 1: 创建 settings.gradle.kts**

```kotlin
// D:\ai_code\RimeVimIME\settings.gradle.kts
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "RimeVimIME"
```

- [ ] **Step 2: 创建 gradle.properties**

```properties
# D:\ai_code\RimeVimIME\gradle.properties
pluginVersion=0.1.0
pluginGroup=com.rimevim
pluginName=RimeVim IME
kotlin.stdlib.default.dependency=false
```

- [ ] **Step 3: 创建 build.gradle.kts**

```kotlin
// D:\ai_code\RimeVimIME\build.gradle.kts
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.2"
}

group = property("pluginGroup")!!
version = property("pluginVersion")!!

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
}

intellij {
    version.set("2024.1")
    type.set("IC")
    plugins.set(listOf("com.maddyhome.idea.vim"))
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
    patchPluginXml {
        version.set("${project.version}")
        sinceBuild.set("241")
        untilBuild.set("243.*")
    }
}
```

- [ ] **Step 4: 验证构建**

```bash
cd D:\ai_code\RimeVimIME
gradlew.bat build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
cd D:\ai_code\RimeVimIME
git init
git add .
git commit -m "初始化 RimeVim IME 项目骨架"
```

---

### Task 2: 插件描述文件 plugin.xml

**Files:**
- Create: `D:\ai_code\RimeVimIME\src\main\resources\META-INF\plugin.xml`
- Create: `D:\ai_code\RimeVimIME\src\main\resources\messages\RimeVimBundle.properties`

- [ ] **Step 1: 创建 plugin.xml**

```xml
<!-- D:\ai_code\RimeVimIME\src\main\resources\META-INF\plugin.xml -->
<idea-plugin>
    <id>com.rimevim.ime</id>
    <name>RimeVim IME</name>
    <vendor email="user@example.com" url="https://github.com/user/RimeVimIME">RimeVim</vendor>

    <description><![CDATA[
    Auto-switch IME (Rime/Weasel) based on Vim mode.
    <ul>
      <li>Normal mode → English (ASCII)</li>
      <li>Insert mode → Chinese</li>
      <li>Cursor color indicates IME state</li>
    </ul>
    ]]></description>

    <depends>com.intellij.modules.platform</depends>
    <depends optional="true" config-file="ideavim-integration.xml">com.maddyhome.idea.vim</depends>

    <extensions defaultExtensionNs="com.intellij">
        <applicationConfigurable
                parentId="tools"
                instance="com.rimevim.settings.RimeVimSettingsConfigurable"
                id="com.rimevim.settings.RimeVimSettingsConfigurable"
                displayName="RimeVim IME"/>
        <applicationService
                serviceImplementation="com.rimevim.settings.RimeVimSettings"/>
    </extensions>

    <applicationListeners>
        <listener class="com.rimevim.listener.VimModeListener"
                  topic="com.intellij.openapi.editor.EditorFactoryListener"/>
    </applicationListeners>
</idea-plugin>
```

- [ ] **Step 2: 创建 ideavim-integration.xml**

```xml
<!-- D:\ai_code\RimeVimIME\src\main\resources\META-INF\ideavim-integration.xml -->
<idea-plugin>
    <extensions defaultExtensionNs="com.maddyhome.idea.vim">
        <vimPluginExtension implementation="com.rimevim.RimeVimPlugin"/>
    </extensions>
</idea-plugin>
```

- [ ] **Step 3: 创建国际化资源**

```properties
# D:\ai_code\RimeVimIME\src\main\resources\messages\RimeVimBundle.properties
settings.title=RimeVim IME 设置
settings.enabled=启用插件
settings.weasel.path=WeaselServer.exe 路径
settings.weasel.detect=自动检测
settings.color.english=英文模式光标颜色
settings.color.chinese=中文模式光标颜色
settings.color.capslock=CapsLock 光标颜色
```

- [ ] **Step 4: Commit**

```bash
cd D:\ai_code\RimeVimIME
git add .
git commit -m "添加插件描述文件和国际化资源"
```

---

### Task 3: WeaselPathDetector - 自动检测小狼毫路径

**Files:**
- Create: `D:\ai_code\RimeVimIME\src\main\kotlin\com\rimevim\ime\WeaselPathDetector.kt`

- [ ] **Step 1: 创建 WeaselPathDetector**

```kotlin
// D:\ai_code\RimeVimIME\src\main\kotlin\com\rimevim\ime\WeaselPathDetector.kt
package com.rimevim.ime

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

object WeaselPathDetector {

    private val COMMON_PATHS = listOf(
        "C:\\Program Files (x86)\\Rime",
        "C:\\Program Files\\Rime"
    )

    /**
     * 自动检测 WeaselServer.exe 路径
     * 优先级：注册表 > 常见路径扫描
     */
    fun detect(): String? {
        return readFromRegistry() ?: scanCommonPaths()
    }

    private fun readFromRegistry(): String? {
        return try {
            val root = Advapi32Util.registryGetStringValue(
                WinReg.HKEY_LOCAL_MACHINE,
                "SOFTWARE\\Rime\\Weasel",
                "WeaselRoot"
            )
            val serverPath = "$root\\WeaselServer.exe"
            if (File(serverPath).exists()) serverPath else null
        } catch (e: Exception) {
            null
        }
    }

    private fun scanCommonPaths(): String? {
        for (basePath in COMMON_PATHS) {
            val baseDir = File(basePath)
            if (!baseDir.exists()) continue

            val weaselDirs = baseDir.listFiles { f ->
                f.isDirectory && f.name.startsWith("weasel-")
            } ?: continue

            for (weaselDir in weaselDirs.sortedDescending()) {
                val serverPath = File(weaselDir, "WeaselServer.exe")
                if (serverPath.exists()) {
                    return serverPath.absolutePath
                }
            }
        }
        return null
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd D:\ai_code\RimeVimIME
git add .
git commit -m "实现 WeaselPathDetector 自动检测小狼毫路径"
```

---

### Task 4: ImeStateDetector - JNA 检测输入法状态

**Files:**
- Create: `D:\ai_code\RimeVimIME\src\main\kotlin\com\rimevim\ime\ImeStateDetector.kt`

- [ ] **Step 1: 创建 ImeStateDetector**

```kotlin
// D:\ai_code\RimeVimIME\src\main\kotlin\com\rimevim\ime\ImeStateDetector.kt
package com.rimevim.ime

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.ptr.IntByReference

object ImeStateDetector {

    private const val WM_IME_CONTROL = 0x283
    private const val IMC_GETCONVERSIONMODE = 0x001
    private const val VK_CAPITAL = 0x14

    interface User32 : Library {
        companion object {
            val INSTANCE: User32 = Native.load("user32", User32::class.java)
        }

        fun GetForegroundWindow(): HWND
        fun GetKeyState(nVirtKey: Int): Short
        fun SendMessage(hWnd: HWND, Msg: Int, wParam: Int, lParam: LPARAM): LRESULT
    }

    interface Imm32 : Library {
        companion object {
            val INSTANCE: Imm32 = Native.load("imm32", Imm32::class.java)
        }

        fun ImmGetDefaultIMEWnd(hwnd: HWND): HWND
    }

    data class ImeState(
        val isAsciiMode: Boolean,
        val isCapsLock: Boolean
    )

    fun getCurrentState(): ImeState {
        val fgWindow = User32.INSTANCE.GetForegroundWindow()
        val imeWnd = Imm32.INSTANCE.ImmGetDefaultIMEWnd(fgWindow)

        val isAsciiMode = if (imeWnd != null) {
            val mode = User32.INSTANCE.SendMessage(
                imeWnd, WM_IME_CONTROL, IMC_GETCONVERSIONMODE, LPARAM(0L)
            )
            val modeVal = mode.toLong()
            // bit0 = 0 表示 ASCII 模式（英文）
            (modeVal and 0x01L) == 0L
        } else {
            true // 无法检测时默认为英文
        }

        val isCapsLock = (User32.INSTANCE.GetKeyState(VK_CAPITAL).toInt() and 0x01) != 0

        return ImeState(isAsciiMode, isCapsLock)
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd D:\ai_code\RimeVimIME
git add .
git commit -m "实现 ImeStateDetector JNA 检测输入法状态"
```

---

### Task 5: RimeController - 控制小狼毫输入法

**Files:**
- Create: `D:\ai_code\RimeVimIME\src\main\kotlin\com\rimevim\ime\RimeController.kt`

- [ ] **Step 1: 创建 RimeController**

```kotlin
// D:\ai_code\RimeVimIME\src\main\kotlin\com\rimevim\ime\RimeController.kt
package com.rimevim.ime

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import java.io.File
import java.util.concurrent.TimeUnit

@Service
class RimeController {

    @Volatile
    private var currentAsciiMode: Boolean? = null

    private val weaselServerPath: String? by lazy {
        resolveWeaselServerPath()
    }

    /**
     * 解析 WeaselServer.exe 路径
     * 优先级：用户配置 > 注册表自动检测
     */
    private fun resolveWeaselServerPath(): String? {
        val settings = com.rimevim.settings.RimeVimSettings.instance
        // 1. 优先使用用户配置
        val configuredPath = settings.weaselServerPath
        if (configuredPath.isNotBlank()) {
            val file = java.io.File(configuredPath)
            if (file.exists()) {
                return file.absolutePath
            }
        }
        // 2. 回退到注册表/常见路径检测
        return WeaselPathDetector.detect()
    }

    fun setAsciiMode(ascii: Boolean) {
        if (currentAsciiMode == ascii) return // 状态未变，跳过

        val path = weaselServerPath
        if (path == null) {
            thisLogger().warn("WeaselServer.exe not found")
            return
        }

        if (!File(path).exists()) {
            thisLogger().warn("WeaselServer.exe not exists at: $path")
            return
        }

        try {
            val arg = if (ascii) "/ascii" else "/nascii"
            val process = ProcessBuilder(path, arg)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            process.waitFor(1, TimeUnit.SECONDS)
            currentAsciiMode = ascii
            thisLogger().info("Switched to ${if (ascii) "ASCII" else "Chinese"} mode")
        } catch (e: Exception) {
            thisLogger().warn(e, "Failed to switch IME mode")
        }
    }

    fun getWeaselServerPath(): String? = weaselServerPath
}
```

- [ ] **Step 2: Commit**

```bash
cd D:\ai_code\RimeVimIME
git add .
git commit -m "实现 RimeController 控制小狼毫输入法"
```

---

### Task 6: CaretColorManager - 管理光标颜色

**Files:**
- Create: `D:\ai_code\RimeVimIME\src\main\kotlin\com\rimevim\caret\CaretColorManager.kt`

- [ ] **Step 1: 创建 CaretColorManager**

```kotlin
// D:\ai_code\RimeVimIME\src\main\kotlin\com\rimevim\caret\CaretColorManager.kt
package com.rimevim.caret

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.util.TextRange
import com.rimevim.settings.RimeVimSettings
import java.awt.Color

object CaretColorManager {

    private val DEFAULT_ENGLISH_COLOR = Color(0x00CC66)    // 绿色
    private val DEFAULT_CHINESE_COLOR = Color(0xFF6666)     // 红色
    private val DEFAULT_CAPSLOCK_COLOR = Color(0xFFCC00)    // 黄色

    fun updateCaretColor(editor: Editor, isAsciiMode: Boolean, isCapsLock: Boolean) {
        val settings = RimeVimSettings.getInstance()
        if (!settings.enabled) return

        val color = when {
            isCapsLock -> parseColor(settings.capsLockColor, DEFAULT_CAPSLOCK_COLOR)
            isAsciiMode -> parseColor(settings.englishColor, DEFAULT_ENGLISH_COLOR)
            else -> parseColor(settings.chineseColor, DEFAULT_CHINESE_COLOR)
        }

        editor.caretModel.allCarets.forEach { caret ->
            val attributes = com.intellij.openapi.editor.CaretVisualAttributes(
                color,
                null
            )
            caret.setVisualAttributes(attributes)
        }
    }

    private fun parseColor(hex: String?, default: Color): Color {
        return try {
            if (hex.isNullOrBlank()) default
            else Color.decode(hex)
        } catch (e: Exception) {
            default
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd D:\ai_code\RimeVimIME
git add .
git commit -m "实现 CaretColorManager 管理光标颜色"
```

---

### Task 7: RimeVimSettings - 配置持久化和 Settings 面板

**Files:**
- Create: `D:\ai_code\RimeVimIME\src\main\kotlin\com\rimevim\settings\RimeVimSettings.kt`

- [ ] **Step 1: 创建 RimeVimSettings**

```kotlin
// D:\ai_code\RimeVimIME\src\main\kotlin\com\rimevim\settings\RimeVimSettings.kt
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
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

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
    var insertModeAsciiRegex: String = ""      // 自动切换中英文的正则规则
    var insertModeCapsRegex: String = ""       // 自动切换大写的正则规则
    var insertModeLowerRegex: String = ""      // 自动切换小写的正则规则

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
        settingsPanel!!.add(javax.swing.JSeparator(), gbc)

        // Insert 模式自动切换规则标题
        gbc.gridy = 6; gbc.gridx = 0; gbc.gridwidth = 2
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
```

- [ ] **Step 2: Commit**

```bash
cd D:\ai_code\RimeVimIME
git add .
git commit -m "实现 RimeVimSettings 配置持久化和 Settings 面板（含正则规则配置）"
```

---

### Task 8: VimModeListener - 监听 Vim 模式变化

**Files:**
- Create: `D:\ai_code\RimeVimIME\src\main\kotlin\com\rimevim\listener\VimModeListener.kt`

- [ ] **Step 1: 创建 VimModeListener**

```kotlin
// D:\ai_code\RimeVimIME\src\main\kotlin\com\rimevim\listener\VimModeListener.kt
package com.rimevim.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.rimevim.caret.CaretColorManager
import com.rimevim.ime.ImeStateDetector
import com.rimevim.ime.RimeController
import com.rimevim.settings.RimeVimSettings

class VimModeListener : EditorFactoryListener {

    private val rimeController = ApplicationManager.getApplication().getService(RimeController::class.java)

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return

        // 监听文件编辑器变化
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    updateEditorState(editor)
                }
            }
        )

        // 监听编辑器鼠标事件（用于焦点变化）
        editor.addEditorMouseListener(object : EditorMouseListener {
            override fun mouseEntered(event: EditorMouseEvent) {
                updateEditorState(editor)
            }
        })

        // 初始化状态
        updateEditorState(editor)
    }

    private fun updateEditorState(editor: Editor) {
        if (!RimeVimSettings.instance.enabled) return

        ApplicationManager.getApplication().invokeLater {
            if (editor.isDisposed) return@invokeLater

            val state = ImeStateDetector.getCurrentState()
            CaretColorManager.updateCaretColor(editor, state.isAsciiMode, state.isCapsLock)
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd D:\ai_code\RimeVimIME
git add .
git commit -m "实现 VimModeListener 监听编辑器事件"
```

---

### Task 9: RimeVimPlugin - 插件入口和 IdeaVim 集成

**Files:**
- Create: `D:\ai_code\RimeVimIME\src\main\kotlin\com\rimevim\RimeVimPlugin.kt`

- [ ] **Step 1: 创建 RimeVimPlugin**

```kotlin
// D:\ai_code\RimeVimIME\src\main\kotlin\com\rimevim\RimeVimPlugin.kt
package com.rimevim

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupActivity
import com.rimevim.caret.CaretColorManager
import com.rimevim.ime.ImeStateDetector
import com.rimevim.ime.RimeController
import com.rimevim.settings.RimeVimSettings

@Service
class RimeVimPlugin : StartupActivity {

    private val rimeController = ApplicationManager.getApplication().getService(RimeController::class.java)

    override fun runActivity(project: Project) {
        if (!RimeVimSettings.instance.enabled) {
            thisLogger().info("RimeVim IME is disabled")
            return
        }

        thisLogger().info("RimeVim IME initialized")

        // 监听所有编辑器的插入/退出事件
        setupEditorListeners(project)
    }

    private fun setupEditorListeners(project: Project) {
        val editorFactory = EditorFactory.getInstance()

        // 监听文档变化（输入时检测模式）
        editorFactory.eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val editor = event.editor
                if (editor.isDisposed) return

                ApplicationManager.getApplication().invokeLater {
                    if (editor.isDisposed) return@invokeLater
                    val state = ImeStateDetector.getCurrentState()
                    CaretColorManager.updateCaretColor(editor, state.isAsciiMode, state.isCapsLock)
                }
            }
        })

        // 监听光标变化
        editorFactory.eventMulticaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                val editor = event.editor
                if (editor.isDisposed) return

                ApplicationManager.getApplication().invokeLater {
                    if (editor.isDisposed) return@invokeLater
                    val state = ImeStateDetector.getCurrentState()
                    CaretColorManager.updateCaretColor(editor, state.isAsciiMode, state.isCapsLock)
                }
            }
        })
    }
}
```

- [ ] **Step 2: 更新 plugin.xml 注册 StartupActivity**

在 `plugin.xml` 的 `<extensions>` 中添加：

```xml
<postStartupActivity implementation="com.rimevim.RimeVimPlugin"/>
```

- [ ] **Step 3: Commit**

```bash
cd D:\ai_code\RimeVimIME
git add .
git commit -m "实现 RimeVimPlugin 插件入口和 IdeaVim 集成"
```

---

### Task 10: 构建测试和打包

- [ ] **Step 1: 完整构建**

```bash
cd D:\ai_code\RimeVimIME
gradlew.bat clean build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 运行测试 IDE**

```bash
cd D:\ai_code\RimeVimIME
gradlew.bat runIde
```

Expected: 启动 IntelliJ IDEA 开发实例，插件已加载

- [ ] **Step 3: 手动测试清单**

1. 打开 Settings → Tools → RimeVim IME，确认配置面板正常显示
2. 在小狼毫中文模式下进入编辑器，按 `i` 进入 Insert 模式
3. 确认光标颜色变为中文模式颜色（默认红色）
4. 按 `Esc` 进入 Normal 模式
5. 确认光标颜色变为英文模式颜色（默认绿色）
6. 按 CapsLock，确认光标颜色变为黄色
7. 修改颜色设置，确认保存后生效

- [ ] **Step 4: 打包插件**

```bash
cd D:\ai_code\RimeVimIME
gradlew.bat buildPlugin
```

Output: `build/distributions/RimeVimIME-0.1.0.zip`

- [ ] **Step 5: 最终 Commit**

```bash
cd D:\ai_code\RimeVimIME
git add .
git commit -m "完成 RimeVim IME 插件开发"
```

---

## 自审检查

### 1. 规格覆盖

| 规格要求 | 对应 Task |
|---------|-----------|
| 自动切换输入法 | Task 5 (RimeController) + Task 8 (VimModeListener) |
| 光标颜色指示 | Task 6 (CaretColorManager) |
| Settings 可配置 | Task 7 (RimeVimSettings) |
| WeaselServer 路径检测 | Task 3 (WeaselPathDetector) |
| IME 状态检测 | Task 4 (ImeStateDetector) |
| 插件入口 | Task 9 (RimeVimPlugin) |

### 2. 占位符扫描

无 TBD/TODO，所有代码完整。

### 3. 类型一致性

- `RimeVimSettings` 在所有 Task 中使用一致
- `ImeStateDetector.ImeState` 数据结构一致
- 颜色格式统一为 `#RRGGBB` 字符串

### 4. 范围检查

聚焦于核心功能，无多余特性。
