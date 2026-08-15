# AutoSwitchIME Rime 状态桥

该组件由 Rime Lua 脚本采集 `ascii_mode` 和 composition，通过 Windows 命名共享内存保存最新状态，并用 Windows Named Event 通知 IntelliJ/VSCode 插件。它不创建状态文件，也不需要常驻服务。

## 安装

1. 解压整个 ZIP。
2. 双击 `install.cmd`，按提示选择当前使用的 Rime 输入方案。
3. 等待小狼毫重新部署完成，然后重新聚焦编辑器并输入一次。

安装器会复制以下文件：

```text
%APPDATA%\Rime\lua\autoswitchime_bridge.lua
%APPDATA%\Rime\lua\autoswitchime_ipc.dll
```

同时在所选 `<schema>.custom.yaml` 的 `patch` 中添加：

```yaml
"engine/processors/@before 0": lua_processor@*autoswitchime_bridge
```

修改已有方案文件前会生成带时间戳的备份。检测到旧 `rimevim_bridge.lua` 时也只会改名备份。

## 诊断

保持目标编辑器位于前台，在编辑器内输入一次后双击 `diagnose.cmd`：

- `rime_state: Some(...)`：共享内存可读取。
- `is_ascii_mode: true`：Rime 英文模式。
- `is_composing: true`：正在输入编码或显示候选。
- `rime_state_raw` 为负数：状态不可用，通常是尚未输入、当前前台窗口不匹配或小狼毫未加载脚本。

## 卸载

双击 `uninstall.cmd`。卸载器会删除本组件的 Lua/DLL，并从所有 `*.custom.yaml` 移除 AutoSwitchIME processor 行；被备份的旧脚本不会自动恢复。

也可以从 PowerShell 指定方案和目录：

```powershell
.\Install-AutoSwitchIME.ps1 -Schema rime_ice
.\Install-AutoSwitchIME.ps1 -Schema rime_ice -RimeDir D:\Rime
.\Install-AutoSwitchIME.ps1 -Uninstall
```
