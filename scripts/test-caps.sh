#!/usr/bin/env bash
set -euo pipefail

WEASEL=$(cmd.exe /c 'cd /d C:\ && for /d %i in ("%ProgramFiles%\Rime\weasel-*") do @echo %i\WeaselServer.exe' 2>/dev/null | tr -d '\r')
echo "Weasel: $WEASEL"

# Trigger redeploy
powershell.exe -NoProfile -Command "Start-Process -FilePath '$WEASEL' -ArgumentList '/deploy'" 2>/dev/null
echo "Redeploy sent. Waiting 3s for Rime to reload Lua..."
sleep 3

# Read state file
cat /mnt/c/Users/Administrator/AppData/Local/Temp/ime-state-rime.json 2>&1
echo ""
echo "---"
echo "Now press CapsLock in a Rime app (e.g. Notepad with rime_ice), then check state file again:"
echo "  cat /mnt/c/Users/Administrator/AppData/Local/Temp/ime-state-rime.json"
echo ""
echo "Or run ime-watch for live monitoring:"
echo "  cd /projects/ai_code/RimeVimIME"
echo '  WIN_PATH=$(echo ime-sys/target/x86_64-pc-windows-gnu/release/ime-watch.exe | sed "s|^/mnt/\([a-z]\)/|\1:/|" | sed "s|/|\\\\|g")'
echo "  powershell.exe -NoProfile -Command \"& '\$WIN_PATH'\""
