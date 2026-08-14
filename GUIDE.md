# 操作指南

## Docker 开发环境

```bash
# 构建镜像（首次或依赖变更时）
docker compose build

# 进入容器
docker compose run --rm dev
```

## Rust 原生模块 (ime-sys)

```bash
# 构建所有（.dll + 二进制工具）
cd ime-sys && cargo build --release --target x86_64-pc-windows-gnu

# 构建特定工具
cargo build --release --target x86_64-pc-windows-gnu --bin ime-diag
cargo build --release --target x86_64-pc-windows-gnu --bin ime-helper
cargo build --release --target x86_64-pc-windows-gnu --bin ime-watch
```

**产物：**

| 文件 | 用途 |
|---|---|
| `target/.../release/ime_sys.dll` | IntelliJ JNA 加载 |
| `target/.../release/ime-diag.exe` | 一次性系统 IME、composition 和物理 CapsLock 诊断 |
| `target/.../release/ime-watch.exe` | CLI 监听系统 IME、composition 和物理 CapsLock 变化 |

### 原生输入框实测

先构建 release DLL，再从 Windows PowerShell 运行：

```powershell
pwsh -NoLogo -File .\scripts\ime-system-test.ps1
```

测试窗口提供真实输入框和 `ENGLISH`、`CHINESE`、`CAPS` 三个动作。只有测试窗口位于前台时，窗口标题、状态行和输入框背景才会每 100ms 使用 `ime_sys.dll` 的实际读取结果更新；失焦后显示 `INACTIVE`。模式动作会先恢复输入框焦点，并根据实际系统状态最多重试 3 次，以覆盖新输入框首次创建 Weasel session 的竞态。该脚本只用于开发验证，不进入插件发布包。

## IntelliJ 插件

```bash
# 构建（需要先编译 ime_sys.dll，自动拷贝到资源）
./gradlew :intellij:buildPlugin -x buildSearchableOptions -x prepareJarSearchableOptions

# 产物
packages/AutoSwitchIME-IntelliJ-<version>.zip
```

## VSCode 扩展

```bash
cd vscode
npm install
npx vsce package --allow-missing-repository
cp auto-switch-ime-<version>.vsix ../packages/AutoSwitchIME-VSCode-<version>.vsix
rm auto-switch-ime-<version>.vsix

# 产物
packages/AutoSwitchIME-VSCode-<version>.vsix
```

**native 绑定**：VSCode 通过 `koffi` 加载 `bin/ime_sys.dll`，读取/切换系统 IME 状态并控制 CapsLock，
`bin/ime_sys.dll` + `node_modules/koffi/` + `node_modules/@koromix/koffi-win32-x64/` 自动打包进 VSIX。

## 诊断

### 一次性状态
```bash
ime-diag.exe
```
输出：系统 IME 可用性、open、ascii mode、conversion flags、composition 和物理 CapsLock。

### 持续监听
```bash
ime-watch.exe
```
同时监听系统 IME、composition 和物理 CapsLock，仅变化时打印：
```
[     0.0s] mode=native      caps=false composing=0 raw=4294969217 initial
[     2.1s] mode=ascii       caps=false composing=0 raw=4294967296 change
```
`Ctrl+C` 退出。

插件直接通过内置 `ime_sys.dll` 读取 Windows 系统 IME 状态，不需要部署 Rime Lua、状态文件或额外服务。
