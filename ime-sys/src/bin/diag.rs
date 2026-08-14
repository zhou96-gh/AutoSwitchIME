fn main() {
    let raw = ime_sys::ime_get_conversion_status();
    let caps = ime_sys::ime_caps_read() != 0;
    let composing = ime_sys::ime_is_composing();

    println!("=== AutoSwitchIME system IME diagnostic ===");
    match ime_sys::decode_conversion_status(raw) {
        Some(status) => {
            println!("available: true");
            println!("open: {}", status.is_open);
            println!("ascii_mode: {}", status.is_ascii_mode);
            println!("conversion_mode: 0x{:08X}", status.conversion_mode);
        }
        None => {
            println!("available: false");
            println!("error_code: {}", raw);
        }
    }
    println!("caps_lock: {}", caps);
    println!("is_composing: {}", composing);
}
