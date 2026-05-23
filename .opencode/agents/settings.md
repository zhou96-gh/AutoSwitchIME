# 设置面板与配置持久化

## RimeVimSettings

### 接口实现
- `PersistentStateComponent<RimeVimSettings.State>`: 配置持久化
- `Configurable`: Settings 面板注册

### 存储位置
- 配置文件: `config/options/rimevim.xml`
- 状态类: `data class State`

### 配置项
| 字段 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| enabled | Boolean | true | 启用插件 |
| logLevel | String | "INFO" | 日志级别 (OFF/ERROR/WARN/INFO/DEBUG) |
| normalCaretColor | String | "#FF0000" | Normal 模式光标颜色 |
| insertCaretColor | String | "#00FF00" | Insert 模式光标颜色 |
| capsCaretColor | String | "#FFFF00" | Caps 模式光标颜色 |
| weaselServerPath | String? | null | WeaselServer.exe 自定义路径 |
| insertModeChineseBeforeRegex | String | `.*[\u4e00-\u9fa5]$` | Insert 模式中文规则（光标前） |
| insertModeChineseAfterRegex | String | `^[\u4e00-\u9fa5].*` | Insert 模式中文规则（光标后） |
| insertModeCapsBeforeRegex | String | `.*[A-Z0-9_]$` | Insert 模式大写规则（光标前） |
| insertModeCapsAfterRegex | String | `^[A-Z0-9_].*` | Insert 模式大写规则（光标后） |

### 设置面板路径
`Settings → Tools → RimeVim IME`

## Insert 模式正则规则

### 评估逻辑 (`evaluateInsertModeRules()`)
1. 获取光标前后 5 字符上下文（不跨行）
2. 优先级匹配:
   - 中文规则（前后任一匹配）→ 中文模式
   - 大写规则（前后任一匹配）→ 大写模式
   - 默认 → 英文模式
3. 使用 `java.util.regex.Pattern` 匹配（**已缓存，避免重复编译**）

### 规则数据结构
```kotlin
// 直接使用字符串配置项，非 Rule 列表
var insertModeChineseBeforeRegex: String
var insertModeChineseAfterRegex: String
var insertModeCapsBeforeRegex: String
var insertModeCapsAfterRegex: String
```

### 性能优化
- `regexCache.getOrPut(pattern) { Pattern.compile(pattern) }` 缓存已编译 Pattern
- 空规则视为匹配（`pattern.isBlank() → true`）

## 项目结构相关

```
src/main/kotlin/com/rimevim/settings/
└── RimeVimSettings.kt   # 配置持久化 + Settings 面板
```
