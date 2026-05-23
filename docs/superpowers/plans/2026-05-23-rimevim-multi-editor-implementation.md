# RimeVimIME 多编辑器架构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 RimeVimIME 从单一 IntelliJ 插件重构为共享核心库 + 多平台适配架构，支持 IntelliJ 和 VSCode 两个编辑器。

**Architecture:** 提取平台无关的 IME 切换逻辑到 `core/` 模块，通过 `ImeProvider` 接口支持多种输入法。IntelliJ 插件和 VSCode 扩展分别依赖 core 模块。

**Tech Stack:** Kotlin 2.3.0, Gradle 8.10, IntelliJ Platform 2.8.0, JNA 5.14.0, TypeScript (VSCode)

---

## 文件结构

### 新增文件
- `core/build.gradle.kts` - 核心库构建配置
- `core/src/main/kotlin/com/rimevim/core/ImeAction.kt` - 纯枚举
- `core/src/main/kotlin/com/rimevim/core/ImeProvider.kt` - 输入法接口
- `core/src/main/kotlin/com/rimevim/core/ImeProviderFactory.kt` - 工厂类
- `core/src/main/kotlin/com/rimevim/core/ImeType.kt` - 输入法类型枚举
- `core/src/main/kotlin/com/rimevim/core/ImeConfig.kt` - 配置数据类
- `core/src/main/kotlin/com/rimevim/core/ImeConstants.kt` - 常量定义
- `core/src/main/kotlin/com/rimevim/core/ImeException.kt` - 异常定义
- `core/src/main/kotlin/com/rimevim/core/ime/RimeImeProvider.kt` - Rime 实现
- `core/src/main/kotlin/com/rimevim/core/ime/StateWatcher.kt` - 文件状态监听
- `core/src/main/kotlin/com/rimevim/core/ime/ImeStateDetector.kt` - IME 状态检测
- `core/src/main/kotlin/com/rimevim/core/ime/CapsLockController.kt` - CapsLock 控制
- `core/src/main/kotlin/com/rimevim/core/ime/WeaselPathDetector.kt` - 路径检测
- `core/src/main/kotlin/com/rimevim/core/rules/RuleEvaluator.kt` - 正则规则评估
- `core/src/main/kotlin/com/rimevim/core/util/Logger.kt` - 日志接口
- `intellij/build.gradle.kts` - IntelliJ 插件构建配置
- `intellij/src/main/kotlin/com/rimevim/adapter/IntelliJLogger.kt` - Logger 实现
- `intellij/src/main/resources/META-INF/plugin.xml` - 插件描述符

### 修改文件
- `settings.gradle.kts` - 添加 core 和 intellij 子项目
- `gradle.properties` - 保持版本号
- `lua/rimevim_bridge.lua` - 更新状态文件名

### 删除文件（迁移后）
- `src/main/kotlin/com/rimevim/ime/RimeController.kt` → 迁移到 core
- `src/main/kotlin/com/rimevim/ime/RimeStateFileWatcher.kt` → 迁移到 core
- `src/main/kotlin/com/rimevim/ime/CapsLockController.kt` → 迁移到 core
- `src/main/kotlin/com/rimevim/ime/WeaselPathDetector.kt` → 迁移到 core
- `src/main/kotlin/com/rimevim/ime/ImeStateDetector.kt` → 迁移到 core
- `src/main/kotlin/com/rimevim/ImeAction.kt` → 迁移到 core
- `src/main/kotlin/com/rimevim/util/VimModeChecker.kt` → 保留在 intellij

---

## 阶段 1：核心库提取

### Task 1: 创建 core 模块骨架

**Files:**
- Create: `core/build.gradle.kts`
- Create: `core/src/main/kotlin/com/rimevim/core/util/Logger.kt`
- Create: `core/src/main/kotlin/com/rimevim/core/ImeAction.kt`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: 创建 core/build.gradle.kts**

```kotlin
// core/build.gradle.kts
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
}

group = "com.rimevim"
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    // JNA 用于 Windows API 调用
    compileOnly("net.java.dev.jna:jna:5.14.0")
    compileOnly("net.java.dev.jna:jna-platform:5.14.0")
    
    // 测试依赖
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
```

- [ ] **Step 2: 创建 Logger 接口**

```kotlin
// core/src/main/kotlin/com/rimevim/core/util/Logger.kt
package com.rimevim.core.util

/**
 * 平台无关的日志接口
 * 各平台实现此接口并注入到核心库
 */
interface Logger {
    fun info(msg: String)
    fun warn(msg: String, e: Throwable? = null)
    fun debug(msg: String)
    fun error(msg: String, e: Throwable? = null)
}
```

