# RimeVimIME 多编辑器架构设计

> 日期：2026-05-23
> 状态：待实施
> 作者：Sisyphus

## 概述

将 RimeVimIME 从单一 IntelliJ 插件重构为**共享核心库 + 多平台适配**架构，支持 IntelliJ IDEA 和 VSCode 两个编辑器，同时设计为可扩展的输入法框架。

## 架构目标

1. **核心逻辑共享**：IME 切换、状态检测、规则评估等平台无关代码提取为独立模块
2. **多平台支持**：IntelliJ 插件和 VSCode 扩展分别打包，共享核心库
3. **可扩展输入法**：通过 `ImeProvider` 接口支持多种输入法（Rime、搜狗、微软拼音等）
4. **独立状态文件**：每个输入法使用独立的状态文件，避免混淆

## 项目结构

```
RimeVimIME/
├── core/                          # 共享核心库 (Kotlin JVM)
│   ├── src/main/kotlin/com/rimevim/core/
│   │   ├── ImeAction.kt           # 纯枚举
│   │   ├── ImeProvider.kt         # 输入法接口
│   │   ├── ImeProviderFactory.kt  # 工厂类
│   │   ├── ImeType.kt             # 输入法类型枚举
│   │   ├── ImeConfig.kt           # 配置数据类
│   │   ├── ImeConstants.kt        # 常量定义
│   │   ├── ImeException.kt        # 异常定义
│   │   ├── ime/
│   │   │   ├── RimeImeProvider.kt   # Rime 实现
│   │   │   ├── StateWatcher.kt      # 文件状态监听
│   │   │   ├── ImeStateDetector.kt  # IME 状态检测
│   │   │   ├── CapsLockController.kt# CapsLock 控制
│   │   │   └── WeaselPathDetector.kt# 路径检测
│   │   ├── rules/
│   │   │   └── RuleEvaluator.kt     # 正则规则评估
│   │   └── util/
│   │       └── Logger.kt            # 日志接口
│   └── build.gradle.kts
│
├── intellij/                      # IntelliJ 插件
│   ├── src/main/kotlin/com/rimevim/
│   │   ├── RimeVimPlugin.kt
│   │   ├── RimeVimExtension.kt
│   │   ├── VimModeListener.kt
│   │   ├── VimModeChecker.kt
│   │   ├── CaretColorManager.kt
│   │   ├── RimeVimSettings.kt
│   │   └── adapter/
│   │       ├── IntelliJLogger.kt    # Logger 实现
│   │       └── IntelliJImeAdapter.kt# 适配器
│   └── build.gradle.kts
│
├── vscode/                        # VSCode 扩展 (TypeScript)
│   ├── src/
│   │   ├── extension.ts
│   │   ├── core/                    # 核心逻辑 TypeScript 移植
│   │   │   ├── ImeProvider.ts
│   │   │   ├── RimeImeProvider.ts
│   │   │   └── ...
│   │   ├── vim/ModeDetector.ts
│   │   └── caret/ColorManager.ts
│   ├── package.json
│   └── tsconfig.json
│
└── lua/rimevim_bridge.lua         # 共享（Rime 侧脚本）
```

## 核心接口设计

### ImeProvider 接口

```kotlin
interface ImeProvider {
    val name: String
    suspend fun setAsciiMode(ascii: Boolean)
    suspend fun setCapsMode()
    suspend fun isComposing(): Boolean
    fun getTrackedState(): ImeState
    fun syncTrackedState(ascii: Boolean, caps: Boolean)
    fun dispose()
}

data class ImeState(
    val isAsciiMode: Boolean,
    val isCapsLock: Boolean
)
```

### ImeType 枚举

```kotlin
enum class ImeType {
    RIME,          // 小狼毫 Rime/Weasel
    SOGOU,         // 搜狗输入法
    MS_PINYIN,     // 微软拼音
    QQ_PINYIN,     // QQ 拼音
    CUSTOM         // 自定义（通过外部脚本）
}
```

### ImeConstants 常量

```kotlin
object ImeConstants {
    const val STATE_FILE_DIR = "%TEMP%"
    
    fun getStateFileName(type: ImeType): String = when (type) {
        ImeType.RIME -> "ime-state-rime.json"
        ImeType.SOGOU -> "ime-state-sogou.json"
        ImeType.MS_PINYIN -> "ime-state-mspinyin.json"
        ImeType.QQ_PINYIN -> "ime-state-qqpinyin.json"
        ImeType.CUSTOM -> "ime-state-custom.json"
    }
    
    fun getStateFilePath(type: ImeType): String = 
        System.getenv("TEMP") + File.separator + getStateFileName(type)
}
```

### ImeConfig 配置

```kotlin
data class ImeConfig(
    val type: ImeType = ImeType.RIME,
    val weaselServerPath: String? = null,
    val customSwitchScript: String? = null,
)
```

### ImeProviderFactory 工厂

