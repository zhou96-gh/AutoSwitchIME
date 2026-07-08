fn main() {
    let args: Vec<String> = std::env::args().collect();
    let cmd = args.get(1).map(|s| s.as_str()).unwrap_or("");
    match cmd {
        "--read" => println!("{}", ime_sys::ime_caps_read()),
        "--toggle" => println!("{}", ime_sys::ime_caps_toggle()),
        "--set" => {
            let target = if args.get(2).is_some_and(|v| v == "on" || v == "true" || v == "1") { 1 } else { 0 };
            println!("{}", ime_sys::ime_caps_set(target));
        }
        _ => {
            eprintln!("Usage: ime-helper.exe [--read|--toggle|--set <on|off>]");
            std::process::exit(1);
        }
    }
}