- [ ] **Step 3: 迁移 ImeAction 枚举**

```kotlin
// core/src/main/kotlin/com/rimevim/core/ImeAction.kt
package com.rimevim.core

/**
 * 输入法动作枚举
 * 用于规则评估结果
 */
enum class ImeAction {
    CHINESE,    // 切换到中文模式
    CAPS,       // 切换到大写模式
    ENGLISH,    // 切换到英文模式
    UNCHANGED   // 保持当前状态
}
```

- [ ] **Step 4: 更新 settings.gradle.kts**

```kotlin
// settings.gradle.kts
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "RimeVimIME"
include("core")
include("intellij")
```

- [ ] **Step 5: 验证 core 模块编译**

```bash
$env:JAVA_HOME="D:\Program Files\Java\java-21"; .\gradlew.bat :core:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add core/build.gradle.kts core/src/main/kotlin/com/rimevim/core/ImeAction.kt core/src/main/kotlin/com/rimevim/core/util/Logger.kt settings.gradle.kts
git commit -m "feat(core): 创建核心库模块骨架"
```

---

### Task 2: 创建核心接口和常量

**Files:**
- Create: `core/src/main/kotlin/com/rimevim/core/ImeProvider.kt`
- Create: `core/src/main/kotlin/com/rimevim/core/ImeType.kt`
- Create: `core/src/main/kotlin/com/rimevim/core/ImeConfig.kt`
- Create: `core/src/main/kotlin/com/rimevim/core/ImeConstants.kt`
- Create: `core/src/main/kotlin/com/rimevim/core/ImeException.kt`
- Create: `core/src/main/kotlin/com/rimevim/core/ImeProviderFactory.kt`

- [ ] **Step 1: 创建 ImeProvider 接口**

```kotlin
// core/src/main/kotlin/com/rimevim/core/ImeProvider.kt
package com.rimevim.core

/**
 * 平台无关的输入法提供者接口
 * 各输入法实现此接口以支持 IME 切换
 */
interface ImeProvider {
    /** 输入法名称，用于日志和调试 */
    val name: String
    
    /** 切换中英文模式 */
    suspend fun setAsciiMode(ascii: Boolean)
    
    /** 切换大写模式 */
    suspend fun setCapsMode()
    
    /** 是否正在输入（显示候选词窗口） */
    suspend fun isComposing(): Boolean
    
    /** 获取当前跟踪的 IME 状态 */
    fun getTrackedState(): ImeState
    
    /** 同步内部跟踪状态（不触发实际切换） */
    fun syncTrackedState(ascii: Boolean, caps: Boolean)
    
    /** 释放资源 */
    fun dispose()
}

/**
 * IME 状态数据类
 */
data class ImeState(
    val isAsciiMode: Boolean,
    val isCapsLock: Boolean
)
```

- [ ] **Step 2: 创建 ImeType 枚举**

```kotlin
// core/src/main/kotlin/com/rimevim/core/ImeType.kt
package com.rimevim.core

/**
 * 支持的输入法类型
 */
enum class ImeType {
    RIME,          // 小狼毫 Rime/Weasel
    SOGOU,         // 搜狗输入法
    MS_PINYIN,     // 微软拼音
    CUSTOM         // 自定义（通过外部脚本）
}
```

- [ ] **Step 3: 创建 ImeConstants 常量**

```kotlin
// core/src/main/kotlin/com/rimevim/core/ImeConstants.kt
package com.rimevim.core

import java.io.File

/**
 * IME 相关常量
 */
object ImeConstants {
    /** 状态文件目录 */
    const val STATE_FILE_DIR = "%TEMP%"
    
    /**
     * 获取输入法状态文件名
     * 每个输入法独立文件，避免混淆
     */
    fun getStateFileName(type: ImeType): String = when (type) {
        ImeType.RIME -> "ime-state-rime.json"
        ImeType.SOGOU -> "ime-state-sogou.json"
        ImeType.MS_PINYIN -> "ime-state-mspinyin.json"
        ImeType.CUSTOM -> "ime-state-custom.json"
    }
    
    /**
     * 获取输入法状态文件完整路径
     */
    fun getStateFilePath(type: ImeType): String =
        System.getenv("TEMP") + File.separator + getStateFileName(type)
}
```

