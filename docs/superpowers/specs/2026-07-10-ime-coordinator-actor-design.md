# IME Coordinator Actor 架构设计

> 日期：2026-07-10
> 状态：已确认

## 背景

当前 IntelliJ 与 VSCode 入口会从 Vim 模式、编辑器变化、状态文件和窗口焦点等多个路径触发输入法操作。各入口分别维护防抖、去重和异步状态，容易出现以下问题：

- 活动编辑器切换时，新的更新请求被旧请求占用的锁吞掉
- 窗口失焦或插件停用后，已排队的 CapsLock 操作仍可能继续执行
- IntelliJ 旧请求虽然不再更新光标颜色，但仍会执行系统输入法切换
- Controller 的后台线程与状态监听器没有接入完整的插件生命周期

本设计通过平台层 Coordinator Actor 统一调度事件和系统操作，不改变现有输入法规则与用户可见行为。

## 目标

1. 所有会修改全局输入法或 CapsLock 的操作严格串行执行。
2. 自动切换只接受当前聚焦窗口的活动编辑器事件。
3. 新编辑上下文覆盖尚未执行的旧上下文，避免过时切换。
4. 窗口失焦和插件关闭时，使自动请求失效并释放插件持有的 CapsLock。
5. IntelliJ 与 VSCode 使用相同的事件模型和状态迁移规则。
6. Coordinator 与 Provider、规则评估和 UI 更新之间保持清晰边界。

## 非目标与兼容边界

本次重构不改变以下外部行为：

- 设置项、默认值和正则规则
- Vim 模式、选区状态与 `ImeAction` 的映射
- 光标颜色和状态栏展示规则
- `WeaselServer.exe` 的 `/ascii`、`/nascii` 参数
- 状态文件路径、JSON 格式和 Lua bridge
- IntelliJ 与 VSCode 的插件入口和用户操作方式
- 物理 CapsLock 作为唯一真相源的约束
- 插件版本号、打包产物和原生 DLL

Kotlin 与 TypeScript 无法直接共享运行时代码，因此两端独立实现 Coordinator，但必须保持事件语义和状态迁移一致。

## 总体架构

```text
平台监听器
  -> Coordinator 事件邮箱
  -> 串行状态迁移
  -> 规则评估
  -> Provider 系统操作
  -> 物理状态确认
  -> UI 更新
```

### 平台监听器

监听器只采集事件，不直接调用 Provider，也不直接决定最终光标颜色。事件包含产生时的编辑器标识和必要上下文。

### Coordinator

Coordinator 是平台内唯一的输入法调度入口，负责：

- 串行消费事件
- 维护活动编辑器和窗口焦点状态
- 合并连续的编辑上下文事件
- 计算目标 `ImeAction`
- 使过时请求失效
- 调用 Provider
- 在操作完成后确认请求仍有效，再更新 UI

### Provider

Provider 只负责系统操作和物理状态读取：

- 切换 Rime ASCII 状态
- 开启或释放插件持有的 CapsLock
- 查询 composing 和物理 CapsLock
- 监听状态文件
- 跟踪 Provider 自身已知的 ASCII 和 composing 状态

Provider 不保存活动编辑器或请求代次，不计算正则规则，也不更新 UI。

## 事件模型

Coordinator 接收以下事件：

| 事件 | 说明 |
|------|------|
| `EditorActivated` | 活动编辑器发生变化 |
| `EditorContextChanged` | Vim 模式、选区、光标或文本上下文变化 |
| `WindowFocusChanged` | IDE 或 VSCode 窗口获得或失去焦点 |
| `PhysicalStateChanged` | 状态文件或物理 CapsLock 发生变化 |
| `SettingsChanged` | 插件启用状态或规则配置变化 |
| `Shutdown` | 插件关闭或动态卸载 |

编辑上下文事件至少携带：

- 编辑器稳定标识
- Vim 模式和选区状态
- 光标前后文本

请求代次由 Coordinator 在接受有效事件时统一分配，平台监听器不生成或维护代次。

## 状态模型

Coordinator 维护以下内部状态：

- `activeEditorId`：当前活动编辑器
- `windowFocused`：窗口是否聚焦
- `enabled`：插件是否启用
- `generation`：自动请求代次
- `pendingContext`：最新待处理编辑上下文
- `running`：是否正在执行系统操作
- `shuttingDown`：是否正在关闭

