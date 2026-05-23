# 已知问题与待完成项

## 已完成 ✅

| 项目 | 版本 | 备注 |
|------|------|------|
| Vim 模式监听 | 1.0.0 | RimeVimExtension 实现 ModeChangeListener |
| ideavim-integration.xml 修复 | 1.0.0 | 使用 `<vimExtension>` 注册 |
| IdeaVim Gradle 依赖 | 1.0.0 | 插件 ID `IdeaVIM`，版本 2.35.2 |
| Insert 模式正则规则 | 1.0.0 | `evaluateInsertModeRules()` 已实现 |
| JNA 降级处理 | 1.0.0 | try-catch 返回 null |
| JNA SendMessage 修复 | 1.0.0 | 使用 `StdCallLibrary` + `SendMessageW` |
| 构建成功 | 1.0.0 | BUILD SUCCESSFUL |
| Agent 配置分模块 | 1.0.0 | 迁移到 `.opencode/agents/` 目录 |
| ModeChangeListener API 验证 | 1.0.0 | 签名 `modeChanged(editor, oldMode)` 与 IdeaVim 2.35.2 一致 |
| 插件打包 | 1.0.0 | `build/distributions/RimeVimIME-1.0.0.zip` (~77 KB) |
| 日志级别 | 1.0.0 | 关键路径 `info`，细节 `debug`，支持 `#debug#separate` 单独文件输出 |
| 模式切换逻辑 | 1.0.0 | Normal→英文, CMD_LINE→不变, Insert→规则评估(默认英文) |
| 大写切换 | 1.0.0 | 新增 `RimeController.setCapsMode()` |
| 迁移 ProjectActivity | 1.0.0 | v0.1.9: `RimeVimPlugin` 从 `StartupActivity` 迁移至 `ProjectActivity` |
| 默认规则 | 1.0.0 | v0.1.6: 中文 `.*[\u4e00-\u9fa5]$` / `^[\u4e00-\u9fa5].*`, 大写 `.*[A-Z0-9_]$` / `^[A-Z0-9_].*` |
| Composing 检测修复 | 1.0.0 | v0.1.29: `ImeStateDetector.isComposing()` 使用 Lua 脚本文件状态，回退 JNA |
| Normal 模式强制英文 | 1.0.0 | v0.1.30: `RimeStateFileWatcher` 检测手动切换中文时自动强制回英文，防递归 |
| Lua is_composing 修复 | 1.0.0 | v0.1.31: `ctx.is_composing` → `ctx:is_composing()`（方法调用非属性访问） |
| 窗口焦点检测 | 1.0.0 | v0.1.36: `forceEnglishMode()` 添加 IntelliJ 窗口聚焦检测 |
| 窗口 ID 调试信息 | 1.0.0 | v0.1.36: `switchImeMode()` 输出窗口 ID 和聚焦状态 |
| 强制切换递归防护修复 | 1.0.0 | v0.1.37: `forceEnglishMode()` 使用 `CountDownLatch` 同步等待 |
| 正则匹配规则优化 | 1.0.0 | v0.1.32: `&&` → `||`，前后任一匹配即可切换 |
| 上下文不跨行 | 1.0.0 | v0.1.33: 获取光标所在行的上下文文本，不跨行 |
| 光标移动性能优化 | 1.0.0 | v0.1.38: 防抖 50ms、正则缓存、setAsciiMode 早返回、Service 引用缓存 |
| 重复监听器移除 | 1.0.0 | v0.1.38: 移除 RimeVimPlugin 中重复的 selectionChanged 监听器 |
| 多编辑器架构重构 | 1.1.0 | 拆分为 core + intellij 模块，重命名为 AutoSwitchIME |
| zip 产物命名修复 | 1.1.0 | `buildPlugin.doLast` 自动重命名为含编辑器类型的文件名 |
| 构建输出路径统一 | 1.1.0 | 产物输出到根目录 `build/distributions/AutoSwitchIME-IntelliJ-<ver>.zip` |
| Kotlin 插件重复加载 | 1.1.0 | 根项目声明版本 `apply false`，子模块去除版本号 |
| plugin.xml 修复 | 1.1.2 | 移除 `<applicationListeners>`（EditorFactoryListener 不支持此注册方式）、恢复 `<postStartupActivity>` |
| 服务级别修复 | 1.1.2 | `AutoSwitchIMEController`/`AutoSwitchIMEStateWatcher` 的 `@Service` 添加 `Level.APP`，使 `getApplication().getService()` 能正确获取 |
| JNA 加载修复 | 1.1.2 | JNA 改为 `compileOnly`，利用 IDE 自带 JNA，解决 `UnsatisfiedLinkError: Unable to locate JNA native support library` |
| 默认颜色修正 | 1.1.3 | 英文=白色 `#FFFFFF`、中文=绿色 `#00CC66`、Caps=黄色 `#FFCC00` |
| 大写规则优化 | 1.1.3 | 前 `.*[A-Z]{2,}[0-9_]?$`（≥2大写）、后 `^[A-Z][0-9_]?.*`（单个大写开头） |
| 日志默认关闭 | 1.1.3 | logError/logWarn/logInfo/logDebug 默认全部 false |
| 中文规则源码直写 | 1.1.3 | `[\u4e00-\u9fa5]` → `[一-龥]`，避免配置页显示转义字符 |
| isComposing 跳过修复 | 1.1.4 | Insert 模式只跳过切英文（`action == ENGLISH`），切中文/大写照常执行 |
| JNA isComposing 回退移除 | 1.1.4 | `isComposingViaJna()` 的 `IMC_GETCONVERSIONMODE` 只能检测中文模式而非真实的 composing 状态，导致 Lua 写入 `false` 后被 JNA 覆写为 `true`。改用只信任状态文件 |
| 中文规则恢复 Unicode | 1.1.4 | `[一-龥]` → `[\u4e00-\u9fa5]`（编译后等价，源码可读性偏好） |
| Caps Lock 状态初始化 | 1.1.5 | Lua init `caps_lock = false`（用户确认 Lua 逻辑正确，状态由按键检测更新） |