- [ ] **Step 4: 创建 ImeConfig 配置**

```kotlin
// core/src/main/kotlin/com/rimevim/core/ImeConfig.kt
package com.rimevim.core

/**
 * 输入法配置
 */
data class ImeConfig(
    val type: ImeType = ImeType.RIME,
    val weaselServerPath: String? = null,
    val customSwitchScript: String? = null,
)
```

- [ ] **Step 5: 创建 ImeException 异常**

```kotlin
// core/src/main/kotlin/com/rimevim/core/ImeException.kt
package com.rimevim.core

/**
 * IME 相关异常基类
 */
sealed class ImeException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ProviderNotFound(type: ImeType) : ImeException("IME provider not found for type: $type")
    class InitializationFailed(provider: String, cause: Throwable) :
        ImeException("Failed to initialize IME provider: $provider", cause)
    class SwitchFailed(provider: String, action: String, cause: Throwable) :
        ImeException("Failed to switch IME ($provider): $action", cause)
    class StateFileError(path: String, cause: Throwable) :
        ImeException("Failed to read/write state file: $path", cause)
}
```

- [ ] **Step 6: 创建 ImeProviderFactory 工厂**

```kotlin
// core/src/main/kotlin/com/rimevim/core/ImeProviderFactory.kt
package com.rimevim.core

import com.rimevim.core.ime.RimeImeProvider
import com.rimevim.core.util.Logger

/**
 * IME Provider 工厂
 * 支持注册和创建不同类型的输入法提供者
 */
object ImeProviderFactory {
    private val providers = mutableMapOf<ImeType, (ImeConfig, Logger) -> ImeProvider>()
    
    init {
        // 注册内置 Provider
        register(ImeType.RIME) { config, logger -> RimeImeProvider(config, logger) }
    }
    
    /**
     * 注册自定义 Provider
     */
    fun register(type: ImeType, factory: (ImeConfig, Logger) -> ImeProvider) {
        providers[type] = factory
    }
    
    /**
     * 创建 Provider 实例
     */
    fun createProvider(config: ImeConfig, logger: Logger): ImeProvider {
        val factory = providers[config.type]
            ?: throw ImeException.ProviderNotFound(config.type)
        return factory(config, logger)
    }
}
```

- [ ] **Step 7: 验证编译**

```bash
$env:JAVA_HOME="D:\Program Files\Java\java-21"; .\gradlew.bat :core:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add core/src/main/kotlin/com/rimevim/core/ImeProvider.kt core/src/main/kotlin/com/rimevim/core/ImeType.kt core/src/main/kotlin/com/rimevim/core/ImeConfig.kt core/src/main/kotlin/com/rimevim/core/ImeConstants.kt core/src/main/kotlin/com/rimevim/core/ImeException.kt core/src/main/kotlin/com/rimevim/core/ImeProviderFactory.kt
git commit -m "feat(core): 添加核心接口、常量、异常和工厂类"
```

---

### Task 3: 迁移 IME 核心逻辑到 core

**Files:**
- Create: `core/src/main/kotlin/com/rimevim/core/ime/RimeImeProvider.kt`
- Create: `core/src/main/kotlin/com/rimevim/core/ime/StateWatcher.kt`
- Create: `core/src/main/kotlin/com/rimevim/core/ime/ImeStateDetector.kt`
- Create: `core/src/main/kotlin/com/rimevim/core/ime/CapsLockController.kt`
- Create: `core/src/main/kotlin/com/rimevim/core/ime/WeaselPathDetector.kt`

- [ ] **Step 1: 迁移 CapsLockController（无依赖，最简单）**

```kotlin
// core/src/main/kotlin/com/rimevim/core/ime/CapsLockController.kt
package com.rimevim.core.ime

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser

/**
 * Windows CapsLock 控制
 * 使用 SendInput API 模拟 CapsLock 按键
 */
object CapsLockController {
    
    private interface User32 : Library {
        fun SendInput(
            nInputs: Int,
            pInputs: Array<WinUser.INPUT>?,
            cbSize: Int
        ): Int
        
        companion object {
            val INSTANCE: User32 = Native.load("user32", User32::class.java)
        }
    }
    
    /**
     * 切换 CapsLock 状态
     * @return 成功发送的事件数（2 表示成功）
     */
    fun toggleCapsLock(): Int {
        val input = WinUser.INPUT()
        input.type = WinUser.INPUT.INPUT_KEYBOARD
        
        val keyDown = WinUser.KEYBDINPUT()
        keyDown.wVk = WinDef.WORD(0x14.toShort()) // VK_CAPITAL
        keyDown.dwExtraInfo = WinDef.ULONG_PTR(0)
        keyDown.time = 0
        keyDown.dwFlags = WinDef.DWORD(0)
        input.input = keyDown
        
        val inputs = arrayOf(input)
        
        // KEY DOWN
        val downResult = User32.INSTANCE.SendInput(1, inputs, input.size())
        
        // KEY UP
        keyDown.dwFlags = WinDef.DWORD(2) // KEYEVENTF_KEYUP
        val upResult = User32.INSTANCE.SendInput(1, inputs, input.size())
        
        return downResult + upResult
    }
}
```

