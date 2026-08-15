# AutoSwitchIME - 主索引

IntelliJ/VSCode 插件：Vim 模式切换时自动切换输入法中英文状态，并用光标颜色指示。

**当前版本**: v3.2.6

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
| 输入法控制 | [`agents/ime-control.md`](agents/ime-control.md) | ImeGateway、系统 Provider、输入法 Provider |
| UI/光标 | [`agents/ui-caret.md`](agents/ui-caret.md) | 光标颜色管理（IntelliJ+VSCode） |
| 设置面板 | [`agents/settings.md`](agents/settings.md) | 配置项、正则规则（IntelliJ+VSCode） |
| 已知问题 | [`agents/issues.md`](agents/issues.md) | 版本日志、待完成、已知 bug |
| 架构设计 | Obsidian `技术/自研规划/AutoSwitchIME/项目规划/输入法接管/流程设计.md` | 输入法接管、Coordinator、多编辑器调度与项目结构；关联设计统一归档到同一功能分组 |

## 快速参考

- 操作命令详见 [`GUIDE.md`](GUIDE.md)
- **JDK 21** / **Kotlin 2.3.0** / **Gradle 8.10**
- **IntelliJ Platform 2.8.0** / **IdeaVim 2.35.2**
- **目标 IDE**: PhpStorm 2026.1 (build 261.*)
- **Rust**: stable + `x86_64-pc-windows-gnu`

## 开发约束

- 输入状态真相源必须来自焦点校验后的输入法级可选能力或系统级默认能力，不信任状态文件；Rime 通过 Lua 命名共享内存提供状态，Windows 默认实现读取系统 IME 与物理 CapsLock
- VSCode 与 IntelliJ 行为一致，操作系统差异和输入法差异分别在两级 Provider 隔离
- 整体架构固定分为输入监控、输入切换处理、光标颜色处理三部分；三者只通过 `ImeState` 和 Coordinator 事件协作，不得互相读取平台实现细节
- `ImeGateway` 按单项能力合并两级 Provider：输入法级已实现则优先使用，未实现才使用系统级；专用切换执行失败不得静默降级
- 已声明的输入法级状态源不可用时必须暂停插件动作和光标更新；只有未实现专用状态源或可用快照缺少单项字段时才能使用系统级能力
- 操作系统通过 `SystemImeProvider` 和 Registry 扩展，输入法通过 `ImeProvider` 和 Registry 按需实现；未实现的输入法不得出现在可选列表
- 光标颜色只绑定当前实际 `ImeState`，不得附加 Vim 模式、规则动作、焦点或其他显示条件
- 动作目标、命令成功结果和临时确认状态不得写入 `ImeState`；光标只能在输入法或系统状态源回读实际值后更新
- 所有原生 Win32 调用集中在 `ime-sys/`

## 版本与打包

- 项目不保留旧版本安装包；每次打包前必须清理 `packages/` 中已有的 IntelliJ ZIP、VSCode VSIX 和 Rime ZIP，只保留本次版本生成的三个产物。
- 每次打包前必须先根据本次改动判断并更新版本号，不能复用旧版本号打包。
- 语义版本判断：修复或行为修正用 patch，新增向后兼容功能用 minor，破坏性变更用 major。
- 版本源必须同步更新：本文件当前版本、`gradle.properties` 的 `pluginVersion`、`vscode/package.json` 的 `version`、`vscode/package-lock.json` 顶部包版本。
- 打包后必须验证产物文件名和插件元数据版本一致，检查 IntelliJ ZIP、VSCode VSIX 与 Rime ZIP 三个产物；完整发布流程见 `agents/env-build.md`。
