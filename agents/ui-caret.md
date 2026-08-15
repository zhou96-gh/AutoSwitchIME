# UI 与光标管理

## IntelliJ — CaretColorManager

### API
```kotlin
object CaretColorManager {
    fun updateCaretColor(editor: Editor, state: ImeState)
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
- `ImeGateway` 采集到实际状态变化时更新
- IntelliJ 编辑器聚焦期间任意按键释放后必须重启 100ms 单次计时器，待 Windows 完成物理按键转换后读取 CapsLock 并刷新颜色；该刷新不得受 Vim 模式限制，且不属于状态轮询。
- 光标模块只能接收 `ImeGateway` 合并两级 Provider 后产生的实际 `ImeState`，不能接收规则产生的目标动作、命令成功结果或临时确认状态。
- 输入法切换命令成功后不得按目标值抢先改变光标；必须等待 Rime 共享内存或 Windows GUI 线程回读到实际状态。状态暂未回报时保持上一次实际颜色。
- 光标颜色直接绑定当前实际英文、中文或 CapsLock 状态，不得读取或判断 Vim 模式、规则动作、焦点或启用状态；IdeaVim 只管理光标形状、粗细和厚度。

## VSCode — CaretColor.ts

### API
```typescript
async updateCaretColor(state: ImeState): Promise<void>
```

### 实现
通过 `vscode.workspace.getConfiguration().update('workbench.colorCustomizations.editorCursor.foreground', color)` 修改光标颜色。
dispose 时恢复原始颜色。
光标颜色刷新只读取当前实际输入状态，不得读取或判断 Vim 模式、规则动作、焦点或启用状态；Normal、Visual、Insert、Command 等模式只影响输入法切换策略。

### `isCapsLock`

当前 Rime 没有提供输入法级大写状态，因此由 Windows 系统 Provider 即时读取物理 CapsLock。Coordinator 更新状态栏和光标颜色前必须使用 `ImeGateway` 产出的实际 state。

## 文件

| 平台 | 路径 |
|------|------|
| IntelliJ | `intellij/src/.../caret/CaretColorManager.kt` |
| VSCode | `vscode/src/ui/CaretColor.ts` |
| VSCode | `vscode/src/ui/StatusBar.ts` |