- [ ] **Step 2: 迁移 WeaselPathDetector**

```kotlin
// core/src/main/kotlin/com/rimevim/core/ime/WeaselPathDetector.kt
package com.rimevim.core.ime

import com.rimevim.core.util.Logger
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

/**
 * WeaselServer.exe 路径检测
 */
object WeaselPathDetector {
    
    private val COMMON_PATHS = listOf(
        "D:\\Program Files\\Rime\\weasel-0.17.4\\WeaselServer.exe",
        "C:\\Program Files\\Rime\\weasel\\WeaselServer.exe",
        "C:\\Program Files (x86)\\Rime\\weasel\\WeaselServer.exe"
    )
    
    /**
     * 检测 WeaselServer.exe 路径
     * @return 路径或 null
     */
    fun detect(logger: Logger? = null): String? {
        // 1. 注册表检测
        try {
            val regPath = Advapi32Util.registryGetStringValue(
                WinReg.HKEY_CURRENT_USER,
                "Software\\Rime\\Weasel",
                "InstallDir"
            )
            if (regPath != null) {
                val exePath = File(regPath, "WeaselServer.exe")
                if (exePath.exists()) {
                    return exePath.absolutePath
                }
            }
        } catch (e: Exception) {
            logger?.debug("Registry detection failed: ${e.message}")
        }
        
        // 2. 常见路径扫描
        for (path in COMMON_PATHS) {
            if (File(path).exists()) {
                return path
            }
        }
        
        return null
    }
}
```

- [ ] **Step 3: 迁移 StateWatcher**

```kotlin
// core/src/main/kotlin/com/rimevim/core/ime/StateWatcher.kt
package com.rimevim.core.ime

import com.rimevim.core.ImeState
import com.rimevim.core.util.Logger
import java.io.File
import java.nio.file.*

/**
 * IME 状态文件监听器
 * 监听 Rime Lua 脚本写入的状态文件变化
 */
class StateWatcher(
    private val stateFilePath: String,
    private val logger: Logger,
    private val onStateChanged: (ImeState) -> Unit
) {
    private var watcherThread: Thread? = null
    private var isRunning = false
    
    @Volatile
    var isComposing: Boolean = false
        private set
    
    @Volatile
    var lastAsciiMode: Boolean = true
        private set
    
    @Volatile
    var lastCapsLock: Boolean = false
        private set
    
    /**
     * 启动监听
     */
    fun start() {
        if (isRunning) return
        isRunning = true
        
        watcherThread = Thread({
            watchLoop()
        }, "RimeVim-StateWatcher")
        watcherThread?.isDaemon = true
        watcherThread?.start()
    }
    
    /**
     * 停止监听
     */
    fun stop() {
        isRunning = false
        watcherThread?.interrupt()
        watcherThread = null
    }
    
    private fun watchLoop() {
        val stateFile = File(stateFilePath)
        val parentDir = stateFile.parentFile ?: return
        
        try {
            val watchService = FileSystems.getDefault().newWatchService()
            val path = Paths.get(parentDir.absolutePath)
            path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)
            
            while (isRunning && !Thread.interrupted()) {
                val key = watchService.take()
                for (event in key.pollEvents()) {
                    val fileName = event.context().toString()
                    if (fileName.contains("ime-state")) {
                        readAndApplyState(stateFile)
                    }
                }
                key.reset()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            logger.error("State watcher error: ${e.message}", e)
        }
    }
    
    private fun readAndApplyState(stateFile: File) {
        try {
            if (!stateFile.exists()) return
            
            val content = stateFile.readText(Charsets.UTF_8).trim()
            if (content.isEmpty()) return
            
            val state = parseStateJson(content) ?: return
            
            if (state.isAsciiMode != lastAsciiMode || state.isCapsLock != lastCapsLock || state.isComposing != isComposing) {
                lastAsciiMode = state.isAsciiMode
                lastCapsLock = state.isCapsLock
                isComposing = state.isComposing
                
                logger.debug("State file change: ascii=${state.isAsciiMode}, caps=${state.isCapsLock}, composing=${state.isComposing}")
                onStateChanged(state)
            }
        } catch (e: Exception) {
            logger.debug("Failed to parse state file: ${e.message}")
        }
    }
    
    private fun parseStateJson(content: String): ImeState? {
        return try {
            val asciiMode = extractBoolean(content, "ascii_mode") ?: return null
            val capsLock = extractBoolean(content, "caps_lock") ?: return null
            val isComposing = extractBoolean(content, "is_composing") ?: false
            ImeState(asciiMode, capsLock).also {
                this.isComposing = isComposing
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractBoolean(json: String, key: String): Boolean? {
        val regex = """"$key"\s*:\s*(true|false)""".toRegex()
        val match = regex.find(json) ?: return null
        return match.groupValues[1].toBoolean()
    }
}
```

