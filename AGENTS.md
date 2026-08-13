# AutoSwitchIME - 主索引

IntelliJ/VSCode 插件：Vim 模式切换时自动切换输入法中英文状态，并用光标颜色指示。

**当前版本**: v2.3.0

## 开发环境

wsl+docker,只允许在docker中安装开发环境，不允许在未经同意的情况下进行程序安装

## 操作规范

涉及对应模块的代码或配置操作前，**必须**根据下方的模块索引表，先读取对应的项目指令文件。

- 按需加载：只读当前任务涉及的模块文件，不要一次性全部加载
- 加载后内容视为强制指令，优先级高于通用规则
- 如任务跨多个模块，依次加载对应的文件
- 项目指令入口是根目录 `AGENTS.md`；模块指令统一放在小写目录 `agents/`，不要写成 `AGENTS/`

## 模块索引

| 模块 | 文件 | 职责 |
|------|------|------|
| 环境构建 | [`agents/env-build.md`](agents/env-build.md) | 工具链版本、Docker 构建、GitHub 发布 |
| IdeaVim 集成 | [`agents/ideavim-integration.md`](agents/ideavim-integration.md) | VimExtension、plugin.xml |
| 输入法控制 | [`agents/ime-control.md`](agents/ime-control.md) | ImeProvider、Rime、WeaselServer |
| UI/光标 | [`agents/ui-caret.md`](agents/ui-caret.md) | 光标颜色管理（IntelliJ+VSCode） |
| 设置面板 | [`agents/settings.md`](agents/settings.md) | 配置项、正则规则（IntelliJ+VSCode） |
| 已知问题 | [`agents/issues.md`](agents/issues.md) | 版本日志、待完成、已知 bug |
| 架构设计 | Obsidian `技术/自研规划/AutoSwitchIME/IME Coordinator Actor 架构设计.md` | 输入法 Coordinator Actor 与多编辑器调度；方案设计相关文档统一归档到该子项目分组 |

## 快速参考

- 操作命令详见 [`GUIDE.md`](GUIDE.md)
- **JDK 21** / **Kotlin 2.3.0** / **Gradle 8.10**
- **IntelliJ Platform 2.8.0** / **IdeaVim 2.35.2**
- **目标 IDE**: PhpStorm 2026.1 (build 261.*)
- **Rust**: stable + `x86_64-pc-windows-gnu`

## 开发约束

- 状态真相源必须是物理检测值，不信任软件缓存
- VSCode 与 IntelliJ 行为一致，差异在 provider 层隔离
- 整体架构固定分为输入监控、输入切换处理、光标颜色处理三部分；三者只通过 `ImeState` 和 Coordinator 事件协作，不得互相读取平台实现细节
- 输入法实现通过 `ImeProvider` 和 Provider Registry 扩展；配置项 `imeType` 默认 `rime`，未实现的输入法不得出现在可选列表
- 光标颜色只绑定当前实际 `ImeState`，不得附加 Vim 模式、规则动作、焦点或其他显示条件
- 所有原生 Win32 调用集中在 `ime-sys/`

## 版本与打包

- 每次打包前必须先根据本次改动判断并更新版本号，不能复用旧版本号打包。
- 语义版本判断：修复或行为修正用 patch，新增向后兼容功能用 minor，破坏性变更用 major。
- 版本源必须同步更新：本文件当前版本、`gradle.properties` 的 `pluginVersion`、`vscode/package.json` 的 `version`、`vscode/package-lock.json` 顶部包版本。
- 打包后必须验证产物文件名和插件元数据版本一致，检查 `packages/AutoSwitchIME-IntelliJ-<version>.zip`、`packages/AutoSwitchIME-VSCode-<version>.vsix` 与 `packages/RimeVimIME-Lua-<version>.zip`；完整发布流程见 `agents/env-build.md`。