```kotlin
object ImeProviderFactory {
    private val providers = mutableMapOf<ImeType, (ImeConfig) -> ImeProvider>()
    
    init {
        register(ImeType.RIME) { config -> RimeImeProvider(config, DefaultLogger) }
    }
    
    fun register(type: ImeType, factory: (ImeConfig) -> ImeProvider) {
        providers[type] = factory
    }
    
    fun createProvider(config: ImeConfig): ImeProvider {
        val factory = providers[config.type]
            ?: throw ImeException.ProviderNotFound(config.type)
        return factory(config)
    }
}
```

## 平台层适配

### IntelliJ 适配

| 功能              | 实现方式                                      |
| ----------------- | --------------------------------------------- |
| 入口              | `ProjectActivity.execute()`                     |
| 状态监听          | `StateWatcher` (Java NIO WatchService)          |
| 光标颜色          | `CaretVisualAttributes` API                     |
| Vim 模式检测      | `VimPlugin.isEnabled()` + `injector.vimState.mode` |
| 设置读取          | `PersistentStateComponent`                      |
| 日志              | `com.intellij.openapi.diagnostic.Logger`        |
| 线程模型          | EDT + `invokeLater`                             |
| WeaselServer 调用 | `ProcessBuilder` (Java)                         |

### VSCode 适配

| 功能              | 实现方式                                      |
| ----------------- | --------------------------------------------- |
| 入口              | `activate(context)`                             |
| 状态监听          | `fs.watch()` (Node.js)                          |
| 光标颜色          | `TextEditorDecorationType` 或设置修改           |
| Vim 模式检测      | `vscodevim` 扩展 API                            |
| 设置读取          | `vscode.workspace.getConfiguration()`           |
| 日志              | `vscode.window.createOutputChannel()`           |
| 线程模型          | 单线程事件循环                                  |
| WeaselServer 调用 | `child_process.spawn` (Node.js)                 |

## 错误处理

### 异常层次

```kotlin
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

### 降级策略

1. **主 Provider 失败** → 尝试 JNA 直接切换
2. **状态文件读取失败** → 使用内存缓存的最后状态
3. **WeaselServer 不存在** → 提示用户配置路径

## 测试策略

### 核心库测试 (JUnit 5 + MockK)

- `RimeImeProviderTest`：测试状态切换逻辑
- `StateWatcherTest`：测试文件监听和解析
- `RuleEvaluatorTest`：测试正则规则评估

### IntelliJ 集成测试

- `RimeVimPluginTest`：测试插件初始化和模式切换

### VSCode 测试 (Mocha)

- `extension.test.ts`：测试扩展激活和 IME Provider 创建

## 构建和打包

### Gradle 多项目配置

```kotlin
// settings.gradle.kts
rootProject.name = "RimeVimIME"
include("core")
include("intellij")
```

### 打包命令

```bash
# 构建核心库
./gradlew :core:build

# 构建 IntelliJ 插件
./gradlew :intellij:buildPlugin

# 构建 VSCode 扩展
cd vscode && npm run package
```

### 输出产物

| 模块     | 输出产物                                              |
| -------- | ----------------------------------------------------- |
| `core`     | `core/build/libs/rimevim-core-<version>.jar`            |
| `intellij` | `intellij/build/distributions/RimeVimIME-<version>.zip` |
| `vscode`   | `vscode/rimevim-vscode-<version>.vsix`                  |

## Lua 脚本适配

状态文件名更新为 `ime-state-rime.json`，与 `ImeConstants` 保持一致：

```lua
local state_file = os.getenv("TEMP") .. "\\ime-state-rime.json"
```

## 迁移计划

### 阶段 1：核心库提取
1. 创建 `core/` 模块
2. 迁移 `ImeAction`、`ImeProvider` 接口、`ImeProviderFactory`
3. 迁移 `RimeController` → `RimeImeProvider`
4. 迁移 `StateWatcher`、`CapsLockController`、`WeaselPathDetector`
5. 创建 `Logger` 接口

### 阶段 2：IntelliJ 重构
1. 创建 `intellij/` 模块
2. 迁移现有代码，依赖 `core` 模块
3. 实现 `IntelliJLogger`、`IntelliJImeAdapter`
4. 更新设置面板支持 `ImeType` 选择

### 阶段 3：VSCode 扩展
1. 创建 `vscode/` 项目骨架
2. 移植核心逻辑到 TypeScript
3. 实现 VSCodeVim 模式检测
4. 实现光标颜色管理

### 阶段 4：测试和文档
1. 编写核心库单元测试
2. 编写集成测试
3. 更新 README 和文档

## 风险和缓解

| 风险                              | 影响 | 缓解措施                                    |
| --------------------------------- | ---- | ------------------------------------------- |
| Kotlin/TypeScript 逻辑不一致      | 高   | 核心逻辑先用 Kotlin 实现，VSCode 严格对照移植 |
| VSCodeVim API 不稳定              | 中   | 使用官方扩展 API，避免内部实现依赖          |
| 多项目构建复杂度高                | 中   | 使用 Gradle 多项目，保持清晰依赖关系        |
| 状态文件并发读写冲突              | 低   | 每个输入法独立文件，加文件锁保护            |
