package com.auto_switch_ime.caret

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.CaretVisualAttributes
import com.auto_switch_ime.core.ImeState
import com.auto_switch_ime.core.InputDisplayMode
import com.auto_switch_ime.core.toInputDisplayMode
import com.auto_switch_ime.settings.AutoSwitchIMESettings
import java.awt.Color

object CaretColorManager {

    private val DEFAULT_ENGLISH_COLOR = Color(0xFFFFFF)    // 白色
    private val DEFAULT_CHINESE_COLOR = Color(0x00CC66)     // 绿色
    private val DEFAULT_CAPSLOCK_COLOR = Color(0xFFCC00)    // 黄色

    fun updateCaretColor(editor: Editor, state: ImeState) {
        val settings = AutoSwitchIMESettings.instance
        val color = when (state.toInputDisplayMode()) {
            InputDisplayMode.ENGLISH -> parseColor(settings.englishColor, DEFAULT_ENGLISH_COLOR)
            InputDisplayMode.CHINESE -> parseColor(settings.chineseColor, DEFAULT_CHINESE_COLOR)
            InputDisplayMode.CAPS -> parseColor(settings.capsLockColor, DEFAULT_CAPSLOCK_COLOR)
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

    fun restoreCaretColor(editor: Editor) {
        editor.caretModel.allCarets.forEach { caret ->
            val currentAttributes = caret.visualAttributes
            val restoredAttributes = CaretVisualAttributes(
                null,
                currentAttributes.weight,
                currentAttributes.shape,
                currentAttributes.thickness
            )
            caret.setVisualAttributes(restoredAttributes)
        }
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
