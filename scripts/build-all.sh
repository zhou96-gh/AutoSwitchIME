#!/bin/bash
set -euo pipefail

echo "=== 0/3: 版本一致性 ==="
cd /workspace
python3 -B scripts/check-version-consistency.py

echo "=== 1/3: Rust 原生模块 ==="
cd /workspace/ime-sys
cargo build --release --target x86_64-pc-windows-gnu 2>&1
echo "Rust build done"

echo "=== 2/3: IntelliJ 插件 ==="
cd /workspace
./gradlew clean :intellij:buildPlugin -x buildSearchableOptions -x prepareJarSearchableOptions 2>&1
echo "IntelliJ plugin done"

echo "=== 3/3: VSCode 扩展 ==="
mkdir -p /workspace/vscode/bin
cp /workspace/ime-sys/target/x86_64-pc-windows-gnu/release/ime_sys.dll /workspace/vscode/bin/ime_sys.dll

cd /workspace/vscode
npm ci --ignore-scripts --include=optional --os=win32 --cpu=x64 2>&1
npx vsce package --allow-missing-repository 2>&1
vscode_version="$(node -p "require('./package.json').version")"
python3 -B scripts/check_vsix.py \
  "auto-switch-ime-${vscode_version}.vsix" \
  /workspace/ime-sys/target/x86_64-pc-windows-gnu/release/ime_sys.dll
mkdir -p /workspace/packages
cp "auto-switch-ime-${vscode_version}.vsix" "/workspace/packages/AutoSwitchIME-VSCode-${vscode_version}.vsix"
echo "VSCode extension done"

echo ""
echo "=== 产物 ==="
ls -lh /workspace/packages/AutoSwitchIME-IntelliJ-*.zip 2>/dev/null || echo "No IntelliJ zip"
ls -lh /workspace/vscode/auto-switch-ime-*.vsix 2>/dev/null || echo "No VSCode vsix"
ls -lh /workspace/ime-sys/target/x86_64-pc-windows-gnu/release/ime_sys.dll 2>/dev/null || echo "No DLL"
