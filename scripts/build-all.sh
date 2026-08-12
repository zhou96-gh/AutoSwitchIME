#!/bin/bash
set -euo pipefail

echo "=== 0/4: 版本一致性 ==="
cd /workspace
python3 -B scripts/check-version-consistency.py

echo "=== 1/4: Rust 原生模块 ==="
cd /workspace/ime-sys
cargo build --release --target x86_64-pc-windows-gnu 2>&1
cp /workspace/ime-sys/target/x86_64-pc-windows-gnu/release/ime_sys.dll \
  /workspace/intellij/src/main/resources/native/ime_sys.dll
echo "Rust build done"

echo "=== 2/4: IntelliJ 插件 ==="
cd /workspace
./gradlew clean :intellij:buildPlugin -x buildSearchableOptions -x prepareJarSearchableOptions 2>&1
python3 -B scripts/check-intellij-plugin.py
echo "IntelliJ plugin done"

echo "=== 3/4: VSCode 扩展 ==="
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

echo "=== 4/4: Rime Lua 桥 ==="
lua_package="/workspace/packages/RimeVimIME-Lua-${vscode_version}.zip"
rm -f "$lua_package"
cd /workspace/lua
zip -q "$lua_package" rimevim_bridge.lua rime_ice.custom.yaml README.md
cd /workspace
python3 -B scripts/check-lua-package.py "$lua_package"
echo "Rime Lua bridge done"

echo ""
echo "=== 产物 ==="
ls -lh "/workspace/packages/AutoSwitchIME-IntelliJ-${vscode_version}.zip"
ls -lh "/workspace/packages/AutoSwitchIME-VSCode-${vscode_version}.vsix"
ls -lh "/workspace/packages/RimeVimIME-Lua-${vscode_version}.zip"
ls -lh /workspace/ime-sys/target/x86_64-pc-windows-gnu/release/ime_sys.dll 2>/dev/null || echo "No DLL"
