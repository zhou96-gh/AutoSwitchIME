# AutoSwitchIME - AGENTS.md

> ⚠️ 详细 agent 配置已迁移到 `.opencode/agents/` 目录，本文档保留核心快速参考。

IntelliJ 插件 + VSCode 扩展：Vim 模式切换时自动切换输入法中英文状态，并用光标颜色指示。

**当前版本**: 1.1.0（多编辑器架构设计中）

## 分模块配置索引

| 模块 | 文件 | 描述 |
|------|------|------|
| 环境构建 | `.opencode/agents/env-build.md` | JDK、Gradle、Kotlin 版本及构建命令 |
| IdeaVim 集成 | `.opencode/agents/ideavim-integration.md` | VimExtension、ModeChangeListener、plugin.xml 配置 |
| 输入法控制 | `.opencode/agents/ime-control.md` | ImeProvider 接口、RimeImeProvider、WeaselServer.exe 调用、IME 状态检测 |
| UI/光标 | `.opencode/agents/ui-caret.md` | CaretColorManager 光标颜色管理 |
| 设置面板 | `.opencode/agents/settings.md` | PersistentStateComponent、Configurable 配置持久化 |
| 已知问题 | `.opencode/agents/issues.md` | 待完成项、已知 bug、弃用警告 |
| 架构设计 | `docs/superpowers/specs/2026-05-23-rimevim-multi-editor-design.md` | 多编辑器架构设计文档 |

## 快速参考

- **JDK**: 21 (`D:\Program Files\Java\java-21`)
- **Kotlin**: 2.3.0
- **Gradle**: 8.10
- **IntelliJ Platform Gradle Plugin**: 2.8.0
- **目标 IDE**: PhpStorm 2026.1 (build 261.*)
- **IdeaVim**: 2.35.2
- **构建**: `.\gradlew.bat build`
- **运行**: `.\gradlew.bat runIde`
- **输出**: `build/distributions/AutoSwitchIME-<version>.zip` (~77 KB)

## 版本更新流程（每次发版必须执行）

1. **修改版本号**：编辑 `gradle.properties` 中的 `pluginVersion`
2. **构建插件包**：`$env:JAVA_HOME="D:\Program Files\Java\java-21"; .\gradlew.bat clean buildPlugin -x buildSearchableOptions`
3. **验证产物**：确认 `build\distributions\AutoSwitchIME-<新版本>.zip` 存在且大小合理
4. **同步 Lua 脚本**：如有修改，复制 `lua\rimevim_bridge.lua` 到 `%APPDATA%\Rime\lua\rimevim_bridge.lua`
5. **记录变更**：在 `.opencode/agents/issues.md` 或提交消息中记录版本变更内容

## 架构要点

### 多编辑器架构（v1.1.0+）

- **核心库** (`core/`)：平台无关的 IME 切换逻辑，通过 `ImeProvider` 接口支持多种输入法
- **IntelliJ 插件** (`intellij/`)：依赖 core 模块，实现 IdeaVim 集成
- **VSCode 扩展** (`vscode/`)：TypeScript 实现，依赖 core 模块（移植）

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
```

### 状态文件

- 每个输入法独立状态文件，避免混淆
- Rime: `%TEMP%\ime-state-rime.json`
- 搜狗: `%TEMP%\ime-state-sogou.json`
- 微软拼音: `%TEMP%\ime-state-mspinyin.json`

### 核心特性

- **IME 状态检测**：优先使用 Rime Lua 脚本写入的状态文件，回退 JNA IMM32 API
- **Normal 模式强制英文**：检测到手动切换中文时自动强制回英文（带窗口焦点检测）
- **防止递归**：`isForcingImeSwitch` 标志 + `CountDownLatch` 同步
- **光标颜色**：中文=绿色，英文=白色，Caps=黄色
- **正则匹配**：前后任一匹配即切换（`||` 逻辑），不跨行
- **性能优化**：光标移动防抖 50ms、正则 Pattern 缓存、`setAsciiMode` 状态早返回
