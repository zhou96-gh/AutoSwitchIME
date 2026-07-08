use windows_sys::Win32::UI::Input::Ime::{
    ImmGetContext, ImmGetCompositionStringW, ImmReleaseContext, GCS_COMPSTR,
};
use windows_sys::Win32::UI::WindowsAndMessaging::GetForegroundWindow;

/// 获取前台窗口句柄（后续可用于 IME 窗口检测占位）
/// 返回窗口句柄，0 表示失败
#[no_mangle]
pub extern "C" fn ime_foreground_window() -> isize {
    unsafe { GetForegroundWindow() as isize }
}

/// 检测前台窗口 IME 是否正在 composition（输入法中）
///
/// 使用 IMM32 API 直接查询：
/// 1. 取前台窗口句柄
/// 2. 取该窗口的 IME 上下文
/// 3. ImmGetCompositionStringW(GCS_COMPSTR) > 0 → composing 中
///
/// 返回值：
///   - 1  正在 composition
///   - 0  不在 composition
///   - -1 IMM32 不可用（TSF 应用等），由调用方 fallback
#[no_mangle]
pub extern "C" fn ime_is_composing() -> i32 {
    unsafe {
        let hwnd = GetForegroundWindow();
        if hwnd.is_null() {
            return -1;
        }

        let himc = ImmGetContext(hwnd);
        if himc.is_null() {
            return -1;
        }

        let len = ImmGetCompositionStringW(himc, GCS_COMPSTR, std::ptr::null_mut(), 0);
        ImmReleaseContext(hwnd, himc);

        if len > 0 { 1 } else { 0 }
    }
}
