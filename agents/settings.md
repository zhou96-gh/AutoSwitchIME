# 设置面板与配置

## IntelliJ 设置

### 实现
- `AutoSwitchIMESettings : PersistentStateComponent` — 配置持久化，存储 `auto_switch_ime.xml`
- `AutoSwitchIMESettingsConfigurable : Configurable` — 设置面板
- 路径: `Settings → Tools → 自动切换输入`

### 配置项

| 字段 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| enabled | Boolean | true | 启用插件 |
| weaselServerPath | String | `""` | WeaselServer.exe 自定义路径 |
| englishColor | String | `#FFFFFF` | 英文模式光标颜色 |
| chineseColor | String | `#00CC66` | 中文模式光标颜色 |
| capsLockColor | String | `#FFCC00` | CapsLock 光标颜色 |
| insertModeChineseBeforeRegex | String | `.*[一-龥]$` | 中文规则（光标前） |
| insertModeChineseAfterRegex | String | `^[一-龥].*` | 中文规则（光标后） |
| insertModeCapsBeforeRegex | String | `.*[A-Z]{2,}[0-9_]?$` | 大写规则（光标前） |
| insertModeCapsAfterRegex | String | `""` | 大写规则（光标后，默认不启用） |
| logError/Warn/Info/Debug | Boolean | false | 日志级别控制 |

## VSCode 设置

- 通过 `contributes.configuration` 注册，路径: `Settings → Extensions → Auto Switch IME`
- 配置项与 IntelliJ 对齐，前缀 `autoSwitchIME.*`
- 正则规则字段名: `chineseBeforeRegex` / `chineseAfterRegex` / `capsBeforeRegex` / `capsAfterRegex`

## 正则规则

### 评估逻辑
1. 获取光标所在行前后各 5 字符（不跨行）
2. 优先级匹配（`||` 逻辑，前后任一匹配即生效）:
   - 中文规则 → 中文模式
   - 大写规则 → 大写模式
   - 默认 → 英文模式

### 性能
- `regexCache.getOrPut(pattern) { Pattern.compile(pattern) }`
- 空规则视为不匹配
