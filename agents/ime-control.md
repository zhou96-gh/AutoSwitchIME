# 输入法控制 (IME Control)

## 架构

```text
Coordinator
  -> ImeGateway
      ├── ImeProvider             # 输入法级可选能力
      │   └── RimeImeProvider
      │       ├── 状态通知 -> Named Event -> ime_sys.dll -> 命名共享内存
      │       └── 中英文切换 -> WeaselServer.exe
      └── SystemImeProvider       # 系统级默认能力
          └── WindowsSystemImeProvider
              └── NativeImeSys -> ime_sys.dll -> Win32
```

Kotlin 与 TypeScript 必须保持同一职责：

- 两端 IME 代码统一放入 `ime/`：Gateway 位于根级，输入法级实现位于 `ime/input/`，系统级实现与原生绑定位于 `ime/system/`。
- `ImeGateway`：状态快照逐字段合并、能力选择、状态缓存与发布、CapsLock 所有权。
- `ImeProvider`：按需暴露输入法专用状态源、中英文切换或 CapsLock 切换。
- `SystemImeProvider`：提供当前操作系统的默认状态读取与切换能力。
- 两级 Registry：分别按输入法类型和操作系统类型注册实现。
- Coordinator：焦点门禁、请求代次、规则、composition 保护与效果执行。

Rime 状态不写文件。Rime 部署包把 Lua 与同一次构建的 `ime_sys.dll` 写入用户目录；DLL 在 Weasel 进程中负责写共享内存，在 IntelliJ/VSCode 进程中负责读取。该通道不需要额外常驻服务。

## 能力选择

状态读取按字段选择，不要求输入法 Provider 实现完整状态：

```text
输入法快照：ImeProvider.readState()
中英文：  快照.isAsciiMode  ?? SystemImeProvider.readAsciiMode()
大写：    快照.isCapsLock   ?? SystemImeProvider.readCapsLock()
输入中：  快照.isComposing  ?? SystemImeProvider.readComposing()
```

切换按动作选择：

```text
中英文：ImeProvider.asciiModeSwitcher ?? SystemImeProvider
大写：  ImeProvider.capsLockSwitcher  ?? SystemImeProvider
```

- 输入法级能力未实现时，使用系统级默认实现。
- 输入法没有实现专用状态源时，使用系统级状态；已声明的专用状态源整体不可用时暂停插件，恢复后自动继续。
- 专用状态源可用但没有提供某个可选字段时，该字段可以由系统级状态补齐。
- 输入法级切换已经接管但执行失败时，必须返回失败，不得再调用系统切换，避免一次动作执行两套语义。
- 输入法级状态源一次读取必须返回同一时刻的字段快照，Gateway 不得为合并同一状态重复查询各字段。
- 切换完成后必须由 `ImeGateway` 重新合并状态并发布，UI 不得直接使用规则目标动作。
- 输入切换成功只表示命令已发送，不得把动作目标写入状态快照；中英文必须等待 Rime 共享内存回报，CapsLock 必须等待 Windows 实际键盘状态回报。

## 多系统扩展

当前只注册 `WindowsSystemImeProvider`，通过 `SystemType` 和 `SystemImeProviderRegistry` 预留 `macOS`、`Linux` 实现。新增操作系统时：

1. 新增独立 `SystemImeProvider`。
2. 在 IntelliJ 与 VSCode 平台入口注册对应 `SystemType`。
3. 原生系统调用放入该系统独立模块；Windows Win32 调用继续只允许进入 `ime-sys/`。
4. 不修改 `ImeGateway`、输入法 Provider、Coordinator、规则或 UI。

## Windows 默认能力

Windows 中英文状态读取链路：

```text
GetForegroundWindow
-> ImmGetDefaultIMEWnd
-> SendMessageTimeout(WM_IME_CONTROL, IMC_GETOPENSTATUS)
-> SendMessageTimeout(WM_IME_CONTROL, IMC_GETCONVERSIONMODE)
```

`ime_get_conversion_status()` 返回值：

