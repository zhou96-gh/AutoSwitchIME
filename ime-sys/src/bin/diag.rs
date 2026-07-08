use std::env;
use std::fs;
use std::path::PathBuf;

fn state_file_path() -> PathBuf {
    let temp = env::var("TEMP").unwrap_or_else(|_| "C:\\Windows\\Temp".into());
    let mut p = PathBuf::from(temp);
    p.push("ime-state-rime.json");
    p
}

fn read_state_file() -> String {
    let path = state_file_path();
    match fs::read_to_string(&path) {
        Ok(c) => c,
        Err(e) => format!("(读取失败: {})", e),
    }
}

fn caps_lock_state() -> String {
    let on = ime_sys::ime_caps_read();
    if on != 0 { "ON".into() } else { "OFF".into() }
}

fn main() {
    let state = read_state_file();
    let caps = caps_lock_state();

    println!("=== AutoSwitchIME 诊断 ===");
    println!();
    println!("-- 状态文件 --");
    println!("路径: {}", state_file_path().display());
    println!("物理 CapsLock: {}", caps);
    println!();
    println!("-- 文件内容 --");
    for line in state.lines() {
        println!("{}", line);
    }
}