- [ ] **Step 4: 创建 RimeImeProvider**

```kotlin
// core/src/main/kotlin/com/rimevim/core/ime/RimeImeProvider.kt
package com.rimevim.core.ime

import com.rimevim.core.*
import com.rimevim.core.util.Logger
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Rime/Weasel 输入法提供者
 */
class RimeImeProvider(
    private val config: ImeConfig,
    private val logger: Logger
) : ImeProvider {
    
    override val name: String = "Rime/Weasel"
    
    @Volatile
    private var currentAsciiMode: Boolean = true
    
    @Volatile
    private var currentCapsMode: Boolean = false
    
    private val stateWatcher: StateWatcher
    private val weaselServerPath: String?
    
    @Volatile
    private var isForcingImeSwitch: Boolean = false
    
    init {
        weaselServerPath = config.weaselServerPath ?: WeaselPathDetector.detect(logger)
        stateWatcher = StateWatcher(
            stateFilePath = ImeConstants.getStateFilePath(ImeType.RIME),
            logger = logger,
            onStateChanged = { state -> onImeStateChanged(state) }
        )
    }
    
    /**
     * 启动状态监听
     */
    fun start() {
        stateWatcher.start()
    }
    
    /**
     * 停止状态监听
     */
    fun stop() {
        stateWatcher.stop()
    }
    
    override suspend fun setAsciiMode(ascii: Boolean) {
        // 如果已是目标模式且非大写，跳过
        if (currentAsciiMode == ascii && !currentCapsMode) {
            return
        }
        
        // 如果当前是大写模式且切换到英文，先退出大写
        if (currentCapsMode && ascii) {
            logger.debug("Exiting caps mode before switching to ASCII")
            exitCapsMode()
        }
        
        currentAsciiMode = ascii
        switchImeMode(if (ascii) "/ascii" else "/nascii", if (ascii) "ASCII" else "Chinese")
    }
    
    override suspend fun setCapsMode() {
        if (currentCapsMode) {
            logger.debug("IME already in Caps mode, skipping")
            return
        }
        
        val eventsSent = CapsLockController.toggleCapsLock()
        if (eventsSent == 2) {
            currentCapsMode = true
            currentAsciiMode = false
            logger.info("Caps mode activated via SendInput ($eventsSent events)")
        } else {
            logger.warn("Failed to toggle CapsLock via SendInput (sent $eventsSent events)")
        }
    }
    
    private fun exitCapsMode() {
        if (!currentCapsMode) return
        
        val eventsSent = CapsLockController.toggleCapsLock()
        if (eventsSent == 2) {
            currentCapsMode = false
            logger.info("Exited Caps mode via SendInput ($eventsSent events)")
        }
    }
    
    override suspend fun isComposing(): Boolean {
        return stateWatcher.isComposing
    }
    
    override fun getTrackedState(): ImeState {
        return ImeState(currentAsciiMode, currentCapsMode)
    }
    
    override fun syncTrackedState(ascii: Boolean, caps: Boolean) {
        currentAsciiMode = ascii
        currentCapsMode = caps
        logger.debug("Synced tracked state: ascii=$ascii, caps=$caps")
    }
    
    override fun dispose() {
        stateWatcher.stop()
    }
    
    private fun onImeStateChanged(state: ImeState) {
        syncTrackedState(state.isAsciiMode, state.isCapsLock)
    }
    
    private fun switchImeMode(arg: String, label: String) {
        val path = weaselServerPath
        if (path == null) {
            logger.warn("WeaselServer.exe not found")
            return
        }
        
        if (!File(path).exists()) {
            logger.warn("WeaselServer.exe not exists at: $path")
            return
        }
        
        try {
            logger.debug("Executing: $path $arg")
            val process = ProcessBuilder(path, arg)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val exited = process.waitFor(1, TimeUnit.SECONDS)
            if (exited) {
                logger.info("Switched to $label mode (exitCode=${process.exitValue()})")
            } else {
                logger.warn("Switch to $label mode timed out")
                process.destroy()
            }
        } catch (e: Exception) {
            logger.warn("Failed to switch IME mode: $arg", e)
        }
    }
}
```