Coordinator 不缓存 CapsLock 真相值。任何需要展示或判断 CapsLock 的路径都从 Provider 获取即时物理状态。

## 调度规则

1. 所有事件按邮箱顺序处理。
2. `EditorContextChanged` 只保留最新活动编辑器的最新上下文。
3. 非活动编辑器、失焦窗口或已停用插件的上下文事件直接丢弃。
4. 每次有效上下文变化递增 `generation`。
5. 系统操作执行前检查窗口、编辑器和 `generation`。
6. 系统操作中的分阶段动作在修改 CapsLock 前再次检查请求有效性。
7. 操作完成后再次检查请求有效性，只有有效请求可以更新光标和状态栏。
8. `WindowFocusChanged(false)` 递增 `generation`，清空待处理上下文，并串行释放插件持有的 CapsLock。
9. `Shutdown` 阻止新事件，递增 `generation`，等待或取消队列，再释放资源。

## IntelliJ 实现

- `AutoSwitchIMEController` 演进为应用级 Coordinator。
- 使用单线程 executor 作为事件邮箱和系统操作执行线程。
- EDT 只负责采集编辑器状态和执行 UI 更新。
- 需要读取 editor 状态时通过 EDT 快照完成，不在后台线程直接访问 Swing 状态。
- Controller 实现 IntelliJ `Disposable`，关闭后拒绝新事件。
- 关闭顺序：停止接收事件、使请求失效、停止 executor、释放 Provider。
- `AutoSwitchIMEExtension`、`AutoSwitchIMEPlugin` 和 `VimModeListener` 统一发送上下文事件，移除直接 Provider 切换路径。

## VSCode 实现

- 新增 Promise mailbox，任何时刻只运行一个事件处理循环。
- 使用 `pendingContext` 保存最新活动编辑器上下文，不再只保存布尔 `updatePending`。
- `extension.ts` 的监听器只发送事件。
- 状态文件回调和 CapsLock 轮询转换为 `PhysicalStateChanged` 事件。
- Provider 的所有系统修改只能由 Coordinator 串行调用。
- `deactivate()` 发送 `Shutdown` 并等待其完成，然后释放 UI 和监听器资源。

## 错误处理

- 单次 Provider 操作失败时记录日志，但不终止事件邮箱。
- 失败操作不得更新成功状态或目标颜色。
- 新事件在失败后仍可继续处理。
- 插件关闭期间产生的迟到事件直接忽略。
- Provider 缺失或 WeaselServer 不可用时沿用现有告警行为。

## 测试策略

### 纯调度测试

两端都应覆盖以下事件序列：

1. 编辑器 A 操作未完成时激活编辑器 B，最终只应用 B 的目标状态。
2. 多个连续上下文事件到达时，只执行最新有效目标。
3. 窗口失焦后，排队中的自动请求失效，只允许释放插件持有的 CapsLock。
4. `Shutdown` 后拒绝新事件并释放资源。
5. Provider 操作失败后，后续事件仍可执行。

### 兼容测试

- Normal-like 模式仍强制英文
- Insert 模式仍按现有正则优先级决定中文、Caps 或英文
- composing 状态仍跳过自动切换
- 光标颜色仍使用目标动作和物理 CapsLock 决定
- 状态 JSON 解析和规则评估测试保持通过

### 构建验证

所有验证必须在 Docker 临时容器中执行：

```bash
docker compose run --rm dev ./gradlew :core:test --rerun-tasks
docker compose run --rm dev ./gradlew :intellij:compileKotlin --rerun-tasks
docker compose run --rm -w /workspace/vscode dev npm test
python3 -B scripts/check-version-consistency.py
git diff --check
```

## 迁移顺序

1. 建立可独立测试的 Coordinator 事件和状态迁移模型。
2. 将 IntelliJ 调用入口迁移到 Coordinator，接入 `Disposable`。
3. 将 VSCode 调用入口迁移到 Promise mailbox。
4. 将状态文件与 CapsLock 变化迁移为 Coordinator 事件。
5. 删除已被替代的锁、pending 布尔值和直接 Provider 调用。
6. 运行完整兼容测试和编译验证。

## 风险控制

- 不同时重写规则评估、Provider 系统调用和 UI 展示。
- 每个平台先建立调度测试，再迁移监听入口。
- 保留现有日志，并为过时事件丢弃和关闭流程增加调试日志。
- 每次迁移后检查 Git diff，避免格式化或修改无关代码。
