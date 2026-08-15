use std::ffi::c_void;
use std::mem::size_of;
use std::sync::atomic::{AtomicU32, AtomicU64, Ordering};
use std::sync::{Mutex, OnceLock};
use windows_sys::Win32::Foundation::{
    CloseHandle, HANDLE, INVALID_HANDLE_VALUE, WAIT_TIMEOUT,
};
use windows_sys::Win32::Storage::FileSystem::SYNCHRONIZE;
use windows_sys::Win32::System::LibraryLoader::{GetModuleHandleW, GetProcAddress};
use windows_sys::Win32::System::Memory::{
    CreateFileMappingW, MapViewOfFile, OpenFileMappingW, UnmapViewOfFile, FILE_MAP_READ,
    FILE_MAP_WRITE, PAGE_READWRITE,
};
use windows_sys::Win32::System::Threading::{
    CreateEventW, GetCurrentProcessId, OpenEventW, OpenProcess, ResetEvent, SetEvent,
    WaitForSingleObject,
};
use windows_sys::Win32::UI::WindowsAndMessaging::{
    GetForegroundWindow, GetWindowThreadProcessId,
};

const MAPPING_NAME: &[u16] = &[
    'L' as u16, 'o' as u16, 'c' as u16, 'a' as u16, 'l' as u16, '\\' as u16,
    'A' as u16, 'u' as u16, 't' as u16, 'o' as u16, 'S' as u16, 'w' as u16,
    'i' as u16, 't' as u16, 'c' as u16, 'h' as u16, 'I' as u16, 'M' as u16,
    'E' as u16, '.' as u16, 'R' as u16, 'i' as u16, 'm' as u16, 'e' as u16,
    'S' as u16, 't' as u16, 'a' as u16, 't' as u16, 'e' as u16, '.' as u16,
    'v' as u16, '1' as u16, 0,
];
const CHANGE_EVENT_NAME: &[u16] = &[
    'L' as u16, 'o' as u16, 'c' as u16, 'a' as u16, 'l' as u16, '\\' as u16,
    'A' as u16, 'u' as u16, 't' as u16, 'o' as u16, 'S' as u16, 'w' as u16,
    'i' as u16, 't' as u16, 'c' as u16, 'h' as u16, 'I' as u16, 'M' as u16,
    'E' as u16, '.' as u16, 'R' as u16, 'i' as u16, 'm' as u16, 'e' as u16,
    'S' as u16, 't' as u16, 'a' as u16, 't' as u16, 'e' as u16, 'C' as u16,
    'h' as u16, 'a' as u16, 'n' as u16, 'g' as u16, 'e' as u16, 'd' as u16,
    '.' as u16, 'v' as u16, '1' as u16, 0,
];
const MAGIC: u32 = u32::from_le_bytes(*b"ASIM");
const PROTOCOL_VERSION: u32 = 1;
const FLAG_ASCII_MODE: u32 = 1 << 0;
const FLAG_COMPOSING: u32 = 1 << 1;
const EVENT_SEQUENCE_MASK: u64 = (1_u64 << 61) - 1;

pub const RIME_STATE_UNAVAILABLE: i64 = -1;
pub const RIME_STATE_INVALID: i64 = -2;
pub const RIME_STATE_FOREGROUND_MISMATCH: i64 = -3;
pub const RIME_STATE_INCONSISTENT: i64 = -4;
pub const RIME_STATE_WRITER_EXITED: i64 = -5;

#[repr(C, align(8))]
struct SharedRimeState {
    sequence: AtomicU32,
    magic: u32,
    protocol_version: u32,
    writer_process_id: u32,
    foreground_process_id: u32,
    flags: u32,
    reserved_u32: u32,
    foreground_window: u64,
    event_sequence: u64,
    reserved_u64: [u64; 2],
}

struct WriterMapping {
    handle: HANDLE,
    change_event: HANDLE,
    state: *mut SharedRimeState,
}

unsafe impl Send for WriterMapping {}
unsafe impl Sync for WriterMapping {}

