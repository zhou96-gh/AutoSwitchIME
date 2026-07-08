#!/usr/bin/env bash
set -euo pipefail

# Change to Windows drive first to avoid UNC path issues
cd /mnt/c

# Try registry
WS=$(cmd.exe /c "reg query \"HKLM\SOFTWARE\Rime\Weasel\" /v WeaselRoot 2>nul" 2>/dev/null | sed -n 's/.*REG_SZ\s*//p' | tr -d '\r\n')
echo "Reg: $WS"

if [ -z "$WS" ]; then
    # Try scanning Program Files
    WS=$(cmd.exe /c "for /d %i in (\"%ProgramFiles%\\Rime\\weasel-*\") do @echo %i" 2>/dev/null | tr -d '\r')
    echo "PF scan: $WS"
fi
if [ -z "$WS" ]; then
    WS=$(cmd.exe /c "for /d %i in (\"%ProgramFiles(x86)%\\Rime\\weasel-*\") do @echo %i" 2>/dev/null | tr -d '\r')
    echo "PF86 scan: $WS"
fi

echo "Result: $WS"
