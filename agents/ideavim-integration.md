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
    controller.requestEditorUpdate(
        editor = editor.ij,
        source = "AutoSwitchIMEExtension",
        normalLikeOverride = VimModeChecker.isNormalLikeMode(
            currentMode,
            editor.ij.selectionModel.hasSelection()
        )
    )
}
```

注意：只要当前编辑器存在活动选区，即使 IdeaVim 仍报告 `Mode.INSERT`，也必须按选中/Visual 模式处理并强制英文。
监听器只提交编辑器上下文事件，不直接调用 Provider 或更新目标光标颜色；动作决策和系统切换由 `AutoSwitchIMEController` 串行处理。

## 核心文件

| 文件 | 路径 |
|------|------|
| 扩展入口 | `intellij/src/.../AutoSwitchIMEExtension.kt` |
| 插件主入口 | `intellij/src/.../AutoSwitchIMEPlugin.kt` |
| 监听器 | `intellij/src/.../listener/VimModeListener.kt` |
| Vim 模式工具 | `intellij/src/.../util/VimModeChecker.kt` |
