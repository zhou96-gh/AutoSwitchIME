#!/bin/bash
set -euo pipefail
cd /workspace/vscode
npx vsce package --no-dependencies --allow-missing-repository 2>&1
echo "DONE"
