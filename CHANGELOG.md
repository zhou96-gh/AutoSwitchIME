# Changelog

## 3.2.5 - 2026-08-15

### 修复

- 光标状态与切换动作彻底解耦：删除 IntelliJ 与 VSCode 的 Rime 目标确认状态，`/ascii`、`/nascii` 成功后等待共享内存实际回报再更新光标。
- CapsLock 注入不再把目标值写入原生跟踪器；IntelliJ 在键盘消息完成后从 EDT 读取 Windows 真实 toggle 位，再通过 Gateway 更新光标与 Normal 锁定。

## 3.2.4 - 2026-08-15

### 修复

- 重写 Windows CapsLock 设置契约：关闭成功明确返回成功状态，每次动作只注入一次完整按键，不再根据未同步的线程键盘缓存重复注入，修复插件显示已处理但系统大写实际保持不变的问题。
- IntelliJ 恢复经过真实交互验证的按键释放后 100ms 单次刷新，等待 Windows 完成 CapsLock 物理转换再执行 Normal 锁定；该机制由按键事件触发，不恢复轮询。
- Normal 关闭 CapsLock 失败时记录明确警告，避免效果链静默中断。

## 3.2.3 - 2026-08-15

### 修复

- IntelliJ 处理物理输入状态变化时实时读取 IdeaVim 模式，不再依赖编辑器的 strict Normal 缓存，修复 CapsLock 状态已更新光标但未生成关闭大写动作的问题。

## 3.2.2 - 2026-08-15

### 修复

- IntelliJ 使用 Rime 状态通知时，按键释放仍会即时采集 Windows 物理 CapsLock，修复精确 Normal 模式下手动开启大写后未恢复小写英文的问题；Rime 中英文继续使用事件通知，不恢复状态轮询。

## 3.2.1 - 2026-08-14

### 修复

- Rime 已声明专用状态源但共享内存不可用时，IntelliJ 与 VSCode 运行时暂停规则切换、Normal 锁定和光标更新，不再回退不可靠的系统中英文值打断中文 composition；状态源恢复后自动恢复。
- Rime 状态变化通过 Windows Named Event 通知插件读取最新共享内存，支持手动与自动切换统一更新光标和 Normal 锁定；仅不支持通知的系统级实现保留焦点内轮询。
- Rime 安装器升级时先通过 `WeaselServer.exe /quit` 释放已加载 DLL，再使用 `WeaselDeployer.exe /deploy` 并检查退出码，修复 DLL 无法覆盖以及安装显示成功但 schema 仍加载旧 Lua processor 的问题。

## 3.2.0 - 2026-08-14

### 功能

- 恢复 Rime Lua 状态采集，通过 Windows 命名共享内存直接提供中英文与 composition 状态，不再读写临时状态文件，也不需要常驻服务。
- `ime_sys.dll` 同时提供 Rime Lua 写入入口和 IntelliJ/VSCode 读取入口；状态包含前台窗口、进程和事件序号，插件拒绝其他窗口及已退出 Weasel 进程留下的数据。
- 新增独立 Rime 部署包，提供双击安装、卸载和诊断入口；安装器会备份方案配置与旧 Lua 桥，再触发小狼毫重新部署。

### 调整

- Rime Provider 按一次快照读取中英文和 composition，CapsLock 继续由 Windows Provider 补齐；其他输入法未提供专用状态服务时仍回退系统级能力。
- Weasel 切换成功后的短暂确认状态收敛到 Rime Provider，并由共享内存事件序号结束确认，避免系统 conversion 状态覆盖实际输出。

## 3.1.1 - 2026-08-14

### 修复

- 精确 Normal 模式始终执行输入法级英文切换，不再因 Windows conversion 状态已经是英文而跳过 Weasel `/ascii`。
- 输入法没有状态查询能力时，使用最近一次成功切换确认中英文状态，修复 Weasel 实际输出与光标颜色不一致的问题；系统状态之后真实变化时仍会恢复跟随。

## 3.1.0 - 2026-08-14

### 调整

- 中英文状态改由 `ime_sys.dll` 通过 Windows 默认 IME 窗口和 `WM_IME_CONTROL` 直接读取，不再依赖 Rime Lua 状态桥和临时状态文件。
- IntelliJ 与 VSCode 只在编辑器焦点和前台窗口校验通过后轮询系统状态，转换值 `0` 按合法英文模式处理。
- 删除 Lua bridge、部署脚本、状态文件 watcher 和 Lua ZIP 发布产物，插件安装后无需额外组件。
- 状态与切换调整为 `ImeGateway` 两级能力模型：输入法级按需覆盖，未实现的单项能力使用系统级默认 Provider；系统 Provider 通过 Registry 预留多操作系统实现。
- Kotlin 与 TypeScript 的 IME 内部目录统一为 `ime/input` 和 `ime/system`，Gateway 固定放在 `ime` 根级。
- 项目、仓库与发布名称统一为 `AutoSwitchIME`，JetBrains 展示名统一为“自动切换输入法”。
- VSCode 打包前清理旧的 `out/` 编译目录，避免已删除模块的 JavaScript 残留进入 VSIX。

