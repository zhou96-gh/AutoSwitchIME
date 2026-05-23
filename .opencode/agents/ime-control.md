# 输入法控制 (IME Control)

## RimeController (@Service)

### 核心方法
- `setAsciiMode(ascii: Boolean)`: 切换中英文模式
  - `true` → 调用 `WeaselServer.exe /ascii`（英文）
  - `false` → 调用 `WeaselServer.exe /nascii`（中文）
  - **性能优化**: 若当前已是目标模式且非 Caps，跳过进程调用
- `setCapsMode()`: 切换大写模式（使用 SendInput 模拟 CapsLock 按键）
- `exitCapsMode()`: 退出大写模式
- `getTrackedState()`: 获取内部跟踪的 IME 状态
- `syncTrackedState(ascii, caps)`: 同步内部状态（不触发实际切换）

### 内部状态跟踪
```kotlin
@Volatile private var currentAsciiMode: Boolean = true  // 默认英文
@Volatile private var currentCapsMode: Boolean = false
```

### 调用时机
- Normal/Visual/Select 模式 → `setAsciiMode(true)`
- Insert/Replace 模式 → 正则规则评估后调用
- 检测到手动切换中文 → `forceEnglishMode()` 强制回英文

## RimeStateFileWatcher (@Service)

### 功能
监听 `%TEMP%\rimevim-state.json` 文件变化，获取 Rime Lua 脚本写入的实时 IME 状态。

### 状态文件格式
```json
{
  "ascii_mode": true,
  "caps_mode": false,
  "is_composing": false,
  "timestamp": 1234567890
}
```

### Normal 模式强制英文逻辑
1. 检测到 `ascii_mode=false`（用户手动切换为中文）
2. 检查当前是否为 Normal/Visual/Select 模式
3. 检查 IntelliJ 窗口是否聚焦（`editor.contentComponent.hasFocus()`）
4. 若聚焦，调用 `forceEnglishMode()` 强制切回英文
5. 使用 `CountDownLatch` 同步等待 `invokeLater` 完成，防止递归

### 防止递归机制
- `isForcingImeSwitch` 标志：强制切换期间阻止再次触发
- `CountDownLatch`：确保 `invokeLater` 执行完成后才重置标志

## ImeStateDetector

### 功能
检测 IME 当前状态（ASCII/CapsLock/Composing）。

### isComposing() 检测策略
1. **优先**: `RimeStateFileWatcher.isComposing`（Lua 脚本直接读取 `context:is_composing()`）
2. **回退**: JNA IMM32 API（`ImmGetDefaultIMEWnd` + `SendMessageW`）

### API 使用
- `MyUser32` 接口继承 `StdCallLibrary`（Windows 标准调用约定）
- 使用 `SendMessageW` 而非 `SendMessage`（user32.dll 导出 Unicode 版本）
- `Imm32` 接口继承 `Library`，调用 `ImmGetDefaultIMEWnd`
- 返回 nullable 类型

### 降级处理
- JNA 不可用时返回 null/默认值
- 不影响插件基本功能

### 已知问题修复
- **`UnsatisfiedLinkError: SendMessage`**: user32.dll 导出 `SendMessageW`/`SendMessageA`，无裸 `SendMessage`。修复: 改用 `StdCallLibrary` + `SendMessageW`
- **TSF 应用 IME 检测不可靠**: IntelliJ 使用 TSF 框架，IMM32 API 返回错误结果。修复: 使用 Lua 脚本文件状态

## WeaselServer.exe 通信

### 命令格式
```bash
WeaselServer.exe /ascii    # 切换到英文模式
WeaselServer.exe /nascii   # 切换到中文模式
WeaselServer.exe /caps     # 切换大写模式
```

### 路径检测优先级
1. 用户自定义路径（设置面板配置）
2. 注册表 `HKEY_CURRENT_USER\Software\Rime\Weasel`
3. 常见安装路径扫描

### 窗口焦点检测
- 切换 IME 前检查 IntelliJ 窗口是否聚焦
- 输出窗口 ID 调试信息：`IntelliJ focused=true, windowId=12345678`
- 未聚焦时跳过切换，避免影响其他应用

## 项目结构相关

```
src/main/kotlin/com/rimevim/ime/
├── RimeController.kt         # @Service，核心控制器
├── ImeStateDetector.kt       # JNA + Lua 文件状态检测
├── RimeStateFileWatcher.kt   # 监听状态文件变化
├── WeaselPathDetector.kt     # 路径自动检测
└── CapsLockController.kt     # SendInput 模拟 CapsLock
```

## Lua 脚本

### 文件位置
- 项目内: `lua/rimevim_bridge.lua`
- 用户目录: `%APPDATA%\Rime\lua\rimevim_bridge.lua`

### 功能
- 监听 `context.update_notifier`，在 composing 状态变化时写入状态文件
- 读取 `context:is_composing()` 方法（非属性）
- 写入 `%TEMP%\rimevim-state.json` 包含 `ascii_mode`、`caps_mode`、`is_composing`

### 部署
每次修改后需复制到用户目录并重新部署 Rime。
