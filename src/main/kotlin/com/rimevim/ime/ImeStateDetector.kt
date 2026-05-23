package com.rimevim.ime

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary

object ImeStateDetector {

    private const val WM_IME_CONTROL = 0x283
    private const val IMC_GETCONVERSIONMODE = 0x001
    private const val IMC_GETOPENSTATUS = 0x005
    private const val VK_CAPITAL = 0x14

    interface MyUser32 : StdCallLibrary {
        companion object {
            val INSTANCE: MyUser32? = try {
                Native.load("user32", MyUser32::class.java)
            } catch (e: UnsatisfiedLinkError) {
                null
            }
        }

        fun GetForegroundWindow(): HWND
        fun GetKeyState(nVirtKey: Int): Short
        fun SendMessageW(hWnd: HWND, Msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
    }

    interface Imm32 : StdCallLibrary {
        companion object {
            val INSTANCE: Imm32? = try {
                Native.load("imm32", Imm32::class.java)
            } catch (e: UnsatisfiedLinkError) {
                null
            }
        }

        fun ImmGetDefaultIMEWnd(hwnd: HWND): HWND
    }

    data class ImeState(
        val isAsciiMode: Boolean,
        val isCapsLock: Boolean
    )

    /**
     * 检测 Rime 是否正在中文输入
     * 优先使用 RimeStateFileWatcher 的文件状态（来自 Lua 脚本的 context.is_composing）
     * 回退到 JNA 检测（当文件状态不可用时）
     * 返回 true 时表示正在中文输入，此时应跳过自动切换
     * 返回 false 时表示：未输入、或英文输入（可安全切换）
     */
    fun isComposing(): Boolean {
        // 优先使用文件状态（最准确，来自 Rime Lua 脚本）
        return try {
            val watcher = ApplicationManager.getApplication()
                .getService(RimeStateFileWatcher::class.java)
            if (watcher != null) {
                val composing = watcher.isComposing
                thisLogger().debug("isComposing from file watcher: $composing")
                composing
            } else {
                // 回退到 JNA 检测
                thisLogger().debug("RimeStateFileWatcher not available, falling back to JNA detection")
                isComposingViaJna()
            }
        } catch (e: Exception) {
            thisLogger().debug("Failed to get composing state from watcher: ${e.message}, falling back to JNA")
            isComposingViaJna()
        }
    }

    /**
     * 通过 JNA 检测 IME composing 状态（回退方案）
     */
    private fun isComposingViaJna(): Boolean {
        val user32 = MyUser32.INSTANCE
        val imm32 = Imm32.INSTANCE

        if (user32 == null || imm32 == null) {
            return false
        }

        return try {
            val fgWindow = user32.GetForegroundWindow()
            val imeWnd = imm32.ImmGetDefaultIMEWnd(fgWindow)

            if (Pointer.nativeValue(imeWnd.pointer) == 0L) {
                return false
            }

            // 先检查 IME 是否打开（正在输入）
            val openResult = user32.SendMessageW(
                imeWnd, WM_IME_CONTROL, WPARAM(IMC_GETOPENSTATUS.toLong()), LPARAM(0L)
            )
            val isOpen = openResult.toLong() != 0L
            if (!isOpen) {
                return false
            }

            // IME 打开时，进一步检查是否为中文模式
            // bit0 = 1 表示中文模式（IME_CMODE_NATIVE），bit0 = 0 表示英文/ASCII 模式
            val modeResult = user32.SendMessageW(
                imeWnd, WM_IME_CONTROL, WPARAM(IMC_GETCONVERSIONMODE.toLong()), LPARAM(0L)
            )
            val modeVal = modeResult.toLong()
            val isChineseMode = (modeVal and 0x01L) != 0L

            if (isChineseMode) {
                thisLogger().debug("IME is composing in Chinese mode (skip switch)")
            } else {
                thisLogger().debug("IME is composing in English/ASCII mode (safe to switch)")
            }

            // 只有中文 composing 时才跳过切换
            isChineseMode
        } catch (e: Exception) {
            thisLogger().debug("Failed to detect IME composing state via JNA: ${e.message}")
            false
        }
    }

    fun getCurrentState(): ImeState {
        val user32 = MyUser32.INSTANCE
        val imm32 = Imm32.INSTANCE

        if (user32 == null || imm32 == null) {
            thisLogger().debug("JNA library not available, falling back to tracked state")
            return getTrackedStateFallback()
        }

        return try {
            val fgWindow = user32.GetForegroundWindow()
            val imeWnd = imm32.ImmGetDefaultIMEWnd(fgWindow)

            // TSF 应用（如 IntelliJ）下 ImmGetDefaultIMEWnd 可能返回 NULL HWND
            if (Pointer.nativeValue(imeWnd.pointer) == 0L) {
                thisLogger().debug("ImmGetDefaultIMEWnd returned NULL (TSF app detected), falling back to tracked state")
                return getTrackedStateFallback()
            }

            val mode = user32.SendMessageW(
                imeWnd, WM_IME_CONTROL, WPARAM(IMC_GETCONVERSIONMODE.toLong()), LPARAM(0L)
            )
            val modeVal = mode.toLong()

            // SendMessageW 返回 0 通常表示窗口不处理该消息（TSF 应用特征）
            if (modeVal == 0L) {
                thisLogger().debug("SendMessageW returned 0 (TSF app detected), falling back to tracked state")
                return getTrackedStateFallback()
            }

            // bit0 = 0 表示 ASCII 模式（英文），bit0 = 1 表示中文（IME_CMODE_NATIVE）
            val isAsciiMode = (modeVal and 0x01L) == 0L

            thisLogger().debug("IME conversion mode: 0x${modeVal.toString(16)} -> isAscii=$isAsciiMode")

            val isCapsLock = (user32.GetKeyState(VK_CAPITAL).toInt() and 0x01) != 0

            ImeState(isAsciiMode, isCapsLock)
        } catch (e: Exception) {
            thisLogger().debug("IME state detection failed: ${e.message}, falling back to tracked state")
            getTrackedStateFallback()
        }
    }

    /**
     * 回退方案：使用 RimeController 的内部跟踪状态
     * 由于 TSF 应用不响应 IMM32 API，这是更可靠的状态来源
     */
    private fun getTrackedStateFallback(): ImeState {
        return try {
            val controller = ApplicationManager.getApplication()
                .getService(com.rimevim.ime.RimeController::class.java)
            controller?.getTrackedState() ?: ImeState(isAsciiMode = true, isCapsLock = false)
        } catch (e: Exception) {
            thisLogger().debug("Failed to get tracked state: ${e.message}")
            ImeState(isAsciiMode = true, isCapsLock = false)
        }
    }
}
