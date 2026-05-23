# AutoSwitchIME - 主索引

IntelliJ 插件 + VSCode 扩展：Vim 模式切换时自动切换输入法中英文状态，并用光标颜色指示。

**当前版本**: 1.1.1（多编辑器架构）

## 模块索引

| 模块 | 文件 | 描述 |
|------|------|------|
| 环境构建 | `agents/env-build.md` | JDK、Gradle、Kotlin 版本及构建命令 |
| IdeaVim 集成 | `agents/ideavim-integration.md` | VimExtension、ModeChangeListener、plugin.xml 配置 |
| 输入法控制 | `agents/ime-control.md` | ImeProvider 接口、RimeImeProvider、WeaselServer.exe 调用、IME 状态检测 |
| UI/光标 | `agents/ui-caret.md` | CaretColorManager 光标颜色管理 |
| 设置面板 | `agents/settings.md` | PersistentStateComponent、Configurable 配置持久化 |
| 已知问题 | `agents/issues.md` | 待完成项、已知 bug、弃用警告 |
| 架构设计 | `../docs/superpowers/specs/2026-05-23-rimevim-multi-editor-design.md` | 多编辑器架构设计文档 |

## 快速参考

- **JDK**: 21 (`D:\Program Files\Java\java-21`)
- **Kotlin**: 2.3.0
- **Gradle**: 8.10
- **IntelliJ Platform Gradle Plugin**: 2.8.0
- **目标 IDE**: PhpStorm 2026.1 (build 261.*)
- **IdeaVim**: 2.35.2
- **构建**: `.\gradlew.bat build`
- **运行**: `.\gradlew.bat runIde`

## 版本更新流程

1. 修改 `gradle.properties` 中的 `pluginVersion`
2. `$env:JAVA_HOME="D:\Program Files\Java\java-21"; .\gradlew.bat clean :intellij:buildPlugin -x buildSearchableOptions -x prepareJarSearchableOptions`
3. 验证产物 `build\distributions\AutoSwitchIME-IntelliJ-<version>.zip`
4. 同步 Lua 脚本到 `%APPDATA%\Rime\lua\rimevim_bridge.lua`
5. 更新 `agents/issues.md` 记录变更

## 架构要点

- **多编辑器架构**：核心库 (`core/`) + IntelliJ 插件 (`intellij/`) + VSCode 扩展 (`vscode/`)
- **ImeProvider 接口**：平台无关的 IME 切换接口，支持多种输入法
- **状态文件**：每个输入法独立文件（如 `ime-state-rime.json`）
- **IME 状态检测**：优先使用 Rime Lua 脚本写入的状态文件，回退 JNA IMM32 API
- **Normal 模式强制英文**：检测到手动切换中文时自动强制回英文（带窗口焦点检测）
- **防止递归**：`isForcingImeSwitch` 标志 + `CountDownLatch` 同步
- **光标颜色**：中文=绿色，英文=白色，Caps=黄色
- **正则匹配**：前后任一匹配即切换，不跨行
- **性能优化**：光标移动防抖 50ms、正则缓存、状态早返回