- [ ] **Step 5: 迁移 ImeStateDetector**

```kotlin
// core/src/main/kotlin/com/rimevim/core/ime/ImeStateDetector.kt
package com.rimevim.core.ime

import com.rimevim.core.ImeState
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary

/**
 * IME 状态检测器
 * 优先使用状态文件，回退 JNA IMM32 API
 */
object ImeStateDetector {
    
    private const val WM_IME_CONTROL = 0x0283
    private const val IMC_GETOPENSTATUS = 0x0005
    
    private interface MyUser32 : StdCallLibrary {
        fun GetForegroundWindow(): WinUser.HWND?
        fun SendMessageW(hWnd: WinUser.HWND?, Msg: Int, wParam: WPARAM, lParam: LPARAM): LPARAM
        
        companion object {
            val INSTANCE: MyUser32? by lazy {
                try {
                    Native.load("user32", MyUser32::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    
    private interface Imm32 : Library {
        fun ImmGetDefaultIMEWnd(hwnd: WinUser.HWND?): Pointer?
        
        companion object {
            val INSTANCE: Imm32? by lazy {
                try {
                    Native.load("imm32", Imm32::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    
    /**
     * 检测是否正在输入
     */
    fun isComposing(stateWatcher: StateWatcher): Boolean {
        // 优先使用状态文件
        if (stateWatcher.isComposing) {
            return true
        }
        
        // 回退 JNA
        return try {
            val user32 = MyUser32.INSTANCE ?: return false
            val imm32 = Imm32.INSTANCE ?: return false
            
            val fgWindow = user32.GetForegroundWindow()
            val imeWnd = imm32.ImmGetDefaultIMEWnd(fgWindow)
            if (imeWnd == null || Pointer.nativeValue(imeWnd.pointer) == 0L) return false
            
            val result = user32.SendMessageW(imeWnd, WM_IME_CONTROL, WPARAM(IMC_GETOPENSTATUS.toLong()), LPARAM(0L))
            result.toLong() != 0L
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取当前 IME 状态
     */
    fun getCurrentState(stateWatcher: StateWatcher): ImeState {
        return ImeState(
            isAsciiMode = !stateWatcher.isComposing,
            isCapsLock = stateWatcher.lastCapsLock
        )
    }
}
```

- [ ] **Step 6: 验证编译**

```bash
$env:JAVA_HOME="D:\Program Files\Java\java-21"; .\gradlew.bat :core:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add core/src/main/kotlin/com/rimevim/core/ime/
git commit -m "feat(core): 迁移 IME 核心逻辑到核心库"
```

---

### Task 4: 创建 RuleEvaluator 规则评估器

**Files:**
- Create: `core/src/main/kotlin/com/rimevim/core/rules/RuleEvaluator.kt`

- [ ] **Step 1: 创建 RuleEvaluator**

