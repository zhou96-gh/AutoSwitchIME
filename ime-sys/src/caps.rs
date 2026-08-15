use std::mem::{zeroed, size_of};
use std::sync::atomic::{AtomicBool, AtomicI32, Ordering};
use windows_sys::Win32::UI::Input::KeyboardAndMouse::{
    GetAsyncKeyState, GetKeyState, SendInput, INPUT, INPUT_KEYBOARD, KEYEVENTF_KEYUP, VK_CAPITAL,
};


/// CapsLock 开关跟踪计数器（-1 未初始化）
static CAPS_TOGGLE: AtomicI32 = AtomicI32::new(-1);

/// 上次 GetAsyncKeyState & 0x8000 的键按下状态
static PREV_KEY_DOWN: AtomicBool = AtomicBool::new(false);

/// 上次 GetKeyState 读数（用于检测 GetKeyState 是否真实更新过）
/// 非 GUI 线程（如 VSCode 扩展宿主）GetKeyState 始终返回 0，
/// 不能与 CAPS_TOGGLE 直接比较，否则会覆盖 toggle 设置的计数器。
static LAST_GKS: AtomicI32 = AtomicI32::new(-1);

/// 读取物理 CapsLock 开关态
///
/// 检测策略（双保险）：
/// 1. `GetKeyState & 1` — GUI 线程（IDE）有消息队列时精确
/// 2. `GetAsyncKeyState & 0x8000` 下降沿检测 — 非 GUI 线程也可靠
///
/// 关键：GetKeyState 只在与上次读数不同时才采用（证明是 GUI 线程），
/// 否则用键按下边沿翻转内部计数器，避免非 GUI 线程的 GetKeyState=0 覆盖。
#[no_mangle]
pub extern "C" fn ime_caps_read() -> i32 {
    let vk = VK_CAPITAL.into();
    let gks = unsafe { GetKeyState(vk) as i32 & 1 };
    let key_down = unsafe { (GetAsyncKeyState(vk) as i32 & 0x8000) != 0 };
    let prev = CAPS_TOGGLE.load(Ordering::Relaxed);
    let prev_gks = LAST_GKS.load(Ordering::Relaxed);

    if prev < 0 {
        // 首次初始化：用 GetKeyState
        CAPS_TOGGLE.store(gks, Ordering::SeqCst);
        PREV_KEY_DOWN.store(key_down, Ordering::SeqCst);
        LAST_GKS.store(gks, Ordering::SeqCst);
        return gks;
    }

    // GetKeyState 相较上次读数有变化 → GUI 线程已处理键盘消息 → 以此为准
    // 注意：这里必须与 LAST_GKS（上次 GetKeyState 读数）比较，
    // 不能与 CAPS_TOGGLE（计数器）比较。非 GUI 线程 GetKeyState 始终返回 0，
    // 若与计数器比较会误判为"有更新"而覆盖 toggle 刚设的值。
    if prev_gks >= 0 && gks != prev_gks {
        CAPS_TOGGLE.store(gks, Ordering::SeqCst);
        PREV_KEY_DOWN.store(key_down, Ordering::SeqCst);
        LAST_GKS.store(gks, Ordering::SeqCst);
        return gks;
    }
    LAST_GKS.store(gks, Ordering::SeqCst);

    // 非 GUI 线程：GetKeyState 不更新，改用下降沿检测
    let prev_down = PREV_KEY_DOWN.swap(key_down, Ordering::SeqCst);
    if key_down && !prev_down {
        CAPS_TOGGLE.fetch_xor(1, Ordering::SeqCst);
    }

    CAPS_TOGGLE.load(Ordering::SeqCst)
}

fn send_caps_lock_key() -> bool {
    unsafe {
        let mut down: INPUT = zeroed();
        down.r#type = INPUT_KEYBOARD;
        down.Anonymous.ki.wVk = VK_CAPITAL;

        let mut up: INPUT = zeroed();
        up.r#type = INPUT_KEYBOARD;
        up.Anonymous.ki.wVk = VK_CAPITAL;
        up.Anonymous.ki.dwFlags = KEYEVENTF_KEYUP;

        let mut inputs = [down, up];
        SendInput(2, inputs.as_mut_ptr(), size_of::<INPUT>() as i32) == 2
    }
}

fn wait_for_injected_key() {
    std::thread::sleep(std::time::Duration::from_millis(30));
}

/// 物理开关 CapsLock，返回新状态；注入失败时返回原状态。
#[no_mangle]
pub extern "C" fn ime_caps_toggle() -> i32 {
    let current = ime_caps_read();
    if send_caps_lock_key() {
        wait_for_injected_key();
        ime_caps_read()
    } else {
        current
    }
}

/// 设置 CapsLock 到指定状态，返回 1 表示已处于目标状态或注入成功，0 表示注入失败。
#[no_mangle]
pub extern "C" fn ime_caps_set(on: i32) -> i32 {
    let target = if on != 0 { 1 } else { 0 };
    if ime_caps_read() == target {
        return 1;
    }
    if !send_caps_lock_key() {
        return 0;
    }
    wait_for_injected_key();
    1
}

/// 在处理 Windows 键盘消息的 GUI 线程调用，读取真实 CapsLock toggle 位并同步跟踪器。
#[no_mangle]
pub extern "C" fn ime_caps_message_state() -> i32 {
    let vk = VK_CAPITAL.into();
    let state = unsafe { GetKeyState(vk) as i32 & 1 };
    let key_down = unsafe { (GetAsyncKeyState(vk) as i32 & 0x8000) != 0 };
    CAPS_TOGGLE.store(state, Ordering::SeqCst);
    PREV_KEY_DOWN.store(key_down, Ordering::SeqCst);
    LAST_GKS.store(state, Ordering::SeqCst);
    state
}
