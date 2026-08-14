# Auto Switch IME

适用于 Windows + 小狼毫（Rime/Weasel）+ VSCodeVim 的输入法自动切换扩展。

## 功能

- 进入 Normal、Visual、Command 等 normal-like 模式时默认切换英文，之后仍允许手动切换。
- Insert 模式根据光标两侧内容自动选择中文、大写或英文。
- 所有匹配忽略数字和空格；同时命中左右上下文时优先左侧。
- 所有 Vim 模式都用光标颜色显示当前实际输入状态，颜色不依赖 Vim 模式或切换规则。
- 设置中的“切换输入法”默认 Rime；当前版本只开放 Rime Provider。
- 切换前校验 VSCode 焦点和 Windows 前台窗口，避免影响外部程序。
- 命令面板提供 `Auto Switch IME: 恢复默认设置`。

## 使用前准备

扩展内置 Windows 输入状态组件，无需额外部署脚本或服务。完整安装、配置和故障排查说明见 [GitHub 项目主页](https://github.com/zhou96-gh/AutoSwitchIME#readme)。

## 系统要求

- Windows 10/11 x64
- 小狼毫（Rime/Weasel）
- VSCode 1.85+
- VSCodeVim

## 问题反馈

[GitHub Issues](https://github.com/zhou96-gh/AutoSwitchIME/issues)

## 许可证

MIT