impl Drop for WriterMapping {
    fn drop(&mut self) {
        unsafe {
            UnmapViewOfFile(windows_sys::Win32::System::Memory::MEMORY_MAPPED_VIEW_ADDRESS {
                Value: self.state.cast(),
            });
            CloseHandle(self.change_event);
            CloseHandle(self.handle);
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct RimeInputState {
    pub is_ascii_mode: bool,
    pub is_composing: bool,
    pub event_sequence: u64,
}

#[derive(Clone, Copy)]
struct StateSnapshot {
    writer_process_id: u32,
    foreground_process_id: u32,
    flags: u32,
    foreground_window: u64,
    event_sequence: u64,
}

type LuaToBoolean = unsafe extern "system" fn(*mut c_void, i32) -> i32;
type LuaPushBoolean = unsafe extern "system" fn(*mut c_void, i32);

#[derive(Clone, Copy)]
struct LuaApi {
    to_boolean: LuaToBoolean,
    push_boolean: LuaPushBoolean,
}

static WRITER_MAPPING: Mutex<Option<WriterMapping>> = Mutex::new(None);
static LUA_API: OnceLock<Option<LuaApi>> = OnceLock::new();
static EVENT_SEQUENCE: AtomicU64 = AtomicU64::new(0);

/// Rime Lua 扩展入口：接收 `(ascii_mode, composing)` 并写入命名共享内存。
///
/// Lua 侧通过 `package.loadlib(..., "autoswitchime_publish")` 加载本函数。
#[no_mangle]
pub extern "C" fn autoswitchime_publish(lua_state: *mut c_void) -> i32 {
    if lua_state.is_null() {
        return 0;
    }

    let Some(api) = lua_api() else {
        return 0;
    };
    let ascii_mode = unsafe { (api.to_boolean)(lua_state, 1) != 0 };
    let composing = unsafe { (api.to_boolean)(lua_state, 2) != 0 };
    let published = publish_rime_state(ascii_mode, composing);
    unsafe { (api.push_boolean)(lua_state, i32::from(published)) };
    1
}

/// 读取与当前前台窗口匹配的 Rime 状态。
///
/// 成功返回 bit 0=`ascii_mode`、bit 1=`composing`，负数表示数据不可用。
#[no_mangle]
pub extern "C" fn ime_rime_state_status() -> i64 {
    read_rime_state_status()
}

/// 等待 Rime 状态变化。返回 1=变化、0=超时、负数=通知源不可用。
#[no_mangle]
pub extern "C" fn ime_rime_state_wait(timeout_ms: u32) -> i32 {
    wait_for_rime_state_change(timeout_ms)
}

pub fn decode_rime_state_status(packed: i64) -> Option<RimeInputState> {
    if packed < 0 {
        return None;
    }
    Some(RimeInputState {
        is_ascii_mode: packed & i64::from(FLAG_ASCII_MODE) != 0,
        is_composing: packed & i64::from(FLAG_COMPOSING) != 0,
        event_sequence: (packed as u64) >> 2,
    })
}

fn publish_rime_state(ascii_mode: bool, composing: bool) -> bool {
    let Ok(mut writer_mapping) = WRITER_MAPPING.lock() else {
        return false;
    };
    if writer_mapping.is_none() {
        *writer_mapping = create_writer_mapping();
    }
    let Some(mapping) = writer_mapping.as_ref() else {
        return false;
    };

    unsafe {
        let foreground_window = GetForegroundWindow();
        let mut foreground_process_id = 0;
        if !foreground_window.is_null() {
            GetWindowThreadProcessId(foreground_window, &mut foreground_process_id);
        }

        let state = &*mapping.state;
        let current_sequence = state.sequence.load(Ordering::Relaxed);
        let writing_sequence = current_sequence.wrapping_add(1) | 1;
        state.sequence.store(writing_sequence, Ordering::Release);

        std::ptr::write_volatile(&raw mut (*mapping.state).magic, MAGIC);
        std::ptr::write_volatile(
            &raw mut (*mapping.state).protocol_version,
            PROTOCOL_VERSION,
        );
        std::ptr::write_volatile(
            &raw mut (*mapping.state).writer_process_id,
            GetCurrentProcessId(),
        );
        std::ptr::write_volatile(
            &raw mut (*mapping.state).foreground_process_id,
            foreground_process_id,
        );
        std::ptr::write_volatile(
            &raw mut (*mapping.state).flags,
            flags_for(ascii_mode, composing),
        );
        std::ptr::write_volatile(
            &raw mut (*mapping.state).foreground_window,
            foreground_window as usize as u64,
        );
        std::ptr::write_volatile(
            &raw mut (*mapping.state).event_sequence,
            EVENT_SEQUENCE.fetch_add(1, Ordering::Relaxed).wrapping_add(1),
        );

        state
            .sequence
            .store(writing_sequence.wrapping_add(1), Ordering::Release);
    }
    unsafe { SetEvent(mapping.change_event) != 0 }
}

fn wait_for_rime_state_change(timeout_ms: u32) -> i32 {
    unsafe {
        let event = OpenEventW(SYNCHRONIZE, 0, CHANGE_EVENT_NAME.as_ptr());
        if event.is_null() {
            return -1;
        }
        let result = WaitForSingleObject(event, timeout_ms);
        if result == 0 {
            // Manual-reset broadcasts to every current reader. The shared state stores the
            // latest sequence, so coalescing a concurrent notification cannot lose state.
            ResetEvent(event);
        }
        CloseHandle(event);
        if result == 0 {
            1
        } else if result == WAIT_TIMEOUT {
            0
        } else {
            -2
        }
    }
}

fn read_rime_state_status() -> i64 {
    unsafe {
        let handle = OpenFileMappingW(FILE_MAP_READ, 0, MAPPING_NAME.as_ptr());
        if handle.is_null() {
            return RIME_STATE_UNAVAILABLE;
        }
        let view = MapViewOfFile(handle, FILE_MAP_READ, 0, 0, size_of::<SharedRimeState>());
        if view.Value.is_null() {
            CloseHandle(handle);
            return RIME_STATE_UNAVAILABLE;
        }

        let result = read_snapshot(view.Value.cast());
        UnmapViewOfFile(view);
        CloseHandle(handle);

        let snapshot = match result {
            Ok(snapshot) => snapshot,
            Err(error) => return error,
        };
        if snapshot.writer_process_id == 0 || !process_is_alive(snapshot.writer_process_id) {
            return RIME_STATE_WRITER_EXITED;
        }

        let foreground_window = GetForegroundWindow();
        if foreground_window.is_null()
            || foreground_window as usize as u64 != snapshot.foreground_window
        {
            return RIME_STATE_FOREGROUND_MISMATCH;
        }
        let mut foreground_process_id = 0;
        GetWindowThreadProcessId(foreground_window, &mut foreground_process_id);
        if foreground_process_id == 0 || foreground_process_id != snapshot.foreground_process_id {
            return RIME_STATE_FOREGROUND_MISMATCH;
        }

        let flags = u64::from(snapshot.flags & (FLAG_ASCII_MODE | FLAG_COMPOSING));
        (((snapshot.event_sequence & EVENT_SEQUENCE_MASK) << 2) | flags) as i64
    }
}

unsafe fn read_snapshot(state: *const SharedRimeState) -> Result<StateSnapshot, i64> {
    for _ in 0..5 {
        let before = (*state).sequence.load(Ordering::Acquire);
        if before & 1 != 0 {
            std::hint::spin_loop();
            continue;
        }

        let magic = std::ptr::read_volatile(&raw const (*state).magic);
        let protocol_version =
            std::ptr::read_volatile(&raw const (*state).protocol_version);
        let snapshot = StateSnapshot {
            writer_process_id: std::ptr::read_volatile(
                &raw const (*state).writer_process_id,
            ),
            foreground_process_id: std::ptr::read_volatile(
                &raw const (*state).foreground_process_id,
            ),
            flags: std::ptr::read_volatile(&raw const (*state).flags),
            foreground_window: std::ptr::read_volatile(
                &raw const (*state).foreground_window,
            ),
            event_sequence: std::ptr::read_volatile(
                &raw const (*state).event_sequence,
            ),
        };
        let after = (*state).sequence.load(Ordering::Acquire);

        if before == after && after & 1 == 0 {
            return if magic == MAGIC && protocol_version == PROTOCOL_VERSION {
                Ok(snapshot)
            } else {
                Err(RIME_STATE_INVALID)
            };
        }
    }
    Err(RIME_STATE_INCONSISTENT)
}

fn create_writer_mapping() -> Option<WriterMapping> {
    unsafe {
        let handle = CreateFileMappingW(
            INVALID_HANDLE_VALUE,
            std::ptr::null(),
            PAGE_READWRITE,
            0,
            size_of::<SharedRimeState>() as u32,
            MAPPING_NAME.as_ptr(),
        );
        if handle.is_null() {
            return None;
        }
        let view = MapViewOfFile(
            handle,
            FILE_MAP_READ | FILE_MAP_WRITE,
            0,
            0,
            size_of::<SharedRimeState>(),
        );
        if view.Value.is_null() {
            CloseHandle(handle);
            return None;
        }

        let change_event = CreateEventW(
            std::ptr::null(),
            1,
            0,
            CHANGE_EVENT_NAME.as_ptr(),
        );
        if change_event.is_null() {
            UnmapViewOfFile(view);
            CloseHandle(handle);
            return None;
        }

        let state = view.Value.cast::<SharedRimeState>();
        std::ptr::write_bytes(state.cast::<u8>(), 0, size_of::<SharedRimeState>());
        Some(WriterMapping {
            handle,
            change_event,
            state,
        })
    }
}

fn lua_api() -> Option<LuaApi> {
    *LUA_API.get_or_init(|| unsafe {
        let module_name: Vec<u16> = "rime.dll\0".encode_utf16().collect();
        let module = GetModuleHandleW(module_name.as_ptr());
        if module.is_null() {
            return None;
        }
        let to_boolean = GetProcAddress(module, b"lua_toboolean\0".as_ptr());
        let push_boolean = GetProcAddress(module, b"lua_pushboolean\0".as_ptr());
        match (to_boolean, push_boolean) {
            (Some(to_boolean), Some(push_boolean)) => Some(LuaApi {
                to_boolean: std::mem::transmute(to_boolean),
                push_boolean: std::mem::transmute(push_boolean),
            }),
            _ => None,
        }
    })
}

unsafe fn process_is_alive(process_id: u32) -> bool {
    let process = OpenProcess(SYNCHRONIZE, 0, process_id);
    if process.is_null() {
        return false;
    }
    let result = WaitForSingleObject(process, 0) == WAIT_TIMEOUT;
    CloseHandle(process);
    result
}

fn flags_for(ascii_mode: bool, composing: bool) -> u32 {
    u32::from(ascii_mode) * FLAG_ASCII_MODE + u32::from(composing) * FLAG_COMPOSING
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn decodes_rime_flags() {
        assert_eq!(
            decode_rime_state_status(0),
            Some(RimeInputState {
                is_ascii_mode: false,
                is_composing: false,
                event_sequence: 0,
            })
        );
        assert_eq!(
            decode_rime_state_status((17 << 2) | 3),
            Some(RimeInputState {
                is_ascii_mode: true,
                is_composing: true,
                event_sequence: 17,
            })
        );
        assert_eq!(decode_rime_state_status(RIME_STATE_UNAVAILABLE), None);
    }

    #[test]
    fn packs_rime_flags() {
        assert_eq!(flags_for(false, false), 0);
        assert_eq!(flags_for(true, false), FLAG_ASCII_MODE);
        assert_eq!(flags_for(false, true), FLAG_COMPOSING);
        assert_eq!(flags_for(true, true), FLAG_ASCII_MODE | FLAG_COMPOSING);
    }
}
