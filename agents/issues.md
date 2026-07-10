# 已知问题与待完成项

## 已完成 ✅

| 项目 | 版本 | 备注 |
|------|------|------|
| 初始实现 | 1.0.0 | ModeChangeListener + RimeController |
| 多编辑器架构重构 | 1.1.0 | core + intellij 模块拆分 |
| VSCode 扩展移植 | 1.1.8 | TypeScript 实现 VSCodeVim 集成 |
| VSCode vim 可选 | 1.1.9 | 移除 hard dependency |
| 版本同步 | 1.1.10 | gradle.properties + package.json 同步 |
| 移除英文正则 | 1.1.13 | 英文为默认回退 |
| CapsLock 手动关闭状态同步 | 1.1.18 | 已由 v2.0 即时物理读替代 |
| 光标颜色使用物理检测状态 | 1.1.19 | IntelliJ/VSCode 颜色修正 |
| Docker 开发环境 | 1.1.20 | 全工具链 Docker 化 |
| Rust ime-sys 原生模块 | 2.0.0 | Win32 API 统一入口（caps.rs + FFI） |
| VSCode native 绑定 (koffi) | 2.0.0 | 替代 child_process/PowerShell |
| IntelliJ JNA 集成 | 2.0.0 | NativeImeSys → CapsLock 物理读写 |
| 状态重构 | 2.0.0 | 消除 `currentCapsMode`/`physicalCapsLock` 分裂 |
| 部署脚本 | 2.0.0 | `scripts/ime-bridge-install.ps1`（Win） + `scripts/ime-bridge-install.sh`（WSL） |
| CapsLock 物理读修复 | 2.0.1 | `GetKeyState` → `GetAsyncKeyState`，解决非键盘消息进程误读 |
| VSCode CapsLock 轮询 | 2.0.1 | 500ms 间隔检测物理 CapsLock 变化，光标颜色实时更新 |
| CapsLock 状态写入文件 | 2.1.1 | Lua bridge 写入真实 caps_lock，修复 StateWatcher 无法检测手动切换 |
| 修复 CapsLock 覆盖 bug | 2.1.1 | 移除错误的 modifier 检测，按键名检测不再被覆盖 |

## 待完成 🔄

| 项目 | 优先级 | 描述 |
|------|--------|------|
| 单元测试覆盖 | 中 | 已覆盖核心规则、状态解析和 Coordinator 基础调度，需继续补齐 Provider 所有权及 IDE/VSCode 集成行为测试 |

## v2.0.0 变更

- **ime-sys**: caps.rs 导出 `i32`（FFI ABI 安全），新增 ime-helper/ime-watch
- **IntelliJ**: NativeImeSys JNA 改用 `Int` 匹配 `i32`，删除 CapsLockController 胶水层，删除 `Ctrl+Shift+Alt+I` 诊断
- **VSCode**: native.ts (koffi → ime_sys.dll)，无 `.vscodeignore` 限制打包
- **状态简化**: 删除 `currentCapsMode`/`physicalCapsLock`/`syncPhysicalCapsLockState`/`exitCapsMode` → 即时物理读
