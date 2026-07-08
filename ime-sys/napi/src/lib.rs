use napi_derive::napi;

#[napi]
pub fn caps_read() -> bool {
    ime_sys::ime_caps_read()
}

#[napi]
pub fn caps_toggle() -> bool {
    ime_sys::ime_caps_toggle()
}

#[napi]
pub fn caps_set(on: bool) -> bool {
    ime_sys::ime_caps_set(on)
}
