# RimeVimIME

![Auto Switch IME](resources/icon.png)

面向 Windows + 小狼毫（Rime/Weasel）的 Vim 输入法自动切换插件，同时支持 JetBrains IDE 和 VSCode。

[下载最新版本](https://github.com/zhou96-gh/RimeVimIME/releases/latest) | [更新日志](CHANGELOG.md) | [操作指南](GUIDE.md) | [问题反馈](https://github.com/zhou96-gh/RimeVimIME/issues)

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

1. 从 [Releases](https://github.com/zhou96-gh/RimeVimIME/releases/latest) 下载 `AutoSwitchIME-IntelliJ-<version>.zip`。
2. 打开 `Settings > Plugins`，点击齿轮按钮，选择 `Install Plugin from Disk...`。
3. 选择下载的 ZIP，安装后重启 IDE。
4. 在 `Settings > Tools > 自动切换输入` 中检查配置。

### VSCode

1. 从 [Releases](https://github.com/zhou96-gh/RimeVimIME/releases/latest) 下载 `AutoSwitchIME-VSCode-<version>.vsix`。
2. 在扩展面板的 `...` 菜单中选择 `Install from VSIX...`。

也可以使用命令行安装：

```powershell
code --install-extension .\AutoSwitchIME-VSCode-<version>.vsix
```

配置入口为 `Settings > Extensions > Auto Switch IME`。

### 部署 Rime 状态桥

插件通过 Lua 桥读取小狼毫的真实中英文和候选状态。先从 [Releases](https://github.com/zhou96-gh/RimeVimIME/releases/latest) 下载并解压 `RimeVimIME-Lua-<version>.zip`。

使用 WSL 且已经克隆仓库时，也可以在仓库目录运行：

```bash
./scripts/ime-bridge-install.sh
```

使用 Release 中的 Lua ZIP 手动部署：

1. 将解压后的 `rimevim_bridge.lua` 复制到 `%APPDATA%\Rime\lua\rimevim_bridge.lua`。
2. 在当前方案对应的 `*.custom.yaml` 中加入：

```yaml
patch:
  "engine/processors/@before 0": lua_processor@*rimevim_bridge
```

3. 通过小狼毫托盘菜单执行“重新部署”。
4. 切换一次中英文，确认 `%TEMP%\ime-state-rime.json` 已生成。

更详细的桥接配置见 [lua/README.md](lua/README.md)。

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

当前“切换输入法”只开放 Rime，架构通过 Provider Registry 预留其他输入法实现入口。`WeaselServer.exe` 默认从注册表和常见安装目录自动检测；自动检测失败时再手动填写完整路径。

恢复全部默认配置：

- IntelliJ：在 `Settings > Tools > 自动切换输入` 点击“恢复默认设置”，再点击“应用”。点击“取消”可撤销本次恢复。
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
- 手动切换后状态不更新：确认 `%TEMP%\ime-state-rime.json` 存在且内容随输入法变化。
- Lua 桥未生效：确认 processor 加在当前实际使用的方案配置中，并重新部署小狼毫。
- IntelliJ：在 `Settings > Tools > 自动切换输入` 使用“检测配置状态”，必要时开启日志。
- VSCode：打开输出面板并选择 `Auto Switch IME` 查看日志。

## 项目结构

```text
core/       Kotlin 公共规则、状态和 Provider
intellij/   JetBrains 插件
vscode/     VSCode 扩展
ime-sys/    Windows 原生输入法与 CapsLock 接口
lua/        Rime 状态桥
scripts/    部署、诊断和打包脚本
```

## 许可证

[MIT License](LICENSE)
