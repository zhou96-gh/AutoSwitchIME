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
| `target/.../release/ime-diag.exe` | 一次性状态诊断（读状态文件 + 物理 CapsLock） |
| `target/.../release/ime-watch.exe` | CLI 监听工具，轮询物理 CapsLock + 状态文件变化 |

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

**native 绑定**：VSCode 通过 `koffi` 加载 `bin/ime_sys.dll`（FFI 直接调用 ime_caps_read/toggle/set），
`bin/ime_sys.dll` + `node_modules/koffi/` + `node_modules/@koromix/koffi-win32-x64/` 自动打包进 VSIX。

## 诊断

### 一次性状态
```bash
ime-diag.exe
```
输出：状态文件路径、内容、物理 CapsLock 状态

### 持续监听
```bash
ime-watch.exe
```
同时监听物理 CapsLock + 状态文件，仅变化时打印：
```
   elapsed   ascii  caps_f  compos  phys_c
[    0.0s]    true   false   false   false  initial
[    2.1s]   false    true   false    true  change
[    5.3s]    true   false   false   false  change
```
`Ctrl+C` 退出。

## Rime Lua 桥部署

### WSL (zsh/bash)

```bash
# 安装（交互选择方案）
./scripts/ime-bridge-install.sh

# 跳过交互直接指定方案
./scripts/ime-bridge-install.sh -s rime_ice

# 指定自定义 Rime 目录
./scripts/ime-bridge-install.sh -d /mnt/d/Rime

# 启动监听
./scripts/ime-bridge-install.sh -w

# 卸载
./scripts/ime-bridge-install.sh -u

# 帮助
./scripts/ime-bridge-install.sh -h
```

### Windows PowerShell

```powershell
.\scripts\ime-bridge-install.ps1
.\scripts\ime-bridge-install.ps1 -Schema rime_ice
.\scripts\ime-bridge-install.ps1 -Watch
.\scripts\ime-bridge-install.ps1 -Uninstall
```

状态文件写入 `%TEMP%\ime-state-rime.json`（write-tmp-rename 原子写入）。
