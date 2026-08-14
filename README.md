# AutoSwitchIME

![Auto Switch IME](resources/icon.png)

面向 Windows + 小狼毫（Rime/Weasel）的 Vim 输入法自动切换插件，同时支持 JetBrains IDE 和 VSCode。

[下载最新版本](https://github.com/zhou96-gh/AutoSwitchIME/releases/latest) | [更新日志](CHANGELOG.md) | [操作指南](GUIDE.md) | [问题反馈](https://github.com/zhou96-gh/AutoSwitchIME/issues)

## 功能

- Normal 模式始终保持小写英文；Visual、Command 等其他 normal-like 模式进入时默认英文，之后允许手动切换。
- Insert 模式根据光标两侧内容自动选择中文、大写或英文。
- 光标两侧同时匹配时优先使用左侧结果。
- 所有匹配都会忽略数字和空格，它们本身不影响匹配结果。
- 英文匹配识别英文字母和英文半角标点；大写匹配只额外跳过 `-`、`_`。
- 所有模式都通过光标颜色显示当前中文、英文或 CapsLock 输入类型，选色不判断 Vim 模式。
- 切换前校验编辑器焦点和 Windows 前台窗口，避免排队事件影响外部程序。
- 除严格 Normal 模式必须关闭 CapsLock 外，只关闭插件自己开启的 CapsLock，不修改用户手动开启的 CapsLock。

## 环境要求

- Windows 10/11 x64
- 小狼毫（Rime/Weasel）
- JetBrains IDE 2026.1（build 261.*）+ IdeaVim，或 VSCode 1.85+ + VSCodeVim

目前原生模块只支持 Windows x64，小狼毫以外的输入法类型尚未完整实现。

## 安装

### JetBrains IDE

1. 从 [Releases](https://github.com/zhou96-gh/AutoSwitchIME/releases/latest) 下载 `AutoSwitchIME-IntelliJ-<version>.zip`。
2. 打开 `Settings > Plugins`，点击齿轮按钮，选择 `Install Plugin from Disk...`。
3. 选择下载的 ZIP，安装后重启 IDE。
4. 在 `Settings > Tools > 自动切换输入法` 中检查配置。

### VSCode

1. 从 [Releases](https://github.com/zhou96-gh/AutoSwitchIME/releases/latest) 下载 `AutoSwitchIME-VSCode-<version>.vsix`。
2. 在扩展面板的 `...` 菜单中选择 `Install from VSIX...`。

也可以使用命令行安装：

```powershell
code --install-extension .\AutoSwitchIME-VSCode-<version>.vsix
```

配置入口为 `Settings > Extensions > Auto Switch IME`。

插件内置 `ime_sys.dll`，直接读取当前焦点编辑器的 Windows 系统 IME 转换状态和物理 CapsLock。安装 IntelliJ ZIP 或 VSCode VSIX 后即可使用，不需要部署 Rime Lua、状态文件、Weasel 补丁或额外服务。

## 模式行为

| 场景 | 输入法行为 | 光标颜色 |
| --- | --- | --- |
| Normal 模式 | 始终保持小写英文 | 当前实际输入类型颜色 |
| Visual、Command 等其他 normal-like 模式 | 进入或重新聚焦时默认英文，随后允许手动切换 | 当前实际输入类型颜色 |
| Insert 模式中文规则命中 | 中文 | 中文颜色 |
| Insert 模式大写规则命中 | 英文 + CapsLock | 大写颜色 |
| Insert 模式英文命中或无规则命中 | 英文 | 英文颜色 |

Insert 模式的中文和大写正则可分别配置光标前、光标后规则。所有规则先忽略紧邻光标的数字和空格；同一侧依次判断大写、英文、中文，左侧无结果时才检查右侧。

## 配置

主要配置项：

| 配置 | IntelliJ | VSCode |
| --- | --- | --- |
| 启用插件 | `enabled` | `autoSwitchIME.enabled` |
| 切换输入法（默认 Rime） | `imeType` | `autoSwitchIME.imeType` |
| WeaselServer 路径 | `weaselServerPath` | `autoSwitchIME.weaselServerPath` |
| 光标前中文规则 | `insertModeChineseBeforeRegex` | `autoSwitchIME.chineseBeforeRegex` |
| 光标后中文规则 | `insertModeChineseAfterRegex` | `autoSwitchIME.chineseAfterRegex` |
| 光标前大写规则 | `insertModeCapsBeforeRegex` | `autoSwitchIME.capsBeforeRegex` |
| 光标后大写规则 | `insertModeCapsAfterRegex` | `autoSwitchIME.capsAfterRegex` |

当前“切换输入法”只开放 Rime。运行时由 `ImeGateway` 组合系统级默认能力与输入法级可选能力：Rime 只覆盖 Weasel 中英文切换，状态读取和 CapsLock 默认使用 Windows Provider。两级 Registry 分别预留其他操作系统和输入法实现入口。`WeaselServer.exe` 默认从注册表和常见安装目录自动检测；自动检测失败时再手动填写完整路径。

恢复全部默认配置：

- IntelliJ：在 `Settings > Tools > 自动切换输入法` 点击“恢复默认设置”，再点击“应用”。点击“取消”可撤销本次恢复。
- VSCode：打开命令面板，执行 `Auto Switch IME: 恢复默认设置`。命令只清除本插件的用户、工作区和工作区文件夹配置，随后可按提示重新加载窗口。

## 开发与构建

开发环境统一使用 Docker：

```bash
docker compose build
docker compose run --rm dev ./scripts/build-all.sh
```

打包产物输出到：

```text
packages/AutoSwitchIME-IntelliJ-<version>.zip
packages/AutoSwitchIME-VSCode-<version>.vsix
```

运行测试：

```bash
docker compose run --rm dev ./gradlew :core:test :intellij:compileKotlin
docker compose run --rm --workdir /workspace/vscode dev npm test
```

## 故障排查

- 无法切换输入法：检查 `WeaselServer.exe` 路径，并确认小狼毫正在运行。
- 手动切换后状态不更新：运行 `ime-watch.exe`，确认编辑器聚焦时 `mode` 会在 `native` 与 `ascii` 之间变化。
- IntelliJ：在 `Settings > Tools > 自动切换输入法` 使用“检测配置状态”，必要时开启日志。
- VSCode：打开输出面板并选择 `Auto Switch IME` 查看日志。

## 项目结构

```text
core/                       Kotlin 公共逻辑
  src/main/kotlin/.../ime/
    ImeGateway.kt           两级能力选择与状态合并
    input/                  输入法级 Provider
    system/                 系统级 Provider 与原生绑定
intellij/                   JetBrains 插件
vscode/                     VSCode 扩展
  src/ime/
    ImeGateway.ts           两级能力选择与状态合并
    input/                  输入法级 Provider
    system/                 系统级 Provider 与原生绑定
ime-sys/                    Windows 原生输入法与 CapsLock 接口
scripts/                    诊断、校验和打包脚本
```

## 许可证

[MIT License](LICENSE)
