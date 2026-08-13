local M = {}

local PROTOCOL_VERSION = 2

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

local function json_escape(value)
    return tostring(value)
        :gsub("\\", "\\\\")
        :gsub('"', '\\"')
        :gsub("\r", "\\r")
        :gsub("\n", "\\n")
end

local function write_json_file(state_file, json)
    local tmp_file = state_file .. ".tmp"
    local file = io.open(tmp_file, "w")
    if not file then
        log_warn("Failed to open temporary file for writing: " .. tmp_file)
        return false
    end

    file:write(json)
    file:close()
    os.remove(state_file)
    if not os.rename(tmp_file, state_file) then
        log_warn("Failed to replace state file: " .. state_file)
        return false
    end
    return true
end

local function state_file_has_session(state_file, session_token)
    local file = io.open(state_file, "r")
    if not file then
        return false
    end

    local content = file:read("*a")
    file:close()
    local written_token = content:match('"session_token"%s*:%s*"([^"]+)"')
    return written_token == session_token
end

local function write_state(env, ascii_mode, is_composing)
    local temp_dir = os.getenv("TEMP")
    if not temp_dir then
        log_warn("TEMP environment variable not set")
        return
    end

    local state_file = temp_dir .. "\\ime-state-rime-v2.json"
    if state_file_has_session(state_file, env.session_token)
        and ascii_mode == env.last_ascii_mode
        and is_composing == env.last_is_composing
    then
        return
    end

    log_info("Writing state: ascii=" .. tostring(ascii_mode) .. ", composing=" .. tostring(is_composing))

    local sequence = env.sequence + 1
    local json = string.format(
        '{"protocol_version": %d, "provider": "rime", "session_token": "%s", "sequence": %d, "ascii_mode": %s, "caps_lock": false, "is_composing": %s, "timestamp": %d}',
        PROTOCOL_VERSION,
        json_escape(env.session_token),
        sequence,
        ascii_mode and "true" or "false",
        is_composing and "true" or "false",
        os.time()
    )

    if write_json_file(state_file, json) then
        env.last_ascii_mode = ascii_mode
        env.last_is_composing = is_composing
        env.sequence = sequence
        log_info("State file written successfully (atomic)")
    end
end

function M.init(env)
    log_info("rimevim_bridge.lua initialized")

    env.last_ascii_mode = nil
    env.last_is_composing = nil
    env.sequence = 0
    local session_identity = table.concat({ tostring(env), tostring(env.engine), tostring(env.engine.context) }, "-")
    env.session_token = string.format("%d-%s", os.time(), session_identity:gsub("[^%w%-_.]", ""))
    log_info("Rime session observer token=" .. env.session_token)

    env.option_conn = env.engine.context.option_update_notifier:connect(
        function(ctx, name)
            if name == "ascii_mode" then
                local ascii_mode = ctx:get_option("ascii_mode") or false
                local is_composing = ctx:is_composing()
                log_info("ascii_mode changed=" .. tostring(ascii_mode))
                write_state(env, ascii_mode, is_composing)
            end
        end
    )

    env.context_conn = env.engine.context.update_notifier:connect(
        function(ctx)
            local ascii_mode = ctx:get_option("ascii_mode") or false
            local is_composing = ctx:is_composing()
            if is_composing ~= env.last_is_composing then
                log_info("composing changed: " .. tostring(is_composing))
                write_state(env, ascii_mode, is_composing)
            end
        end
    )

    local context = env.engine.context
    if context then
        local ascii_mode = context:get_option("ascii_mode") or false
        local is_composing = context:is_composing()
        log_info("Initial state: ascii=" .. tostring(ascii_mode) .. ", composing=" .. tostring(is_composing))
        write_state(env, ascii_mode, is_composing)
    end
end

function M.func(key, env)
    local context = env.engine.context
    if not context then
        return 2
    end

    local current_ascii = context:get_option("ascii_mode") or false
    local current_composing = context:is_composing()
    if env.last_ascii_mode ~= current_ascii or env.last_is_composing ~= current_composing then
        log_info("state changed: ascii=" .. tostring(current_ascii) .. ", composing=" .. tostring(current_composing))
        write_state(env, current_ascii, current_composing)
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