- 负数：查询不可用。
- 低 32 位：IME conversion flags。
- bit 32：IME open 状态。
- `IME_CMODE_NATIVE` 为 `1` 是中文，为 `0` 是英文。

转换值 `0` 是合法英文状态，不得当作失败。IME 关闭时统一归一为英文。

系统级中英文切换通过 `IMC_SETOPENSTATUS` 和 `IMC_SETCONVERSIONMODE` 修改并回读 `IME_CMODE_NATIVE`。输入法未实现专用切换时才使用该路径。

`ime_caps_set()` 返回动作是否成功，不返回目标开关值；关闭 CapsLock 成功同样必须返回 `1`。设置动作只允许注入一次完整的 CapsLock 按下/释放，不得把目标值写入状态跟踪器，也不得根据同一进程尚未同步的键盘缓存立即重试。IntelliJ 在 EDT 完成按键消息处理后通过 `ime_caps_message_state()` 采集真实 toggle 位。

composition 使用前台 GUI 线程的实际焦点子窗口查询：

```text
GetGUIThreadInfo(hwndFocus)
-> ImmGetContext
-> ImmGetCompositionStringW(GCS_COMPSTR)
```

查询不可用时当前 Windows Provider 返回非 composing，不从文件回退。CapsLock 由 `ime_caps_read()`、`ime_caps_set()` 提供默认实现。

## Rime 能力

Rime Provider 实现输入法级状态读取与中英文切换：

```text
Rime Lua context
-> autoswitchime_publish(ascii_mode, composing)
-> Local\AutoSwitchIME.RimeState.v1
-> Local\AutoSwitchIME.RimeStateChanged.v1
-> ime_rime_state_status()
-> RimeImeProvider.readState()
```

共享记录必须包含协议标识、写入进程、前台 HWND/PID、状态位和事件序号，并使用 sequence lock 保证跨进程读取一致。Lua 每次发布后触发 Named Event；IntelliJ 后台线程和 VSCode 异步 FFI 等待事件，再读取最新快照。读取端只接受与当前 Windows 前台窗口匹配的数据，并检查写入 Weasel 进程仍存活。

Lua processor 在初始化、`ascii_mode` 变化、context 更新和每次按键时发布。每次按键发布用于刷新前台窗口归属，不在 Lua 中执行规则、焦点门禁或切换决策。

中英文切换：

```text
WeaselServer.exe /ascii
WeaselServer.exe /nascii
```

Rime 状态源只提供中英文与 composing；大写状态和 CapsLock 切换使用 Windows Provider。共享内存不可用或焦点不匹配时暂停插件，不能用系统中英文值替代；共享源恢复后自动继续。

Weasel 路径检测顺序：用户配置 -> 注册表 `HKLM\SOFTWARE\Rime\Weasel` 的 `WeaselRoot` -> 常见 `weasel-*` 安装目录。

## 处理顺序

1. 焦点与前台归属门禁。
2. `ImeGateway` 读取输入法级可选状态和系统级默认状态。
3. 归一化中英文、CapsLock、composition。
4. 检查请求是否过期。
5. 应用 Vim 模式和上下文规则。
6. composition 中禁止自动切换。
7. 再次检查请求、焦点和前台归属。
8. `ImeGateway` 选择输入法级或系统级切换。
9. 重新采集实际状态并更新光标和状态栏。

监听器和 UI 不得直接调用 Provider。状态源支持变动通知时必须由 Gateway 等待通知；只有不支持通知的系统级实现才能在编辑器聚焦时轮询。

## CapsLock 边界

精确 Normal 模式必须保持小写英文，并且每次执行时都调用当前输入法 Provider 的英文切换，不能因系统级中英文缓存已经是英文而跳过；输入法内部模式可能与系统 conversion 状态不同。除该规则外，只有插件从关闭状态主动开启的 CapsLock 才由 `ImeGateway` 释放；窗口失焦或插件停用不得关闭用户手动开启的 CapsLock。每次修改前必须再次确认请求有效、编辑器聚焦且前台窗口属于当前应用。
