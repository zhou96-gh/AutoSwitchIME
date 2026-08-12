# 外部程序焦点保护修复设计

> 日期：2026-07-13
> 状态：已确认

## 背景

AutoSwitchIME 只应在 IntelliJ 或 VSCode 的插件编辑器获得焦点时自动切换输入法。v2.2.21 引入 Coordinator 后，IntelliJ 请求在生成时检查了编辑器焦点，但后台执行前只检查 Coordinator 内部状态。编辑器已经失焦而焦点事件尚未使请求失效时，旧请求仍可能修改全局输入法或 CapsLock，进而影响外部程序。

VSCode 已在请求执行期间检查实时窗口焦点，但需要补齐与 IntelliJ 一致的失焦回归测试，防止后续改动破坏该边界。

## 修复目标

1. 自动切换只能由当前聚焦且属于插件宿主程序的编辑器触发并完成。
2. 请求排队或执行期间一旦失焦，后续输入法和 CapsLock 开启操作立即停止。
3. 失焦时清除待处理的编辑器请求，并恢复插件自己开启的 CapsLock。
4. 用户原本开启或手动控制的 CapsLock 不由插件关闭。
5. IntelliJ 与 VSCode 保持相同的焦点保护语义。

## 实现范围

### IntelliJ

- 将编辑器实时 `hasFocus()` 和 Win32 前台窗口进程 PID 纳入请求执行期间的有效性判断，而不只依赖 Coordinator 内部状态。
- Provider 每个分阶段系统操作继续通过该有效性判断中止过时请求。
- 保留失焦事件清队列和释放插件自有 CapsLock 的现有流程。

### VSCode

- 在真实窗口聚焦事件中登记 Win32 前台窗口 HWND，并与 `window.state.focused`、活动编辑器和 Coordinator 代次联合校验。
- 确认失焦会清除待处理编辑器事件，并使节流重试无法重新产生有效请求。
- 保留只释放插件自有 CapsLock 的行为。

### 非目标

- 不改变 Vim 模式、正则规则、输入法目标状态或光标颜色规则。
- 不尝试修改或接管外部程序的输入上下文。
- 不改变用户手动 CapsLock 的所有权边界。
- 不重构 Provider 或 Coordinator 的其他职责。

## 验证

- IntelliJ：覆盖请求有效性同时依赖 Coordinator 状态和编辑器实时焦点。
- VSCode：覆盖执行中失焦、待处理请求丢弃和节流回调失焦后不执行。
- Provider：覆盖插件自有 CapsLock 在失焦时恢复、用户自有 CapsLock 保持不变。
- 在 Docker 临时容器内运行双端测试和 IntelliJ 编译。
- 更新 patch 版本为 v2.2.22，并运行版本一致性与 `git diff --check` 检查。
