package com.rimevim.caret

import com.intellij.openapi.editor.Editor
import com.rimevim.settings.RimeVimSettings
import java.awt.Color

object CaretColorManager {

    private val DEFAULT_ENGLISH_COLOR = Color(0x00CC66)    // 绿色
    private val DEFAULT_CHINESE_COLOR = Color(0xFF6666)     // 红色
    private val DEFAULT_CAPSLOCK_COLOR = Color(0xFFCC00)    // 黄色

    fun updateCaretColor(editor: Editor, isAsciiMode: Boolean, isCapsLock: Boolean) {
        val settings = RimeVimSettings.getInstance()
        if (!settings.enabled) return

        val color = when {
            isCapsLock -> parseColor(settings.capsLockColor, DEFAULT_CAPSLOCK_COLOR)
            isAsciiMode -> parseColor(settings.englishColor, DEFAULT_ENGLISH_COLOR)
            else -> parseColor(settings.chineseColor, DEFAULT_CHINESE_COLOR)
        }

        editor.caretModel.allCarets.forEach { caret ->
            val attributes = com.intellij.openapi.editor.CaretVisualAttributes(
                color,
                null
            )
            caret.setVisualAttributes(attributes)
        }
    }

    private fun parseColor(hex: String?, default: Color): Color {
        return try {
            if (hex.isNullOrBlank()) default
            else Color.decode(hex)
        } catch (e: Exception) {
            default
        }
    }
}
