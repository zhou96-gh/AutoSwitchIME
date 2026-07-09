#!/bin/bash
set -euo pipefail

echo "=== 1/2: Rust 原生模块 ==="
cd /workspace/ime-sys
cargo build --release --target x86_64-pc-windows-gnu 2>&1

mkdir -p /workspace/vscode/bin
cp /workspace/ime-sys/target/x86_64-pc-windows-gnu/release/ime_sys.dll /workspace/vscode/bin/ime_sys.dll

echo "=== 2/2: VSCode 扩展 ==="
cd /workspace/vscode
npm ci --ignore-scripts --include=optional --os=win32 --cpu=x64 2>&1
npx vsce package --allow-missing-repository 2>&1
vscode_version="$(node -p "require('./package.json').version")"
python3 -B scripts/check_vsix.py \
  "auto-switch-ime-${vscode_version}.vsix" \
  /workspace/ime-sys/target/x86_64-pc-windows-gnu/release/ime_sys.dll
mkdir -p /workspace/packages
cp "auto-switch-ime-${vscode_version}.vsix" "/workspace/packages/AutoSwitchIME-VSCode-${vscode_version}.vsix"
echo "DONE"
