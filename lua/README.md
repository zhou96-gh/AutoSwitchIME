# RimeVim IME 桥接脚本配置指南

## 安装步骤

### 1. 复制 Lua 脚本到 Rime 用户目录

将 `rimevim_bridge.lua` 复制到 Rime 用户目录的 `lua` 子目录：

```
%APPDATA%\Rime\lua\rimevim_bridge.lua
```

如果 `lua` 目录不存在，请手动创建。

### 2. 配置 Weasel 加载 Lua 脚本

在 Rime 用户目录创建或编辑 `default.custom.yaml`（或 `weasel.custom.yaml`、`rime_ice.custom.yaml`），添加以下配置：

```yaml
patch:
  "engine/processors/@before 0": lua_processor@*rimevim_bridge
```

**注意**：
- `lua_processor@*rimevim_bridge` 中的 `*` 表示使用 `require()` 机制自动加载模块
- `rimevim_bridge` 对应文件名 `rimevim_bridge.lua`（不含扩展名，使用下划线而非连字符）
- 插入 RimeVim IME 状态监控 Lua 处理器到 processors 列表最前面
- 确保在 ascii_composer 之前接收按键事件（如 Caps Lock）
- rime使用rime_ice时，需要配置到`rime_ice.custom.yaml`中

### 3. 重新部署 Rime

右键任务栏小狼毫图标 → 选择"重新部署"

## 验证安装

部署后，在编辑器中切换一次中英文，检查 `%TEMP%\ime-state-rime.json` 文件是否生成并包含正确的状态：

```json
{"ascii_mode": true, "caps_lock": false, "timestamp": 1716364800}
```

- `ascii_mode: true` = 英文模式
- `ascii_mode: false` = 中文模式
- `caps_lock: true` = Caps Lock 开启

## 故障排除

### 状态文件未生成

1. 检查 Rime 用户目录的 `lua` 子目录是否存在
2. 确认 `rimevim_bridge.lua` 文件已正确复制
3. 检查 `default.custom.yaml`（或 `weasel.custom.yaml`、`rime_ice.custom.yaml`） 配置是否正确
4. 查看 Rime 日志（`%APPDATA%\Rime\weasel.log`）是否有 Lua 错误

### 状态文件内容不正确

1. 确认 Lua 脚本版本与插件版本兼容
2. 检查是否有其他 Lua 脚本冲突
3. 尝试重新部署 Rime

## 架构说明

```
Weasel (小狼毫)
    ↓
rimevim_bridge.lua (检测 IME 状态变化)
    ↓
%TEMP%\ime-state-rime.json (状态文件)
    ↓
StateWatcher (监听状态文件)
    ↓
CaretColorManager.updateCaretColor() (更新光标颜色)
```
