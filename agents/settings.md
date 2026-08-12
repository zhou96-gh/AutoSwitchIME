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

设置页提供“恢复默认设置”按钮。按钮只把当前表单恢复为代码默认值，必须点击“应用”才会持久化；点击“取消”应恢复操作前已保存的配置。

## VSCode 设置

- 通过 `contributes.configuration` 注册，路径: `Settings → Extensions → Auto Switch IME`
- 配置项与 IntelliJ 对齐，前缀 `autoSwitchIME.*`
- 正则规则字段名: `chineseBeforeRegex` / `chineseAfterRegex` / `capsBeforeRegex` / `capsAfterRegex`
- 命令面板提供 `Auto Switch IME: 恢复默认设置`，清除本插件配置在用户、工作区和工作区文件夹层级的覆盖值，使其回落到 `package.json` 默认值；不修改其他扩展或编辑器配置。恢复后提示用户重新加载窗口。

## 正则规则

### 评估逻辑
1. 获取光标所在行前后文本（不跨行；IntelliJ 各 5 字符，VSCode 各 20 字符）
2. 所有模式都忽略紧邻光标的数字和空白，它们本身不能改变匹配结果；中文匹配另外忽略连续 Unicode 标点和符号，大写匹配另外只忽略 `-`、`_`
3. 每一侧依次判断大写、英文、中文；跳过数字后，英文在紧邻光标的是英文字母或英文半角标点时命中，空格和全角标点不命中
4. 左侧没有命中后才判断右侧中文、大写，因此光标两侧同时匹配时左侧优先
5. 左右均未命中时默认英文
6. IntelliJ 入口统一通过 `intellij/src/.../util/InsertModeDecision.kt` 获取上下文并调用 `RuleEvaluator`，不要在各监听器里复制规则评估逻辑。

### 性能
- `regexCache.getOrPut(pattern) { Pattern.compile(pattern) }`
- 空规则视为不匹配