```kotlin
// core/src/main/kotlin/com/rimevim/core/rules/RuleEvaluator.kt
package com.rimevim.core.rules

import com.rimevim.core.ImeAction
import java.util.regex.Pattern

/**
 * 正则规则评估器
 * 评估光标前后文本，决定输入法动作
 */
object RuleEvaluator {
    
    private val patternCache = mutableMapOf<String, Pattern>()
    
    /**
     * 评估 Insert 模式下的输入法动作
     * @param before 光标前文本
     * @param after 光标后文本
     * @param chineseBeforeRegex 中文规则（光标前）
     * @param chineseAfterRegex 中文规则（光标后）
     * @param capsBeforeRegex 大写规则（光标前）
     * @param capsAfterRegex 大写规则（光标后）
     * @return 输入法动作
     */
    fun evaluate(
        before: String,
        after: String,
        chineseBeforeRegex: String,
        chineseAfterRegex: String,
        capsBeforeRegex: String,
        capsAfterRegex: String
    ): ImeAction {
        // 1. 检查中文规则：前后任一匹配
        if (matchesRegex(chineseBeforeRegex, before) || matchesRegex(chineseAfterRegex, after)) {
            return ImeAction.CHINESE
        }
        
        // 2. 检查大写规则：前后任一匹配
        if (matchesRegex(capsBeforeRegex, before) || matchesRegex(capsAfterRegex, after)) {
            return ImeAction.CAPS
        }
        
        // 3. 默认英文
        return ImeAction.ENGLISH
    }
    
    /**
     * 检查正则是否匹配（空规则视为匹配）
     */
    private fun matchesRegex(pattern: String, text: String): Boolean {
        if (pattern.isBlank()) return true
        return try {
            val compiled = patternCache.getOrPut(pattern) { Pattern.compile(pattern) }
            compiled.matcher(text).find()
        } catch (e: Exception) {
            false
        }
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
$env:JAVA_HOME="D:\Program Files\Java\java-21"; .\gradlew.bat :core:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/kotlin/com/rimevim/core/rules/RuleEvaluator.kt
git commit -m "feat(core): 添加正则规则评估器"
```

---

## 阶段 2：IntelliJ 插件重构

### Task 5: 创建 intellij 模块

**Files:**
- Create: `intellij/build.gradle.kts`
- Create: `intellij/src/main/resources/META-INF/plugin.xml`
- Create: `intellij/src/main/kotlin/com/rimevim/adapter/IntelliJLogger.kt`
- Modify: `settings.gradle.kts` (already done in Task 1)

- [ ] **Step 1: 创建 intellij/build.gradle.kts**

```kotlin
// intellij/build.gradle.kts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.8.0"
}

group = "com.rimevim"
version = rootProject.version

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":core"))
    
    intellijPlatform {
        phpstorm("2026.1")
        plugins("IdeaVIM:2.35.2")
        pluginVerifier()
        zipSigner()
    }
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        version.set("${project.version}")
        ideaVersion {
            sinceBuild.set("261")
            untilBuild.set("261.*")
        }
    }
}

tasks {
    compileKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    compileJava {
        options.release.set(17)
    }
    test {
        useJUnitPlatform()
    }
}
```

- [ ] **Step 2: 创建 plugin.xml**

```xml
<!-- intellij/src/main/resources/META-INF/plugin.xml -->
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
    <depends optional="true" config-file="ideavim-integration.xml">IdeaVIM</depends>

    <extensions defaultExtensionNs="com.intellij">
        <applicationConfigurable
                parentId="tools"
                instance="com.rimevim.settings.RimeVimSettingsConfigurable"
                id="com.rimevim.settings.RimeVimSettingsConfigurable"
                displayName="RimeVim IME"/>
        <applicationService
                serviceImplementation="com.rimevim.settings.RimeVimSettings"/>
        <postStartupActivity implementation="com.rimevim.RimeVimPlugin"/>
    </extensions>

    <applicationListeners>
        <listener class="com.rimevim.listener.VimModeListener"
                  topic="com.intellij.openapi.editor.EditorFactoryListener"/>
    </applicationListeners>
</idea-plugin>
```

- [ ] **Step 3: 创建 IntelliJLogger**

```kotlin
// intellij/src/main/kotlin/com/rimevim/adapter/IntelliJLogger.kt
package com.rimevim.adapter

import com.rimevim.core.util.Logger
import com.intellij.openapi.diagnostic.thisLogger

/**
 * IntelliJ 平台日志实现
 */
object IntelliJLogger : Logger {
    override fun info(msg: String) {
        thisLogger().info("[RimeVim] $msg")
    }
    
    override fun warn(msg: String, e: Throwable?) {
        if (e != null) {
            thisLogger().warn("[RimeVim] $msg", e)
        } else {
            thisLogger().warn("[RimeVim] $msg")
        }
    }
    
    override fun debug(msg: String) {
        thisLogger().debug("[RimeVim] $msg")
    }
    
    override fun error(msg: String, e: Throwable?) {
        if (e != null) {
            thisLogger().error("[RimeVim] $msg", e)
        } else {
            thisLogger().error("[RimeVim] $msg")
        }
    }
}
```

- [ ] **Step 4: 验证编译**

