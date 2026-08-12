# 输入法控制 (IME Control)

## 架构

```
ime-sys/ (Rust)
├── caps.rs: ime_caps_read / ime_caps_toggle / ime_caps_set (导出 i32)
├── ime.rs: 前台窗口/进程检测与 composition 检测
├── ime-diag: CLI 一次性诊断
├── ime-helper: CLI 切换工具
└── ime-watch: CLI 持续监听

core/ (Kotlin)
├── ImeProvider 接口
│   ├── setAsciiMode(ascii: Boolean)
│   ├── setCapsMode()
│   ├── releaseOwnedCapsLock()
│   ├── isComposing(): Boolean
│   ├── getTrackedState(): ImeState
│   └── dispose()
├── RimeImeProvider (实现)
├── NativeImeSys (JNA → ime_sys.dll)
└── StateWatcher (WatchService 监听状态文件)

intellij/ (Kotlin + JNA)
├── AutoSwitchIMEPlugin: 插件入口，加载 NativeImeSys
└── AutoSwitchIMEController: Coordinator Actor，串行处理编辑器、焦点和物理状态事件

vscode/ (TypeScript + koffi)
├── native.ts: koffi → ime_sys.dll（FFI 直接调用）
├── RimeImeProvider.ts: ImeProvider 实现
├── StateWatcher.ts: fs.watchFile 监听状态文件
├── ImeCoordinator.ts: Promise mailbox，串行处理全部输入法事件
└── extension.ts: 插件入口和事件监听器装配
```

## 核心链路

Vim/编辑器/焦点/物理状态变化 → Coordinator → RimeImeProvider:
1. `setCapsMode()`: nativeCapsSet(true) → 物理开启 CapsLock
2. `setAsciiMode(ascii)`: 必要时释放插件自己开启的 CapsLock + WeaselServer.exe /ascii|/nascii
3. `getTrackedState()`: ascii_mode 来自跟踪值, caps_lock 来自即时物理读
4. `isComposing()`: 优先 native ime_is_composing(), 失败时 fallback 状态文件

Coordinator 是唯一自动切换入口：事件邮箱严格串行，新编辑上下文会使旧请求失效，失焦和关闭事件会清空待处理上下文并释放插件持有的 CapsLock。Provider 只执行系统操作、读取物理状态和跟踪自身 IME 状态。

IntelliJ 和 VSCode 的 UI/监听器入口不得直接调用 Provider；必须向各自 Coordinator 提交事件。同步方法只允许控制器内部或非 UI 诊断路径使用，避免 `WeaselServer.exe` 等外部调用阻塞界面。

所有会修改系统全局输入法或 CapsLock 的自动切换请求，必须在实际执行前同时确认 Coordinator 请求仍有效、编辑器实时聚焦且 Win32 前台窗口仍属于触发请求的应用。IntelliJ 校验前台窗口进程 PID，VSCode 校验获得窗口焦点时登记的 HWND；不能只依赖先前收到的焦点事件。Provider 启动 WeaselServer 或修改 CapsLock 前必须再次调用同一有效性检查。原生前台归属检测不可用时必须拒绝切换，不能降级放行。失焦后只允许释放插件自己开启的 CapsLock，不允许继续执行排队中的上下文切换。

精确 Normal 模式必须始终保持小写英文；检测到中文或 CapsLock 开启时，通过 Coordinator 的严格 Normal 动作恢复小写英文，不绑定具体输入法快捷键。严格 Normal 关闭 CapsLock 是用户手动状态所有权的唯一例外，每次物理写入前仍必须确认请求有效、编辑器聚焦且前台窗口属于当前应用。两端状态文件监听即使已启用文件系统事件，也必须每 500ms 主动回读作为漏事件兜底。IntelliJ 在编辑器聚焦期间必须于任意按键释放后主动读取物理 CapsLock 并提交状态事件，确保颜色更新不依赖状态文件变化。Visual、Command 等其他 normal-like 模式只在进入或重新聚焦时默认英文，默认动作成功后允许用户手动切换。光标颜色只读取实际输入状态，不能依赖模式判断。

物理 CapsLock 是唯一真相源，无软件镜像状态。

CapsLock 有所有权边界：除严格 Normal 模式为保持小写英文而关闭当前应用内手动开启的 CapsLock 外，只有插件从关闭状态主动打开的 CapsLock 才由插件释放；窗口失焦或插件停用不得关闭用户手动开启的 CapsLock。

## ImeProvider 接口

```kotlin
interface ImeProvider {
    val name: String
    suspend fun setAsciiMode(ascii: Boolean)
    suspend fun setCapsMode()
    suspend fun releaseOwnedCapsLock()
    suspend fun isComposing(): Boolean
    fun getTrackedState(): ImeState
    fun syncTrackedState(ascii: Boolean, caps: Boolean)
    fun dispose()
}
```

## NativeImeSys (Kotlin JNA)

加载 `ime_sys.dll`，通过 JNA 直接调用 CapsLock、前台窗口/进程和 composition 检测接口。

## WeaselServer.exe 通信

```
WeaselServer.exe /ascii    # 英文
WeaselServer.exe /nascii   # 中文
```

路径检测：用户配置 → 注册表 `HKLM\SOFTWARE\Rime\Weasel` → `WeaselRoot` → 扫描 `C:/Program Files/Rime/weasel-*`

## Rime Lua 桥

### 部署

```powershell
scripts/ime-bridge-install.sh              # 交互安装（WSL）
scripts/ime-bridge-install.sh -s rime_ice  # 跳过交互（WSL）
scripts/ime-bridge-install.sh -d PATH      # 自定义路径（WSL）
scripts/ime-bridge-install.sh -w           # 启动监听（WSL）
scripts/ime-bridge-install.sh -u           # 卸载（WSL）
scripts/ime-bridge-install.sh -h           # 帮助（WSL）
```

### 状态文件

- 路径: `%TEMP%\ime-state-rime.json`
- 写入方式: write-tmp-rename 原子写入
- 字段: `ascii_mode`, `caps_lock`, `is_composing`, `timestamp`
