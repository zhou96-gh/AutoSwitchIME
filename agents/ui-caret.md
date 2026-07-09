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
- 编辑器/窗口失焦时释放插件自己开启的 CapsLock 后
- 编辑器创建时初始化
- `ImeStateDetector.getCurrentState()` 检测到手动 IME 切换时更新
- **光标颜色必须跟随输入法规则产生的目标状态**：先按 Vim 模式/正则规则决定并执行输入法动作，再用同一个目标状态更新颜色；不能为了修颜色伪造独立的 IME 状态。
- 选色前必须用物理 CapsLock 读数覆盖传入状态，不能信任状态文件中的 `caps_lock`。

## VSCode — CaretColor.ts

### API
```typescript
async updateCaretColor(action: ImeAction): Promise<void>
```

### 实现
通过 `vscode.workspace.getConfiguration().update('workbench.colorCustomizations.editorCursor.foreground', color)` 修改光标颜色。
dispose 时恢复原始颜色。

### `isCapsLock` 直接来自即时物理读（`nativeCapsRead()`），
无软件镜像状态，`getTrackedState().isCapsLock === actual physical value`。
`applyColorAndStatus()` 更新状态栏和光标颜色前也必须用物理读数覆盖传入 state。

## 文件

| 平台 | 路径 |
|------|------|
| IntelliJ | `intellij/src/.../caret/CaretColorManager.kt` |
| VSCode | `vscode/src/ui/CaretColor.ts` |
| VSCode | `vscode/src/ui/StatusBar.ts` |