## 3.0.1 - 2026-08-13

### 调整

- Rime Lua 桥输出独立的协议 v2 session 状态文件，IntelliJ 与 VSCode 根据 `session_token` 和递增 `sequence` 拒绝过期状态。
- 删除 `%TEMP%\ime-state-rime.json` 通用状态文件和协议 v1 兼容逻辑；Lua 桥与插件必须同步升级。
- 修复桥接安装器使用 `--dir` 时仍写默认目录的问题，并在复制和重新部署失败时停止。

### 修复

- 大写规则忽略上下文中的 `-`、`_` 分隔符，修复光标左侧为 `AA_A`、右侧为 `AA` 时未切换大写的问题。

## 2.3.0 - 2026-08-13

### 功能

- IntelliJ 和 VSCode 新增“切换输入法”配置，默认使用 Rime；当前只开放已实现的 Rime Provider。
- 两端新增 Provider Registry 和统一 Provider 契约，为后续接入其他输入法保留扩展入口；无效或未开放的配置值回退到 Rime。

### 调整

- 输入监控、输入切换处理和光标颜色处理按职责拆分，移除 IntelliJ 重复且无消费者的状态监听器。
- 光标颜色只根据实际 `ImeState` 显示英文、中文或 CapsLock，不再依赖 Vim 模式、规则动作或目标状态。
- IntelliJ 与 VSCode 的默认光标颜色统一为英文 `#FFFFFF`、中文 `#00CC66`、CapsLock `#FFCC00`。
- 清理未实现的自定义切换脚本配置和已迁移到 Obsidian 的旧方案文档。

## 2.2.31 - 2026-08-12

### 修复

- IntelliJ 在任意模式切换 CapsLock 后立即读取物理状态并更新光标颜色，不再依赖 Rime 状态文件是否变化。

## 2.2.30 - 2026-08-12

### 修复

- 光标颜色与 Vim 模式完全解耦，只根据实际英文、中文或 CapsLock 输入类型刷新。
- Normal 模式检测到 CapsLock 后恢复小写英文，修复可手动切换大写的问题。

## 2.2.29 - 2026-08-12

### 修复

- 光标颜色在 Normal、Visual、Command 等模式下也始终与实际英文、中文或 CapsLock 输入状态一致。
- 为 IntelliJ 插件补充浅色和深色主题 Logo，修复 IDE 插件列表没有图片。
- 将公共 PNG Logo 提取到根目录，由 VSCode 打包流程生成扩展内副本。
- 打包检查新增 IntelliJ Logo 文件、SVG 尺寸和体积校验。

## 2.2.28 - 2026-08-12

### 修复

- Normal 模式持续保持英文，检测到手动切换为中文时自动恢复英文。
- IntelliJ 监听任意按键并主动回读 Rime 状态，两端增加 500ms 状态轮询兜底，避免文件事件丢失后无法恢复英文。
- Visual、Command 等其他 normal-like 模式仍只在进入或重新聚焦时默认英文，之后允许手动切换。
- 排除 Insert/Replace 临时 `<C-O>` Normal，避免临时普通模式错误锁定英文。

### 界面

- 更新 VSCode 扩展图标，使用三色循环标志表达输入法自动切换。

## 2.2.27 - 2026-08-12

### 修复

- Release 新增独立的 Rime Lua 桥接安装包。
- 总打包流程自动生成并校验 Lua ZIP，避免后续发布遗漏运行时必需脚本。
- README 补充从 Release 下载和部署 Lua 桥的步骤。

## 2.2.26 - 2026-08-12

首个 GitHub 公开发布版本。

### 功能

- 支持 JetBrains IDE + IdeaVim 和 VSCode + VSCodeVim。
- normal-like 模式进入或重新聚焦时默认英文，之后允许用户手动切换。
- Insert 模式根据光标两侧上下文自动选择中文、大写或英文。
- 支持一键恢复插件默认配置。
- 使用 Rime Lua 桥同步真实中英文、候选和 CapsLock 状态。

### 匹配规则

- 所有匹配忽略数字和空格，且不让它们改变匹配结果。
- 英文匹配仅识别英文字母、英文半角标点和数字。
- 大写匹配只额外跳过 `-`、`_`。
- 光标两侧同时命中时优先左侧结果。

### 稳定性

- 输入法事件通过 Coordinator 串行处理，过期请求不会覆盖当前编辑器状态。
- 系统切换前校验编辑器焦点和 Windows 前台窗口，避免影响外部程序。
- 只释放插件自己开启的 CapsLock，保留用户手动状态。
