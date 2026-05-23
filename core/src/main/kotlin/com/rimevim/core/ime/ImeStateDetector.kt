package com.rimevim.core.ime

import com.rimevim.core.ImeState
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.win32.StdCallLibrary

/**
 * IME 状态检测器
 * 优先使用状态文件，回退 JNA IMM32 API
 */
object ImeStateDetector {

    private const val WM_IME_CONTROL = 0x0283
    private const val IMC_GETCONVERSIONMODE = 0x0001
    private const val IMC_GETOPENSTATUS = 0x0005

    private interface MyUser32 : StdCallLibrary {
        companion object {
            val INSTANCE: MyUser32? by lazy {
                try {
                    Native.load("user32", MyUser32::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        }

        fun GetForegroundWindow(): HWND?
        fun GetKeyState(nVirtKey: Int): Short
        fun SendMessageW(hWnd: HWND?, Msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
    }

    private interface Imm32 : Library {
        companion object {
            val INSTANCE: Imm32? by lazy {
                try {
                    Native.load("imm32", Imm32::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        }

        fun ImmGetDefaultIMEWnd(hwnd: HWND?): HWND?
    }

    /**
     * 检测是否正在中文输入
     * @param stateWatcher 状态文件监听器
     * @return true 表示正在中文输入，应跳过切换
     */
    fun isComposing(stateWatcher: StateWatcher): Boolean {
        // 优先使用状态文件（最准确，来自 Rime Lua 脚本）
        if (stateWatcher.isComposing) {
            return true
        }

        // 回退 JNA
        return isComposingViaJna()
    }

    private fun isComposingViaJna(): Boolean {
        val user32 = MyUser32.INSTANCE ?: return false
        val imm32 = Imm32.INSTANCE ?: return false

        return try {
            val fgWindow = user32.GetForegroundWindow() ?: return false
            val imeWnd = imm32.ImmGetDefaultIMEWnd(fgWindow) ?: return false
            if (Pointer.nativeValue(imeWnd.pointer) == 0L) return false

            // 先检查 IME 是否打开
            val openResult = user32.SendMessageW(
                imeWnd, WM_IME_CONTROL, WPARAM(IMC_GETOPENSTATUS.toLong()), LPARAM(0L)
            )
            val isOpen = openResult.toLong() != 0L
            if (!isOpen) return false

            // IME 打开时，进一步检查是否为中文模式
            val modeResult = user32.SendMessageW(
                imeWnd, WM_IME_CONTROL, WPARAM(IMC_GETCONVERSIONMODE.toLong()), LPARAM(0L)
            )
            val modeVal = modeResult.toLong()
            val isChineseMode = (modeVal and 0x01L) != 0L

            // 只有中文 composing 时才跳过切换
            isChineseMode
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取当前 IME 状态
     * @param stateWatcher 状态文件监听器
     * @param trackedState 内部跟踪状态（回退用）
     */
    fun getCurrentState(stateWatcher: StateWatcher, trackedState: ImeState): ImeState {
        val user32 = MyUser32.INSTANCE
        val imm32 = Imm32.INSTANCE

        if (user32 == null || imm32 == null) {
            return trackedState
        }

        return try {
            val fgWindow = user32.GetForegroundWindow() ?: return trackedState
            val imeWnd = imm32.ImmGetDefaultIMEWnd(fgWindow) ?: return trackedState

            if (Pointer.nativeValue(imeWnd.pointer) == 0L) {
                return trackedState  // TSF 应用
            }

            val mode = user32.SendMessageW(
                imeWnd, WM_IME_CONTROL, WPARAM(IMC_GETCONVERSIONMODE.toLong()), LPARAM(0L)
            )
            val modeVal = mode.toLong()

            if (modeVal == 0L) {
                return trackedState  // TSF 应用
            }

            val isAsciiMode = (modeVal and 0x01L) == 0L
            val isCapsLock = (user32.GetKeyState(0x14).toInt() and 0x01) != 0

            ImeState(isAsciiMode, isCapsLock)
        } catch (e: Exception) {
            trackedState
        }
    }
}
