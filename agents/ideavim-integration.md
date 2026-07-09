# IdeaVim 集成

## 插件依赖配置

### plugin.xml
```xml
<depends optional="true" config-file="ideavim-integration.xml">IdeaVIM</depends>
```

插件 ID 是 `IdeaVIM`（不是 `com.maddyhome.idea.vim`）。

### ideavim-integration.xml
```xml
<idea-plugin>
    <vimExtension implementation="com.auto_switch_ime.AutoSwitchIMEExtension"/>
</idea-plugin>
```

## 模式监听逻辑

```kotlin
override fun modeChanged(editor: VimEditor, oldMode: Mode) {
    val currentMode = editor.mode
    if (VimModeChecker.isNormalLikeMode(currentMode, editor.ij.selectionModel.hasSelection())) {
        // Normal/Visual/Select/Replace/Command-line/OP_PENDING/当前有选区: 强制英文
        rimeController.setAsciiMode(true)
    } else {
        // Insert 且无选区：正则规则评估 → 中文 / Caps / 英文
    }
}
```

注意：只要当前编辑器存在活动选区，即使 IdeaVim 仍报告 `Mode.INSERT`，也必须按选中/Visual 模式处理并强制英文。

## 核心文件

| 文件 | 路径 |
|------|------|
| 扩展入口 | `intellij/src/.../AutoSwitchIMEExtension.kt` |
| 插件主入口 | `intellij/src/.../AutoSwitchIMEPlugin.kt` |
| 监听器 | `intellij/src/.../listener/VimModeListener.kt` |
| Vim 模式工具 | `intellij/src/.../util/VimModeChecker.kt` |
