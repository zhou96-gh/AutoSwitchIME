# IdeaVim 集成

## 插件依赖配置

### plugin.xml
```xml
<depends optional="true" config-file="ideavim-integration.xml">IdeaVIM</depends>
```

**注意**: 插件 ID 是 `IdeaVIM`（不是 `com.maddyhome.idea.vim`，那是 Java 包名）

### ideavim-integration.xml
```xml
<idea-plugin>
    <vimExtension implementation="com.rimevim.RimeVimExtension"/>
</idea-plugin>
```

**注意**: 使用 `<vimExtension>` 不是 `<vimPluginExtension>`

## RimeVimExtension 核心实现

### 接口实现
- 实现 `VimExtension` + `ModeChangeListener`
- `init()` 方法无参数（不是 `init(api: VimApi)`）

### API 要点
- `editor.ij` 获取 IntelliJ Editor（需导入 `com.maddyhome.idea.vim.newapi.ij`）
- `editor.isDisposed` 是属性不是方法

### 模式监听逻辑
```kotlin
override fun modeChanged(editor: VimEditor, oldMode: Mode) {
    val currentMode = editor.mode
    when (currentMode) {
        Mode.INSERT, Mode.REPLACE -> {
            // 执行正则规则评估，切换输入法
        }
        else -> {
            // Normal/Visual/Select: 强制英文
            rimeController.setAsciiMode(true)
        }
    }
}
```

## Gradle 依赖解析历史

| 尝试 | 插件 ID | 结果 |
|------|---------|------|
| 1 | `164` | ❌ 失败 |
| 2 | `IdeaVIM` | ✅ 成功 |
| 3 | `com.maddyhome.idea.vim` | ❌ 失败 |

## 项目结构相关

```
src/main/kotlin/com/rimevim/
├── RimeVimPlugin.kt              # ProjectActivity (插件入口)
├── RimeVimExtension.kt           # 核心: VimExtension + ModeChangeListener
├── listener/
│   └── VimModeListener.kt        # EditorFactoryListener + FileEditorManagerListener
├── ime/
│   ├── RimeController.kt         # @Service，IME 控制器
│   ├── ImeStateDetector.kt       # JNA + Lua 文件状态检测
│   └── RimeStateFileWatcher.kt   # 监听 %TEMP%\rimevim-state.json
├── caret/
│   └── CaretColorManager.kt      # 光标颜色管理
└── settings/
    └── RimeVimSettings.kt        # 配置持久化 + 设置面板
src/main/resources/META-INF/
├── plugin.xml
└── ideavim-integration.xml
lua/
└── rimevim_bridge.lua            # Rime Lua 脚本 (需复制到 %APPDATA%\Rime\lua\)
```