## 待完成 🔄

| 项目 | 优先级 | 描述 |
|------|--------|------|
| 单元测试 | 低 | `src/test/` 目录已创建，JUnit 依赖缺失 |
| `runReadAction` 弃用 | 中 | 建议使用 `ReadAction.nonBlocking` |

## 已知问题 ⚠️

| 问题 | 影响 | 解决方案 |
|------|------|----------|
| `runReadAction` 弃用警告 | 低 | 迁移到 `ReadAction.nonBlocking` |

## Agent 配置更新规范

开发过程中，当以下情况发生时必须同步更新对应模块文档：

1. **新增/修改 API 调用** → 更新 `ideavim-integration.md` 或 `ime-control.md`
2. **版本升级** → 更新 `env-build.md`
3. **新增配置项** → 更新 `settings.md`
4. **UI/光标变更** → 更新 `ui-caret.md`
5. **新 bug/修复** → 更新 `issues.md`

## 编译错误自动处理规范

遇到编译错误时按以下流程自动处理：

1. **JUnit 5 参数顺序**: Kotlin 调用 Java 静态方法时，`assertTrue`/`assertFalse` 参数顺序为 `(condition, message)`，不是 JUnit 4 的 `(message, condition)`
2. **弃用 API**: 自动迁移到推荐的新 API（如 `runReadAction` → `ReadAction.nonBlocking`）
3. **依赖冲突**: 检查是否与平台内置库冲突，改用 `compileOnly`
4. **验证步骤**: 修复后必须运行 `compileTestKotlin` 确认通过，再运行 `test`

## JNA 调用规范

| 问题 | 原因 | 修复 |
|------|------|------|
| `UnsatisfiedLinkError: SendMessage` | user32.dll 导出 `SendMessageW`/`SendMessageA`，无裸 `SendMessage` | 接口继承 `StdCallLibrary`，方法名用 `SendMessageW` |
| `GetKeyState` 不在平台 User32 中 | JNA 5.14.0 的 `com.sun.jna.platform.win32.User32` 未包含此方法 | 自定义 `MyUser32` 接口 |
| 调用约定不匹配 | Windows API 使用 `__stdcall` | 接口继承 `StdCallLibrary` 而非 `Library` |

## 调试指南 🔍

### 日志级别控制
插件提供 5 级日志控制（`Settings → Tools → RimeVim IME`）：
- **OFF**: 关闭所有插件日志
- **ERROR**: 仅输出错误信息
- **WARN**: 输出警告和错误
- **INFO**: 输出关键操作（模式切换、WeaselServer 调用）- **默认**
- **DEBUG**: 输出详细信息（正则匹配、上下文文本、状态检测）

### 日志输出位置
- **默认**: 输出到 IDE 主日志 `idea.log`
- **独立文件**: 在 **Help → Debug Log Settings** 中输入 `com.rimevim#debug#separate`，日志将输出到 `idea_debug_com.rimevim.log`

### 问题排查流程
1. **检查插件是否加载**: 查看日志中是否有 `RimeVim IME plugin starting...`。若无，检查插件是否被禁用。
2. **检查扩展初始化**: 查看是否有 `init() called`。若无，说明 IdeaVim 未加载扩展。
3. **检查 WeaselServer 路径**: 日志中应显示 `WeaselServer path: ...`。若为 `(not found)`，需在设置中手动配置路径。
4. **检查模式切换事件**: 切换 Vim 模式时，日志应显示 `modeChanged: ...`。若无，检查 IdeaVim 扩展点注册。
5. **检查命令执行**: 日志应显示 `Switched to ... mode`。若失败，检查 WeaselServer.exe 是否存在及权限。

### 常见问题
- **无任何日志**: 插件未加载或日志级别为 OFF。检查 `Settings → Plugins` 和日志级别设置。
- **有 `execute()` 但无 `init()`**: IdeaVim 未加载扩展。检查 IdeaVim 是否安装并启用。
- **有 `init()` 但无 `modeChanged`**: 监听器注册失败。检查 IdeaVim 版本兼容性。

## 构建规范

### 版本更新流程 (MUST FOLLOW)
1. **修改版本号**: 在 `gradle.properties` 中更新 `pluginVersion`
2. **构建插件包**: `$env:JAVA_HOME="D:\Program Files\Java\java-21"; .\gradlew.bat clean :intellij:buildPlugin -x buildSearchableOptions -x prepareJarSearchableOptions`
3. **验证产物**: 检查根目录 `build/distributions/AutoSwitchIME-<version>.zip`
4. **同步 Lua 脚本**: 复制 `lua/rimevim_bridge.lua` 到 `%APPDATA%\Rime\lua\rimevim_bridge.lua`
5. **重新部署 Rime**: 右击托盘图标 → 重新部署
6. **记录变更**: 更新本文件记录版本变更内容

**教训**: 严禁先构建后改版本号，否则产物文件名不匹配。多模块架构下构建命令需指定 `:intellij:buildPlugin`。

## 构建状态

- 最后构建: `BUILD SUCCESSFUL` (1.1.5)
- 输出: `build/distributions/AutoSwitchIME-IntelliJ-1.1.5.zip`
