-- lua/rimevim_bridge.lua
-- AutoSwitchIME 桥接脚本：检测输入法状态变化并写入文件，通知编辑器插件
--
-- 安装方法：
-- 1. 将此文件复制到 Rime 用户目录的 lua 子目录（%APPDATA%\Rime\lua\）
-- 2. 在 rime_ice.custom.yaml 中添加：
--    "engine/processors/@before 0": lua_processor@*auto_switch_ime_bridge
-- 3. 重新部署 Rime

local M = {}

-- X11 Caps_Lock keysym
local XK_Caps_Lock = 0xffe5

-- 日志辅助函数
local function log_info(msg)
    if log and log.info then
        log.info("[auto_switch_ime_bridge] " .. msg)
    end
end

local function log_warn(msg)
    if log and log.warning then
        log.warning("[auto_switch_ime_bridge] " .. msg)
    end
end

---
-- 写入状态到文件
-- @param env Rime env 对象
-- @param ascii_mode boolean 是否为 ASCII 模式（英文）
-- @param caps_lock boolean 是否为大写锁定
-- @param is_composing boolean 是否正在输入（显示候选词）
local function write_state(env, ascii_mode, caps_lock, is_composing)
    -- 状态未变化时跳过写入
    if ascii_mode == env.last_ascii_mode and caps_lock == env.last_caps_lock and is_composing == env.last_is_composing then
        return
    end

    -- 确保 TEMP 环境变量存在
    local temp_dir = os.getenv("TEMP")
    if not temp_dir then
        log_warn("TEMP environment variable not set")
        return
    end

    local state_file = temp_dir .. "\\ime-state-rime.json"

    log_info("Writing state: ascii_mode=" .. tostring(ascii_mode) .. ", caps_lock=" .. tostring(caps_lock) .. ", is_composing=" .. tostring(is_composing))

    -- Windows 下直接写入，"w" 模式会截断已存在文件
    local file = io.open(state_file, "w")
    if file then
        local json = string.format(
            '{"ascii_mode": %s, "caps_lock": %s, "is_composing": %s, "timestamp": %d}',
            ascii_mode and "true" or "false",
            caps_lock and "true" or "false",
            is_composing and "true" or "false",
            os.time()
        )
        file:write(json)
        file:close()

        -- 写入成功后再更新缓存状态
        env.last_ascii_mode = ascii_mode
        env.last_caps_lock = caps_lock
        env.last_is_composing = is_composing
        log_info("State file written successfully")
    else
        log_warn("Failed to open state file for writing: " .. state_file)
    end
end

---
-- 初始化函数
-- Rime 加载脚本时调用
function M.init(env)
    log_info("rimevim_bridge.lua initialized")

    -- 存储上一次的状态，避免重复写入
    env.last_ascii_mode = nil
    env.last_caps_lock = nil
    env.last_is_composing = nil
    env.option_conn = nil
    env.context_conn = nil

    -- 监听 ascii_mode 选项变化
    env.option_conn = env.engine.context.option_update_notifier:connect(
        function(ctx, name)
            if name == "ascii_mode" then
                local ascii_mode = ctx:get_option("ascii_mode") or false
                local caps_lock = env.last_caps_lock or false
                local is_composing = ctx:is_composing()
                log_info("ascii_mode option changed: " .. tostring(ascii_mode))
                write_state(env, ascii_mode, caps_lock, is_composing)
            end
        end
    )

    -- 监听 context 更新（composing 状态变化时触发）
    env.context_conn = env.engine.context.update_notifier:connect(
        function(ctx)
            local ascii_mode = ctx:get_option("ascii_mode") or false
            local caps_lock = env.last_caps_lock or false
            local is_composing = ctx:is_composing()
            -- 只在 composing 状态变化时写入
            if is_composing ~= env.last_is_composing then
                log_info("composing state changed: " .. tostring(is_composing))
                write_state(env, ascii_mode, caps_lock, is_composing)
            end
        end
    )

    -- 写入初始状态
    local context = env.engine.context
    if context then
        local ascii_mode = context:get_option("ascii_mode") or false
        local caps_lock = false
        local is_composing = context:is_composing()
        log_info("Initial state: ascii_mode=" .. tostring(ascii_mode) .. ", is_composing=" .. tostring(is_composing))
        write_state(env, ascii_mode, caps_lock, is_composing)
    end
end

---
-- Rime Lua Processor 入口
-- 每个按键事件都会调用此函数
-- @param key Rime KeyEvent 对象
-- @param env Rime env 对象（包含 engine 等）
-- @return number 2=kNoop（不拦截按键，继续传递）
function M.func(key, env)
    local context = env.engine.context
    if not context then
        return 2
    end

    -- 检测 Caps Lock 按键事件
    local keycode = key.keycode
    if keycode == XK_Caps_Lock and not key:release() then
        -- Caps Lock 按下时切换状态（取反）
        local new_caps = not (env.last_caps_lock or false)
        local ascii_mode = context:get_option("ascii_mode") or false
        local is_composing = context:is_composing()
        log_info("Caps Lock pressed: toggled caps_state=" .. tostring(new_caps))
        write_state(env, ascii_mode, new_caps, is_composing)
        env.last_caps_lock = new_caps
    end

    -- 每次按键也检查当前 ascii_mode 和 is_composing（兜底）
    local current_ascii = context:get_option("ascii_mode") or false
    local current_composing = context:is_composing()
    if env.last_ascii_mode ~= current_ascii or env.last_is_composing ~= current_composing then
        local caps_lock = env.last_caps_lock or false
        log_info("ascii_mode changed: " .. tostring(current_ascii) .. ", is_composing: " .. tostring(current_composing))
        write_state(env, current_ascii, caps_lock, current_composing)
        env.last_ascii_mode = current_ascii
        env.last_is_composing = current_composing
    end

    -- 返回 2 (kNoop)，不干扰正常输入流程
    return 2
end

---
-- 清理函数
function M.fini(env)
    if env.option_conn then
        env.option_conn:disconnect()
    end
    if env.context_conn then
        env.context_conn:disconnect()
    end
end

return M
