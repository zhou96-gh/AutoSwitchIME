package com.auto_switch_ime.core.ime

import com.auto_switch_ime.core.ImeState
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.*
import com.sun.jna.win32.StdCallLibrary

/**
 * IME 状态检测器
 * 优先使用原生 DLL，回退状态文件/JNA IMM32 API
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
     *
     * 策略（双保险）：
     * 1. 原生 DLL: ime_is_composing() 通过 IMM32 直接查前台窗口 IME 输入状态
     * 2. Fallback: Lua 状态文件（Rime ctx:is_composing()）
     */
    fun isComposing(stateWatcher: StateWatcher): Boolean {
        val nativeResult = NativeImeSys.imeIsComposing()
        if (nativeResult >= 0) return nativeResult == 1
        return stateWatcher.isComposing
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
            val isCapsLock = NativeImeSys.imeCapsRead()

            ImeState(isAsciiMode, isCapsLock)
        } catch (e: Exception) {
            trackedState
        }
    }
}
