# UI 与光标管理

## IntelliJ — CaretColorManager

### API
```kotlin
object CaretColorManager {
    fun updateCaretColor(editor: Editor, isAsciiMode: Boolean, isCapsLock: Boolean)
    fun updateAllCaretColors(isAsciiMode: Boolean, isCapsLock: Boolean)
}
```

### 颜色方案

| IME 状态 | 颜色 | 默认值 |
|----------|------|--------|
| 英文 (ASCII) | 白色 | `#FFFFFF` |
| 中文 | 绿色 | `#00CC66` |
| CapsLock | 黄色 | `#FFCC00` |

### 调用时机
- `setAsciiMode()` / `setCapsMode()` 执行后
- IntelliJ Coordinator 完成切换后回到 EDT 更新；回调前必须确认请求代次、目标 editor 和焦点仍有效，避免快速切换文件后旧请求覆盖新编辑器颜色
- Coordinator 在真正调用 WeaselServer、修改 CapsLock 以及更新 UI 前都必须检查请求有效性，避免队列延迟影响其他应用的全局输入法状态
- 编辑器/窗口失焦时释放插件自己开启的 CapsLock 后
- 编辑器创建时初始化
- `ImeStateDetector.getCurrentState()` 检测到手动 IME 切换时更新
- **光标颜色必须跟随输入法规则产生的目标状态**：先按 Vim 模式/正则规则决定并执行输入法动作，再用同一个目标状态更新颜色；不能为了修颜色伪造独立的 IME 状态。
- 选色前必须用物理 CapsLock 读数覆盖传入状态，不能信任状态文件中的 `caps_lock`。
- 插件启用期间，光标颜色只读取当前实际英文、中文或 CapsLock 状态，不得读取或判断 Vim 模式；IdeaVim 只管理光标形状、粗细和厚度。

## VSCode — CaretColor.ts

### API
```typescript
async updateCaretColor(action: ImeAction): Promise<void>
```

### 实现
通过 `vscode.workspace.getConfiguration().update('workbench.colorCustomizations.editorCursor.foreground', color)` 修改光标颜色。
dispose 时恢复原始颜色。
光标颜色刷新只读取当前实际输入状态，不得读取或判断 Vim 模式；Normal、Visual、Insert、Command 等模式只影响输入法切换策略。

### `isCapsLock` 直接来自即时物理读（`nativeCapsRead()`），
无软件镜像状态，`getTrackedState().isCapsLock === actual physical value`。
`applyColorAndStatus()` 更新状态栏和光标颜色前也必须用物理读数覆盖传入 state。

## 文件

| 平台 | 路径 |
|------|------|
| IntelliJ | `intellij/src/.../caret/CaretColorManager.kt` |
| VSCode | `vscode/src/ui/CaretColor.ts` |
| VSCode | `vscode/src/ui/StatusBar.ts` |
