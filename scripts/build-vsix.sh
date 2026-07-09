#!/bin/bash
set -euo pipefail
cd /workspace/vscode
npx vsce package --allow-missing-repository 2>&1
vscode_version="$(node -p "require('./package.json').version")"
python3 scripts/check_vsix.py "auto-switch-ime-${vscode_version}.vsix"
echo "DONE"
