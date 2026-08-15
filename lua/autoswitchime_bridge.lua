local M = {}

local function log_info(message)
    if log and log.info then
        log.info("[autoswitchime_bridge] " .. message)
    end
end

local function log_warning(message)
    if log and log.warning then
        log.warning("[autoswitchime_bridge] " .. message)
    end
end

local function publish_state(env)
    if not env.publish then
        return
    end

    local context = env.engine.context
    if not context then
        return
    end

    local ascii_mode = context:get_option("ascii_mode") or false
    local is_composing = context:is_composing()
    local ok, published = pcall(env.publish, ascii_mode, is_composing)
    if not ok then
        if not env.publish_error_reported then
            env.publish_error_reported = true
            log_warning("publish failed: " .. tostring(published))
        end
        return
    end

    if published then
        env.publish_error_reported = false
    elseif not env.publish_error_reported then
        env.publish_error_reported = true
        log_warning("shared memory is unavailable")
    end
end

function M.init(env)
    local user_data_dir = rime_api.get_user_data_dir()
    local library_path = user_data_dir .. "/lua/autoswitchime_ipc.dll"
    local publish, load_error = package.loadlib(library_path, "autoswitchime_publish")
    if not publish then
        log_warning("failed to load " .. library_path .. ": " .. tostring(load_error))
        return
    end

    env.publish = publish
    env.publish_error_reported = false
    env.option_connection = env.engine.context.option_update_notifier:connect(
        function(_, option_name)
            if option_name == "ascii_mode" then
                publish_state(env)
            end
        end
    )
    env.context_connection = env.engine.context.update_notifier:connect(
        function(_)
            publish_state(env)
        end
    )

    publish_state(env)
    log_info("initialized")
end

function M.func(_, env)
    -- Refresh the foreground window identity on every key event.
    publish_state(env)
    return 2
end

function M.fini(env)
    if env.option_connection then
        env.option_connection:disconnect()
    end
    if env.context_connection then
        env.context_connection:disconnect()
    end
    env.publish = nil
end

return M
