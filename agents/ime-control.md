# 输入法控制 (IME Control)

## 架构

```
ime-sys/ (Rust)
├── caps.rs: ime_caps_read / ime_caps_toggle / ime_caps_set (导出 i32)
├── ime.rs: 占位（未使用）
├── ime-diag: CLI 一次性诊断
├── ime-helper: CLI 切换工具
└── ime-watch: CLI 持续监听

core/ (Kotlin)
├── ImeProvider 接口
│   ├── setAsciiMode(ascii: Boolean)
│   ├── setCapsMode()
│   ├── isComposing(): Boolean
│   ├── getTrackedState(): ImeState
│   └── dispose()
├── RimeImeProvider (实现)
├── NativeImeSys (JNA → ime_sys.dll)
└── StateWatcher (WatchService 监听状态文件)

intellij/ (Kotlin + JNA)
└── AutoSwitchIMEPlugin: 插件入口，加载 NativeImeSys

vscode/ (TypeScript + koffi)
├── native.ts: koffi → ime_sys.dll（FFI 直接调用）
├── RimeImeProvider.ts: ImeProvider 实现
├── StateWatcher.ts: fs.watchFile 监听状态文件
└── extension.ts: 插件入口
```

## 核心链路

Vim 模式变化 → RimeImeProvider:
1. `setCapsMode()`: nativeCapsToggle() → 物理切换 CapsLock
2. `setAsciiMode(ascii)`: nativeCapsToggle()（需退出大写时）+ WeaselServer.exe /ascii|/nascii
3. `getTrackedState()`: ascii_mode 来自跟踪值, caps_lock 来自即时物理读

物理 CapsLock 是唯一真相源，无软件镜像状态。

## ImeProvider 接口

```kotlin
interface ImeProvider {
    val name: String
    suspend fun setAsciiMode(ascii: Boolean)
    suspend fun setCapsMode()
    suspend fun isComposing(): Boolean
    fun getTrackedState(): ImeState
    fun syncTrackedState(ascii: Boolean, caps: Boolean)
    fun dispose()
}
```

## NativeImeSys (Kotlin JNA)

加载 `ime_sys.dll`，通过 JNA 直接调用 `ime_caps_read/toggle/set`（返回 `Int`）。

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
