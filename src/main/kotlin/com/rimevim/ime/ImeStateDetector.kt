package com.rimevim.ime

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT

object ImeStateDetector {

    private const val WM_IME_CONTROL = 0x283
    private const val IMC_GETCONVERSIONMODE = 0x001
    private const val VK_CAPITAL = 0x14

    interface User32 : Library {
        companion object {
            val INSTANCE: User32 = Native.load("user32", User32::class.java)
        }

        fun GetForegroundWindow(): HWND
        fun GetKeyState(nVirtKey: Int): Short
        fun SendMessage(hWnd: HWND, Msg: Int, wParam: Int, lParam: LPARAM): LRESULT
    }

    interface Imm32 : Library {
        companion object {
            val INSTANCE: Imm32 = Native.load("imm32", Imm32::class.java)
        }

        fun ImmGetDefaultIMEWnd(hwnd: HWND): HWND
    }

    data class ImeState(
        val isAsciiMode: Boolean,
        val isCapsLock: Boolean
    )

    fun getCurrentState(): ImeState {
        val fgWindow = User32.INSTANCE.GetForegroundWindow()
        val imeWnd = Imm32.INSTANCE.ImmGetDefaultIMEWnd(fgWindow)

        val isAsciiMode = if (imeWnd != null) {
            val mode = User32.INSTANCE.SendMessage(
                imeWnd, WM_IME_CONTROL, IMC_GETCONVERSIONMODE, LPARAM(0L)
            )
            val modeVal = mode.toLong()
            // bit0 = 0 表示 ASCII 模式（英文）
            (modeVal and 0x01L) == 0L
        } else {
            true // 无法检测时默认为英文
        }

        val isCapsLock = (User32.INSTANCE.GetKeyState(VK_CAPITAL).toInt() and 0x01) != 0

        return ImeState(isAsciiMode, isCapsLock)
    }
}
