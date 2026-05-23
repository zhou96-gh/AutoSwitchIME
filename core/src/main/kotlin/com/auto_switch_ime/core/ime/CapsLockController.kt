package com.auto_switch_ime.core.ime

import com.sun.jna.Native
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.WORD
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary

/**
 * Windows CapsLock 控制
 * 使用 SendInput API 模拟 CapsLock 按键
 */
object CapsLockController {

    private const val VK_CAPITAL: Int = 0x14
    private const val KEYEVENTF_KEYUP: Int = 0x0002

    interface MyUser32 : StdCallLibrary {
        companion object {
            val INSTANCE: MyUser32 = Native.load("user32", MyUser32::class.java)
        }

        fun GetKeyState(nVirtKey: Int): Short
        fun SendInput(nInputs: DWORD, pInputs: Array<WinUser.INPUT>, cbSize: Int): DWORD
    }

    /**
     * 切换 CapsLock 状态（按下+释放）
     * @return 实际成功注入的事件数（应为 2）
     */
    fun toggleCapsLock(): Int {
        val input = WinUser.INPUT()

        // 设置输入类型
        input.type = DWORD(WinUser.INPUT.INPUT_KEYBOARD.toLong())
        input.input.setType("ki")  // 必须！否则 Union 不生效

        // 初始化公共字段
        input.input.ki.wVk = WORD(VK_CAPITAL.toLong())
        input.input.ki.wScan = WORD(0)
        input.input.ki.time = DWORD(0L)
        input.input.ki.dwExtraInfo = BaseTSD.ULONG_PTR(0)

        // Key Down
        input.input.ki.dwFlags = DWORD(0L)
        val inputs = arrayOf(input)
        val downResult = MyUser32.INSTANCE.SendInput(DWORD(1L), inputs, input.size())

        // Key Up
        input.input.ki.dwFlags = DWORD(KEYEVENTF_KEYUP.toLong())
        val upResult = MyUser32.INSTANCE.SendInput(DWORD(1L), inputs, input.size())

        return downResult.toInt() + upResult.toInt()
    }

    /**
     * 将 CapsLock 设置为指定状态（智能切换）
     */
    fun setCapsLock(on: Boolean) {
        val currentState = isCapsLockOn()
        if (currentState == on) return
        toggleCapsLock()
    }

    /**
     * 检测当前 CapsLock 状态
     */
    fun isCapsLockOn(): Boolean {
        val state = MyUser32.INSTANCE.GetKeyState(VK_CAPITAL)
        return (state.toInt() and 0x0001) != 0
    }
}
