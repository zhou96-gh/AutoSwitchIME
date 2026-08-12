# Auto Switch IME

适用于 Windows + 小狼毫（Rime/Weasel）+ VSCodeVim 的输入法自动切换扩展。

## 功能

- 进入 Normal、Visual、Command 等 normal-like 模式时默认切换英文，之后仍允许手动切换。
- Insert 模式根据光标两侧内容自动选择中文、大写或英文。
- 所有匹配忽略数字和空格；同时命中左右上下文时优先左侧。
- normal-like 模式恢复 VSCode 原生光标颜色，Insert 模式用颜色显示输入法状态。
- 切换前校验 VSCode 焦点和 Windows 前台窗口，避免影响外部程序。
- 命令面板提供 `Auto Switch IME: 恢复默认设置`。

## 使用前准备

扩展依赖 Rime Lua 状态桥。完整安装、配置和故障排查说明见 [GitHub 项目主页](https://github.com/zhou96-gh/RimeVimIME#readme)。

## 系统要求

- Windows 10/11 x64
- 小狼毫（Rime/Weasel）
- VSCode 1.85+
- VSCodeVim

## 问题反馈

[GitHub Issues](https://github.com/zhou96-gh/RimeVimIME/issues)

## 许可证

MIT
