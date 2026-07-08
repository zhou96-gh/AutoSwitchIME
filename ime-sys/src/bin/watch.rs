use serde::Deserialize;
use std::env;
use std::fs;
use std::path::PathBuf;
use std::thread::sleep;
use std::time::{Duration, Instant};

#[derive(Deserialize, Clone, PartialEq)]
struct StateFile {
    #[serde(default)]
    ascii_mode: bool,
    #[serde(default)]
    caps_lock: bool,
    #[serde(default, rename = "is_composing")]
    composing: bool,
}

#[derive(Clone, PartialEq)]
struct Snapshot {
    ascii_mode: bool,
    caps_lock_file: bool,
    composing: bool,
    physical_caps: i32,
}

impl Snapshot {
    fn header() -> &'static str {
        "   elapsed   ascii  caps_f  compos  phys_c  source"
    }
}

impl std::fmt::Display for Snapshot {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(
            f,
            "{:>8} {:>7} {:>7} {:>7} {:>7}",
            self.ascii_mode, self.caps_lock_file, self.composing, self.physical_caps,
            if self.physical_caps != 0 { "CAPS_ON" } else { "CAPS_OFF" },
        )
    }
}

fn state_file_path() -> PathBuf {
    if let Ok(tmp) = env::var("TEMP") {
        PathBuf::from(tmp).join("ime-state-rime.json")
    } else {
        PathBuf::from("/tmp/ime-state-rime.json")
    }
}

fn read_state_file(path: &PathBuf) -> Option<StateFile> {
    let content = fs::read_to_string(path).ok()?;
    serde_json::from_str(&content).ok()
}

fn main() {
    let state_path = state_file_path();
    let mut prev = Snapshot {
        ascii_mode: true,
        caps_lock_file: false,
        composing: false,
        physical_caps: ime_sys::ime_caps_read(),
    };
    let start = Instant::now();

    println!("{}", Snapshot::header());
    println!(
        "[{:>8.1}s] {}  initial",
        start.elapsed().as_secs_f64(),
        prev,
    );

    loop {
        sleep(Duration::from_millis(50));

        let physical = ime_sys::ime_caps_read();
        let file_state = read_state_file(&state_path);
        let cur = Snapshot {
            ascii_mode: file_state.as_ref().map(|s| s.ascii_mode).unwrap_or(prev.ascii_mode),
            caps_lock_file: file_state.as_ref().map(|s| s.caps_lock).unwrap_or(prev.caps_lock_file),
            composing: file_state.as_ref().map(|s| s.composing).unwrap_or(prev.composing),
            physical_caps: physical,
        };

        if cur != prev {
            println!(
                "[{:>8.1}s] {}  change",
                start.elapsed().as_secs_f64(),
                cur,
            );
            prev = cur;
        }
    }
}
