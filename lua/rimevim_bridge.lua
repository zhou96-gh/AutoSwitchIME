local M = {}

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

local function write_state(env, ascii_mode, caps_lock, is_composing)
    if ascii_mode == env.last_ascii_mode
        and caps_lock == env.last_caps_lock
        and is_composing == env.last_is_composing then
        return
    end

    local temp_dir = os.getenv("TEMP")
    if not temp_dir then
        log_warn("TEMP environment variable not set")
        return
    end

    local state_file = temp_dir .. "\\ime-state-rime.json"
    local tmp_file = state_file .. ".tmp"

    log_info("Writing state: ascii=" .. tostring(ascii_mode) .. ", caps=" .. tostring(caps_lock) .. ", composing=" .. tostring(is_composing))

    local json = string.format(
        '{"ascii_mode": %s, "caps_lock": %s, "is_composing": %s, "timestamp": %d}',
        ascii_mode and "true" or "false",
        caps_lock and "true" or "false",
        is_composing and "true" or "false",
        os.time()
    )

    local file = io.open(tmp_file, "w")
    if file then
        file:write(json)
        file:close()
        os.remove(state_file)
        os.rename(tmp_file, state_file)

        env.last_ascii_mode = ascii_mode
        env.last_caps_lock = caps_lock
        env.last_is_composing = is_composing
        log_info("State file written successfully (atomic)")
    else
        log_warn("Failed to open temporary file for writing: " .. tmp_file)
    end
end

function M.init(env)
    log_info("rimevim_bridge.lua initialized")

    env.last_ascii_mode = nil
    env.last_caps_lock = nil
    env.last_is_composing = nil
    env.caps_lock = false

    env.option_conn = env.engine.context.option_update_notifier:connect(
        function(ctx, name)
            if name == "ascii_mode" then
                local ascii_mode = ctx:get_option("ascii_mode") or false
                local is_composing = ctx:is_composing()
                log_info("ascii_mode changed=" .. tostring(ascii_mode))
                write_state(env, ascii_mode, env.caps_lock, is_composing)
            end
        end
    )

    env.context_conn = env.engine.context.update_notifier:connect(
        function(ctx)
            local ascii_mode = ctx:get_option("ascii_mode") or false
            local is_composing = ctx:is_composing()
            if is_composing ~= env.last_is_composing then
                log_info("composing changed: " .. tostring(is_composing))
                write_state(env, ascii_mode, env.caps_lock, is_composing)
            end
        end
    )

    local context = env.engine.context
    if context then
        local ascii_mode = context:get_option("ascii_mode") or false
        local is_composing = context:is_composing()
        log_info("Initial state: ascii=" .. tostring(ascii_mode) .. ", composing=" .. tostring(is_composing))
        write_state(env, ascii_mode, env.caps_lock, is_composing)
    end
end

function M.func(key, env)
    local caps_changed = false

    if key:repr() == "Caps_Lock" then
        env.caps_lock = not env.caps_lock
        caps_changed = true
        log_info("CapsLock toggled by key: " .. tostring(env.caps_lock))
    end

    local mod = key.modifier
    if type(mod) == "number" and mod >= 2 then
        local caps_from_mod = (mod % 4) >= 2
        if caps_from_mod ~= env.caps_lock then
            env.caps_lock = caps_from_mod
            caps_changed = true
            log_info("CapsLock from modifier: " .. tostring(env.caps_lock))
        end
    end

    local context = env.engine.context
    if not context then
        return 2
    end

    local current_ascii = context:get_option("ascii_mode") or false
    local current_composing = context:is_composing()
    if caps_changed
        or env.last_ascii_mode ~= current_ascii
        or env.last_is_composing ~= current_composing then
        log_info("state changed: ascii=" .. tostring(current_ascii) .. ", caps=" .. tostring(env.caps_lock) .. ", composing=" .. tostring(current_composing))
        write_state(env, current_ascii, env.caps_lock, current_composing)
    end

    return 2
end

function M.fini(env)
    if env.option_conn then
        env.option_conn:disconnect()
    end
    if env.context_conn then
        env.context_conn:disconnect()
    end
end

return M
