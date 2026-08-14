use std::mem::{size_of, zeroed};
use windows_sys::Win32::Foundation::HWND;
use windows_sys::Win32::UI::Input::Ime::{
    ImmGetCompositionStringW, ImmGetContext, ImmGetDefaultIMEWnd, ImmReleaseContext, GCS_COMPSTR,
};
use windows_sys::Win32::UI::WindowsAndMessaging::{
    GetForegroundWindow, GetGUIThreadInfo, GetWindowThreadProcessId, SendMessageTimeoutW,
    GUITHREADINFO,
};

const WM_IME_CONTROL: u32 = 0x0283;
const IMC_GETCONVERSIONMODE: usize = 0x0001;
const IMC_SETCONVERSIONMODE: usize = 0x0002;
const IMC_GETOPENSTATUS: usize = 0x0005;
const IMC_SETOPENSTATUS: usize = 0x0006;
const SMTO_ABORTIFHUNG: u32 = 0x0002;
const IME_STATUS_OPEN_BIT: i64 = 1_i64 << 32;
const IME_CMODE_NATIVE: u32 = 0x0001;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct SystemImeStatus {
    pub is_open: bool,
    pub is_ascii_mode: bool,
    pub conversion_mode: u32,
}

/// 获取前台窗口句柄（后续可用于 IME 窗口检测占位）
/// 返回窗口句柄，0 表示失败
#[no_mangle]
pub extern "C" fn ime_foreground_window() -> isize {
    unsafe { GetForegroundWindow() as isize }
}

/// 获取前台窗口所属进程 ID，0 表示无法获取。
#[no_mangle]
pub extern "C" fn ime_foreground_process_id() -> u32 {
    unsafe {
        let hwnd = GetForegroundWindow();
        if hwnd.is_null() {
            return 0;
        }

        let mut process_id = 0;
        GetWindowThreadProcessId(hwnd, &mut process_id);
        process_id
    }
}

/// 读取前台线程的系统 IME 转换状态。
///
/// 成功时低 32 位为 IME conversion flags，第 32 位表示 IME open。
/// `IME_CMODE_NATIVE`（bit 0）为 1 表示中文，为 0 表示英文。
/// 返回负数表示查询不可用；英文 conversion flags 为 0 仍是成功结果。
#[no_mangle]
pub extern "C" fn ime_get_conversion_status() -> i64 {
    unsafe {
        let foreground = GetForegroundWindow();
        if foreground.is_null() {
            return -1;
        }

        let ime_window = ImmGetDefaultIMEWnd(foreground);
        if ime_window.is_null() {
            return -2;
        }

        let open = match query_ime_control(ime_window, IMC_GETOPENSTATUS) {
            Some(value) => value != 0,
            None => return -3,
        };
        let conversion = match query_ime_control(ime_window, IMC_GETCONVERSIONMODE) {
            Some(value) => value as u32,
            None => return -4,
        };

        pack_conversion_status(open, conversion)
    }
}

/// 通过前台窗口的系统 IME 上下文切换中英文状态。
///
/// `ascii != 0` 切换到英文，`ascii == 0` 切换到本地语言模式。
/// 返回 1 表示回读状态符合目标，0 表示系统接口不可用或切换失败。
#[no_mangle]
pub extern "C" fn ime_set_ascii_mode(ascii: i32) -> i32 {
    unsafe {
        let foreground = GetForegroundWindow();
        if foreground.is_null() {
            return 0;
        }

        let ime_window = ImmGetDefaultIMEWnd(foreground);
        if ime_window.is_null() {
            return 0;
        }

        let open = match query_ime_control(ime_window, IMC_GETOPENSTATUS) {
            Some(value) => value != 0,
            None => return 0,
        };
        let conversion = match query_ime_control(ime_window, IMC_GETCONVERSIONMODE) {
            Some(value) => value as u32,
            None => return 0,
        };
        let target_ascii = ascii != 0;

        if target_ascii && !open {
            return 1;
        }
        if !open && !send_ime_control(ime_window, IMC_SETOPENSTATUS, 1) {
            return 0;
        }
        let target_conversion = conversion_mode_for_ascii(conversion, target_ascii);
        if !send_ime_control(
            ime_window,
            IMC_SETCONVERSIONMODE,
            target_conversion as isize,
        ) {
            return 0;
        }

        let actual_open = match query_ime_control(ime_window, IMC_GETOPENSTATUS) {
            Some(value) => value != 0,
            None => return 0,
        };
        let actual_conversion = match query_ime_control(ime_window, IMC_GETCONVERSIONMODE) {
            Some(value) => value as u32,
            None => return 0,
        };
        let actual_ascii = !actual_open || actual_conversion & IME_CMODE_NATIVE == 0;
        i32::from(actual_ascii == target_ascii)
    }
}

