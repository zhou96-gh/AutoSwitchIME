package com.rimevim.caret

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.ex.EditorEx
import com.rimevim.settings.RimeVimSettings
import java.awt.Color

object CaretColorManager {

    private val DEFAULT_ENGLISH_COLOR = Color(0x00CC66)    // 绿色
    private val DEFAULT_CHINESE_COLOR = Color(0xFF6666)     // 红色
    private val DEFAULT_CAPSLOCK_COLOR = Color(0xFFCC00)    // 黄色

    fun updateCaretColor(editor: Editor, isAsciiMode: Boolean, isCapsLock: Boolean) {
        val settings = RimeVimSettings.instance
        if (!settings.enabled) return

        val color = when {
            isCapsLock -> parseColor(settings.capsLockColor, DEFAULT_CAPSLOCK_COLOR)
            isAsciiMode -> parseColor(settings.englishColor, DEFAULT_ENGLISH_COLOR)
            else -> parseColor(settings.chineseColor, DEFAULT_CHINESE_COLOR)
        }

        editor.caretModel.allCarets.forEach { caret ->
            // 获取当前光标属性，只修改颜色，保留原有形状/粗细/厚度等设置
            val currentAttributes = caret.visualAttributes
            val newAttributes = CaretVisualAttributes(
                color,
                currentAttributes.weight,
                currentAttributes.shape,
                currentAttributes.thickness
            )
            caret.setVisualAttributes(newAttributes)
        }

        // 强制光标立即刷新（使用重绘而非 setCaretVisible，避免干扰 IdeaVim 的光标形状设置）
        editor.contentComponent.repaint()
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
