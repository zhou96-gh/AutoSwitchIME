use std::thread::sleep;
use std::time::{Duration, Instant};

#[derive(Clone, PartialEq, Eq)]
struct Snapshot {
    conversion_status: i64,
    caps_lock: bool,
    composing: i32,
}

fn read_snapshot() -> Snapshot {
    Snapshot {
        conversion_status: ime_sys::ime_get_conversion_status(),
        caps_lock: ime_sys::ime_caps_read() != 0,
        composing: ime_sys::ime_is_composing(),
    }
}

fn print_snapshot(start: Instant, snapshot: &Snapshot, event: &str) {
    let mode = ime_sys::decode_conversion_status(snapshot.conversion_status)
        .map(|status| {
            if status.is_ascii_mode {
                "ascii"
            } else {
                "native"
            }
        })
        .unwrap_or("unavailable");
    println!(
        "[{:>8.1}s] mode={:<11} caps={} composing={} raw={} {}",
        start.elapsed().as_secs_f64(),
        mode,
        snapshot.caps_lock,
        snapshot.composing,
        snapshot.conversion_status,
        event,
    );
}

fn main() {
    let start = Instant::now();
    let mut previous = read_snapshot();
    print_snapshot(start, &previous, "initial");

    loop {
        sleep(Duration::from_millis(100));
        let current = read_snapshot();
        if current != previous {
            print_snapshot(start, &current, "change");
            previous = current;
        }
    }
}