pub fn decode_conversion_status(packed: i64) -> Option<SystemImeStatus> {
    if packed < 0 {
        return None;
    }
    let conversion_mode = packed as u32;
    let is_open = packed & IME_STATUS_OPEN_BIT != 0;
    Some(SystemImeStatus {
        is_open,
        is_ascii_mode: !is_open || conversion_mode & 0x01 == 0,
        conversion_mode,
    })
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
        let foreground = GetForegroundWindow();
        if foreground.is_null() {
            return -1;
        }

        let hwnd = focused_input_window(foreground);
        if hwnd.is_null() {
            return -1;
        }

        let himc = ImmGetContext(hwnd);
        if himc.is_null() {
            return -1;
        }

        let len = ImmGetCompositionStringW(himc, GCS_COMPSTR, std::ptr::null_mut(), 0);
        ImmReleaseContext(hwnd, himc);

        if len > 0 {
            1
        } else {
            0
        }
    }
}

fn pack_conversion_status(open: bool, conversion: u32) -> i64 {
    i64::from(conversion) | if open { IME_STATUS_OPEN_BIT } else { 0 }
}

unsafe fn query_ime_control(ime_window: HWND, command: usize) -> Option<usize> {
    let mut result = 0;
    let succeeded = SendMessageTimeoutW(
        ime_window,
        WM_IME_CONTROL,
        command,
        0,
        SMTO_ABORTIFHUNG,
        100,
        &mut result,
    );
    (succeeded != 0).then_some(result)
}

unsafe fn send_ime_control(ime_window: HWND, command: usize, value: isize) -> bool {
    let mut result = 0;
    SendMessageTimeoutW(
        ime_window,
        WM_IME_CONTROL,
        command,
        value,
        SMTO_ABORTIFHUNG,
        100,
        &mut result,
    ) != 0
}

fn conversion_mode_for_ascii(conversion: u32, ascii: bool) -> u32 {
    if ascii {
        conversion & !IME_CMODE_NATIVE
    } else {
        conversion | IME_CMODE_NATIVE
    }
}

unsafe fn focused_input_window(foreground: HWND) -> HWND {
    let thread_id = GetWindowThreadProcessId(foreground, std::ptr::null_mut());
    if thread_id == 0 {
        return foreground;
    }

    let mut info: GUITHREADINFO = zeroed();
    info.cbSize = size_of::<GUITHREADINFO>() as u32;
    if GetGUIThreadInfo(thread_id, &mut info) != 0 && !info.hwndFocus.is_null() {
        info.hwndFocus
    } else {
        foreground
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn zero_conversion_is_a_valid_closed_or_open_status() {
        assert_eq!(pack_conversion_status(false, 0), 0);
        assert_eq!(pack_conversion_status(true, 0), IME_STATUS_OPEN_BIT);
    }

    #[test]
    fn preserves_all_conversion_flags() {
        let packed = pack_conversion_status(true, 0x0000_0781);
        assert_eq!(packed & 0xffff_ffff, 0x0000_0781);
        assert_ne!(packed & IME_STATUS_OPEN_BIT, 0);
    }

    #[test]
    fn decodes_zero_as_valid_ascii_mode() {
        assert_eq!(
            decode_conversion_status(0),
            Some(SystemImeStatus {
                is_open: false,
                is_ascii_mode: true,
                conversion_mode: 0,
            })
        );
    }

    #[test]
    fn ascii_switch_only_changes_native_flag() {
        assert_eq!(conversion_mode_for_ascii(0x0781, true), 0x0780);
        assert_eq!(conversion_mode_for_ascii(0x0780, false), 0x0781);
    }
}
