#!/usr/bin/env bash
# 触发 Rime 重新部署
set -euo pipefail

cd /mnt/c

WEASEL=$(cmd.exe /c 'cd /d C:\ && for /d %i in ("%ProgramFiles%\Rime\weasel-*") do @echo %i\WeaselServer.exe' 2>/dev/null | tr -d '\r')
echo "Found: $WEASEL"

cmd.exe /c "cd /d C:\ && start \"\" \"$WEASEL\" /deploy" 2>/dev/null
echo "Deploy sent."
