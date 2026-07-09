package com.auto_switch_ime.util

/**
 * IdeaVim 模式检测工具
 * 统一处理 IdeaVim 启用状态和模式检测
 */
object VimModeChecker {

    /**
     * 检查 IdeaVim 是否已启用
     * @return true 如果 IdeaVim 类存在且用户已启用
     */
    fun isIdeaVimEnabled(): Boolean {
        return try {
            Class.forName("com.maddyhome.idea.vim.VimPlugin")
            com.maddyhome.idea.vim.VimPlugin.isEnabled()
        } catch (e: ClassNotFoundException) {
            false
        } catch (e: Exception) {
            AutoSwitchIMELogger.debug("Failed to check IdeaVim enabled state: ${e.message}")
            false
        }
    }

    /**
     * 检查当前是否处于需要按 Normal 处理的 Vim 模式
     * @return true 如果 IdeaVim 启用且当前模式要求英文输入
     */
    fun isNormalLikeMode(): Boolean {
        return try {
            if (!isIdeaVimEnabled()) return false

            val injector = com.maddyhome.idea.vim.api.injector
            val mode = injector.vimState.mode
            mode !is com.maddyhome.idea.vim.state.mode.Mode.INSERT
        } catch (e: Exception) {
            AutoSwitchIMELogger.debug("Failed to check Vim mode: ${e.message}")
            false
        }
    }
}
