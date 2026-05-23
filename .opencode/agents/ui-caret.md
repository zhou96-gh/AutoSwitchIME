# UI 与光标管理

## CaretColorManager

### 功能
设置 `CaretVisualAttributes` 光标颜色，用于指示当前 Vim 模式状态。

### 颜色方案
| 模式 | 颜色 | 含义 |
|------|------|------|
| Normal/Visual/Select | 红色/橙色 | 英文模式（ASCII） |
| Insert/Replace | 蓝色/绿色 | 中文模式 |
| Caps | 黄色 | 大写模式 |

### API 使用
```kotlin
fun updateCaretColor(editor: Editor, isAsciiMode: Boolean, isCapsLock: Boolean) {
    val color = when {
        isCapsLock -> Color.YELLOW
        isAsciiMode -> Color.RED    // Normal 模式
        else -> Color.GREEN         // Insert 模式
    }
    val attributes = CaretVisualAttributes(color, ...)
    editor.caretModel.carets.forEach { caret ->
        caret.setVisualAttributes(attributes)
    }
}
```

### 调用时机
- `RimeController.setAsciiMode()` / `setCapsMode()` 执行后自动更新所有编辑器
- 编辑器创建时初始化（通过 `RimeVimPlugin.updateEditorState()`）
- `RimeStateFileWatcher` 检测到手动 IME 切换时更新

## 项目结构相关

```
src/main/kotlin/com/rimevim/caret/
└── CaretColorManager.kt    # 光标颜色管理
```

## 注意事项
- 光标颜色更新需在 EDT（事件调度线程）执行
- 编辑器 disposed 状态检查避免内存泄漏
- `updateAllCaretColors()` 遍历 `EditorFactory.getInstance().allEditors`
