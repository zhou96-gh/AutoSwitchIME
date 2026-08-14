# 输入法控制 (IME Control)

## 架构

```text
Coordinator
  -> ImeGateway
      ├── ImeProvider             # 输入法级可选能力
      │   └── RimeImeProvider     # 仅 Weasel 中英文切换
      └── SystemImeProvider       # 系统级默认能力
          └── WindowsSystemImeProvider
              └── NativeImeSys -> ime_sys.dll -> Win32
```

Kotlin 与 TypeScript 必须保持同一职责：

- 两端 IME 代码统一放入 `ime/`：Gateway 位于根级，输入法级实现位于 `ime/input/`，系统级实现与原生绑定位于 `ime/system/`。
- `ImeGateway`：状态逐字段合并、能力选择、状态缓存与发布、CapsLock 所有权。
- `ImeProvider`：按需暴露输入法专用状态源、中英文切换或 CapsLock 切换。
- `SystemImeProvider`：提供当前操作系统的默认状态读取与切换能力。
- 两级 Registry：分别按输入法类型和操作系统类型注册实现。
- Coordinator：焦点门禁、请求代次、规则、composition 保护与效果执行。

插件不依赖 Lua、Rime 状态文件、Weasel 补丁或额外常驻服务。`ime_sys.dll` 内置在 IntelliJ ZIP 和 VSCode VSIX 中，不单独部署。

## 能力选择

状态读取按字段选择，不要求输入法 Provider 实现完整状态：

```text
中英文：ImeProvider.readAsciiMode()  ?? SystemImeProvider.readAsciiMode()
大写：  ImeProvider.readCapsLock()   ?? SystemImeProvider.readCapsLock()
输入中：ImeProvider.readComposing()  ?? SystemImeProvider.readComposing()
```

切换按动作选择：

```text
中英文：ImeProvider.asciiModeSwitcher ?? SystemImeProvider
大写：  ImeProvider.capsLockSwitcher  ?? SystemImeProvider
```

- 输入法级能力未实现时，使用系统级默认实现。
- 输入法级状态字段返回不可用时，可以读取系统级状态。
- 输入法级切换已经接管但执行失败时，必须返回失败，不得再调用系统切换，避免一次动作执行两套语义。
- 切换完成后必须重新读取实际状态，不能根据目标动作直接修改 UI。

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

composition 使用前台 GUI 线程的实际焦点子窗口查询：

```text
GetGUIThreadInfo(hwndFocus)
-> ImmGetContext
-> ImmGetCompositionStringW(GCS_COMPSTR)
```

查询不可用时当前 Windows Provider 返回非 composing，不从文件或脚本回退。CapsLock 由 `ime_caps_read()`、`ime_caps_set()` 提供默认实现。

## Rime 能力

Rime Provider 当前只实现输入法级中英文切换：

```text
WeaselServer.exe /ascii
WeaselServer.exe /nascii
```

中英文、大写和 composing 状态以及 CapsLock 切换均未写 Rime 专用服务，默认使用 Windows Provider。

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

监听器和 UI 不得直接调用 Provider；系统轮询只在编辑器聚焦时运行。

## CapsLock 边界

精确 Normal 模式必须保持小写英文。除该规则外，只有插件从关闭状态主动开启的 CapsLock 才由 `ImeGateway` 释放；窗口失焦或插件停用不得关闭用户手动开启的 CapsLock。每次修改前必须再次确认请求有效、编辑器聚焦且前台窗口属于当前应用。