```bash
$env:JAVA_HOME="D:\Program Files\Java\java-21"; .\gradlew.bat :intellij:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add intellij/build.gradle.kts intellij/src/main/resources/META-INF/plugin.xml intellij/src/main/kotlin/com/rimevim/adapter/IntelliJLogger.kt
git commit -m "feat(intellij): 创建 IntelliJ 插件模块骨架"
```

---

### Task 6: 迁移 IntelliJ 插件代码

**Files:**
- Move: `src/` → `intellij/src/`
- Modify: 所有导入路径更新

- [ ] **Step 1: 移动现有代码到 intellij 模块**

```bash
# 移动源代码
Move-Item -Path "src\main\kotlin\com\rimevim\*" -Destination "intellij\src\main\kotlin\com\rimevim\" -Force
Move-Item -Path "src\main\resources\*" -Destination "intellij\src\main\resources\" -Force

# 删除旧 src 目录
Remove-Item -Path "src" -Recurse -Force
```

- [ ] **Step 2: 更新 RimeVimPlugin.kt 导入**

```kotlin
// intellij/src/main/kotlin/com/rimevim/RimeVimPlugin.kt
// 更新导入路径
import com.rimevim.core.*
import com.rimevim.core.ime.*
import com.rimevim.core.rules.RuleEvaluator
import com.rimevim.adapter.IntelliJLogger
import com.rimevim.util.VimModeChecker
```

- [ ] **Step 3: 更新 RimeVimExtension.kt 导入**

```kotlin
// intellij/src/main/kotlin/com/rimevim/RimeVimExtension.kt
// 更新导入路径
import com.rimevim.core.*
import com.rimevim.core.ime.*
import com.rimevim.core.rules.RuleEvaluator
import com.rimevim.adapter.IntelliJLogger
```

- [ ] **Step 4: 更新 VimModeListener.kt 导入**

```kotlin
// intellij/src/main/kotlin/com/rimevim/listener/VimModeListener.kt
// 更新导入路径
import com.rimevim.core.*
import com.rimevim.core.ime.*
import com.rimevim.core.rules.RuleEvaluator
import com.rimevim.adapter.IntelliJLogger
import com.rimevim.util.VimModeChecker
```

- [ ] **Step 5: 更新 RimeStateFileWatcher.kt（如存在）**

```kotlin
// intellij/src/main/kotlin/com/rimevim/ime/RimeStateFileWatcher.kt
// 更新导入路径
import com.rimevim.core.*
import com.rimevim.core.ime.*
import com.rimevim.adapter.IntelliJLogger
```

- [ ] **Step 6: 验证编译**

```bash
$env:JAVA_HOME="D:\Program Files\Java\java-21"; .\gradlew.bat :intellij:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add intellij/src/
git commit -m "refactor(intellij): 迁移现有代码到 intellij 模块"
```

---

### Task 7: 更新 Lua 脚本状态文件名

**Files:**
- Modify: `lua/rimevim_bridge.lua`

- [ ] **Step 1: 更新状态文件名**

```lua
-- lua/rimevim_bridge.lua
-- 更新状态文件名
local state_file = os.getenv("TEMP") .. "\\ime-state-rime.json"
```

- [ ] **Step 2: Commit**

```bash
git add lua/rimevim_bridge.lua
git commit -m "chore(lua): 更新状态文件名为 ime-state-rime.json"
```

---

### Task 8: 构建和验证

- [ ] **Step 1: 完整构建**

```bash
$env:JAVA_HOME="D:\Program Files\Java\java-21"; .\gradlew.bat clean :intellij:buildPlugin -x buildSearchableOptions
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 验证产物**

```bash
Get-ChildItem -Path "intellij\build\distributions\RimeVimIME-*.zip" | Select-Object Name, Length
```

Expected: RimeVimIME-1.1.0.zip exists, ~77 KB

- [ ] **Step 3: Commit**

```bash
git add .
git commit -m "build: 验证多编辑器架构构建成功"
```

---

## 阶段 3：VSCode 扩展（后续计划）

VSCode 扩展需要独立的 TypeScript 项目，将在核心库和 IntelliJ 插件完成后单独实施。

---

## 自审检查

1. **规格覆盖**：所有设计文档中的核心接口、常量、异常、工厂、RimeImeProvider、StateWatcher、RuleEvaluator 均已覆盖
2. **占位符扫描**：无 "TBD"、"TODO" 或不完整步骤
3. **类型一致性**：ImeProvider、ImeState、ImeType、ImeConfig、ImeConstants 在所有任务中一致
4. **无歧义**：所有代码步骤包含完整实现
