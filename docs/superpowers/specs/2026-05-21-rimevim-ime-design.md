# RimeVim IME 插件设计文档

**日期**: 2026-05-21
**状态**: 待实现
**技术栈**: Kotlin + IntelliJ Platform SDK + JNA

---

## 1. 概述

RimeVim IME 是一个 IntelliJ IDEA 插件，实现 IdeaVim 与小狼毫（Rime/Weasel）输入法的自动切换和光标颜色指示。

### 核心功能

1. **自动切换输入法**: 进入 Normal 模式自动切换英文，进入 Insert 模式自动切换中文
2. **光标颜色指示**: 根据输入法状态（英文/中文/CapsLock）显示不同光标颜色
3. **可配置**: Settings 面板可自定义颜色和小狼毫路径

---

## 2. 架构设计

```
┌─────────────────────────────────────────────┐
│              RimeVim IME Plugin              │
├─────────────────────────────────────────────┤
│                                              │
│  ┌──────────────┐  ┌──────────────────────┐  │
│  │ VimModeListener│→│  RimeController      │  │
│  │ (IdeaVim API) │  │  - setAsciiMode()    │  │
│  └──────────────┘  │  - getCurrentState()  │  │
│                    └──────────┬───────────┘  │
│                               │              │
│                    ┌──────────▼───────────┐  │
│                    │  CaretColorManager   │  │
│                    │  - setCursorColor()  │  │
│                    │  - loadSettings()    │  │
│                    └──────────────────────┘  │
│                                              │
│  ┌──────────────┐  ┌──────────────────────┐  │
│  │ SettingsPage  │  │  WeaselPathDetector  │  │
│  │ (Configurable)│  │  - findServerPath()  │  │
│  └──────────────┘  └──────────────────────┘  │
└─────────────────────────────────────────────┘
```

---

## 3. 核心模块

### 3.1 VimModeListener

**职责**: 监听 IdeaVim 模式变化事件

**实现**:
- 订阅 IdeaVim 的 `ModeChanged` 事件
- Normal 模式 → 调用 `RimeController.setAsciiMode(true)` + 绿色光标
- Insert 模式 → 调用 `RimeController.setAsciiMode(false)` + 红色光标
- 维护内部状态机，避免重复切换

**依赖**: IdeaVim 插件（optional）

---

### 3.2 RimeController

**职责**: 控制小狼毫输入法的 ASCII/中文模式

**实现**:
- `setAsciiMode(boolean ascii)`: 通过 `ProcessBuilder` 调用 `WeaselServer.exe /ascii` 或 `/nascii`
- 内部维护状态机跟踪当前模式
- 支持启用/禁用开关

**源码依据**: WeaselServer.cpp 原生支持 `/ascii` 和 `/nascii` 命令行参数

---

### 3.3 ImeStateDetector

**职责**: 检测当前输入法状态（中文/英文/CapsLock）

**实现**:
- JNA 调用 `ImmGetDefaultIMEWnd` 获取 IME 窗口句柄
- 发送 `WM_IME_CONTROL` (0x283) + `IMC_GETCONVERSIONMODE` (0x001) 查询模式位
- bit0 = 0 表示 ASCII 模式，bit0 = 1 表示中文模式
- 调用 `GetKeyState(VK_CAPITAL)` 检测 CapsLock 状态

---

### 3.4 CaretColorManager

**职责**: 管理编辑器光标颜色

**实现**:
- 使用 IntelliJ `CaretVisualAttributes` API 设置光标颜色
- 从 `RimeVimSettings` 读取颜色配置
- 三种状态颜色：英文（默认 #00FF00）、中文（默认 #FF0000）、CapsLock（默认 #FFFF00）

---

### 3.5 RimeVimSettings

**职责**: 配置持久化和 Settings 面板

**实现**:
- 继承 `Configurable` 接口创建 Settings 页面
- 路径: `Settings → Tools → RimeVim IME`
- 配置项:
  - 启用/禁用插件
  - 英文模式光标颜色（ColorPicker）
  - 中文模式光标颜色（ColorPicker）
  - CapsLock 光标颜色（ColorPicker）
  - WeaselServer.exe 路径（文本输入 + 自动检测按钮）
- 使用 `PropertiesComponent` 持久化配置

---

### 3.6 WeaselPathDetector

**职责**: 自动检测小狼毫安装路径

**实现**:
- 从注册表 `HKLM\Software\Rime\Weasel\WeaselRoot` 读取安装路径
- 回退到常见路径扫描:
  - `C:\Program Files (x86)\Rime\weasel-*\WeaselServer.exe`
  - `C:\Program Files\Rime\weasel-*\WeaselServer.exe`
- JNA 调用 `Advapi32.RegGetValueW` 读取注册表

---

## 4. 项目结构

```
D:\ai_code\RimeVimIME\
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── src\main\
    ├── kotlin\com\rimevim\
    │   ├── RimeVimPlugin.kt              # 插件入口
    │   ├── listener\VimModeListener.kt   # Vim 模式监听
    │   ├── ime\RimeController.kt         # 小狼毫控制
    │   ├── ime\ImeStateDetector.kt       # 状态检测 (JNA)
    │   ├── ime\WeaselPathDetector.kt     # 路径检测
    │   ├── caret\CaretColorManager.kt    # 光标颜色
    │   └── settings\RimeVimSettings.kt   # 配置持久化
    └── resources\
        ├── META-INF\plugin.xml           # 插件描述
        └── messages\
            └── RimeVimBundle.properties  # 国际化
```

---

## 5. 依赖

| 依赖              | 版本   | 用途                  |
| ----------------- | ------ | --------------------- |
| IntelliJ Platform | 2024.x | 插件框架              |
| JNA               | 5.14.0 | Windows API 调用      |
| IdeaVim           | latest | 模式监听（optional）  |

---

## 6. 构建和测试

```bash
cd D:\ai_code\RimeVimIME
gradlew buildPlugin    # 构建 .zip 插件包
gradlew runIde         # 启动测试 IDE
```

---

## 7. 风险和限制

1. **WeaselServer 路径**: 需要动态检测，用户可能安装在非标准路径
2. **IMM API 兼容性**: 不同输入法对 `WM_IME_CONTROL` 的响应可能不同
3. **IdeaVim 依赖**: 模式监听需要 IdeaVim 插件已安装
4. **权限**: 调用外部进程可能需要管理员权限（取决于安装路径）

---

## 8. 未来扩展

- [ ] 支持其他 Rime 前端（鼠须管、fcitx-rime）
- [ ] 支持更多 Vim 模式（Visual、Replace 等）
- [ ] 状态栏显示当前输入法状态
- [ ] 快捷键手动切换
