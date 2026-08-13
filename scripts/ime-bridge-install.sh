#!/usr/bin/env bash
# AutoSwitchIME Rime Lua 桥部署脚本（WSL）
set -euo pipefail

# ===== 路径 =====
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 解析 Windows 用户名（powershell.exe -> cmd.exe -> $USER 逐级回退）
WSL_USER=$(powershell.exe -NoProfile -Command '[Environment]::UserName' 2>/dev/null | tr -d '\r\n')
if [ -z "$WSL_USER" ]; then
    WSL_USER=$(cmd.exe /c echo %USERNAME% 2>/dev/null | tail -1 | tr -d '\r\n')
fi
if [ -z "$WSL_USER" ]; then
    WSL_USER="$USER"
fi
RIME_DIR="/mnt/c/Users/$WSL_USER/AppData/Roaming/Rime"
SOURCE_LUA="$PROJECT_DIR/lua/rimevim_bridge.lua"
WATCH_EXE="$PROJECT_DIR/ime-sys/target/x86_64-pc-windows-gnu/release/ime-watch.exe"
PROCESSOR_LINE='    "engine/processors/@before 0": lua_processor@*rimevim_bridge'

# ===== 颜色 =====
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[0;90m'
NC='\033[0m'

usage() {
    cat <<EOF
用法: $(basename "$0") [选项]

选项:
  -s, --schema <name>   指定方案名（如 rime_ice），跳过交互选择
  -d, --dir <path>      指定 Rime 用户目录（默认: $RIME_DIR）
  -w, --watch           启动 ime-watch.exe 持续监听
  -u, --uninstall       卸载
  -h, --help            显示此帮助

示例:
  $(basename "$0")
  $(basename "$0") -s rime_ice
  $(basename "$0") -w
  $(basename "$0") -u
EOF
}

# ===== 参数解析 =====
SCHEMA=""
WATCH=false
UNINSTALL=false
HELP=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        -s|--schema) SCHEMA="$2"; shift 2 ;;
        -d|--dir) RIME_DIR="$2"; shift 2 ;;
        -w|--watch) WATCH=true; shift ;;
        -u|--uninstall) UNINSTALL=true; shift ;;
        -h|--help) HELP=true; shift ;;
        *) echo "未知参数: $1"; usage; exit 1 ;;
    esac
done

$HELP && { usage; exit 0; }

# --dir 解析完成后再派生目标路径，避免仍写入默认 Rime 目录。
LUA_DIR="$RIME_DIR/lua"
LUA_FILE="$LUA_DIR/rimevim_bridge.lua"

# ===== 工具函数 =====
deploy_rime() {
    echo -e "${CYAN}→ 触发重新部署...${NC}"

    local ws=""

    # 1. 注册表查询
    ws=$(
        cmd.exe /c 'reg query "HKLM\SOFTWARE\Rime\Weasel" /v WeaselRoot 2>nul' 2>/dev/null \
            | sed -n 's/.*REG_SZ\s*//p' | tr -d '\r\n' | sed 's/[[:space:]]*$//' \
            || true
    )
    if [ -n "$ws" ]; then
        ws="${ws}\\WeaselServer.exe"
    fi

    # 2. 直接从 WSL 挂载盘扫描，避免 cmd.exe 在 UNC 工作目录下返回空结果。
    if [ -z "$ws" ]; then
        local candidate=""
        candidate=$(
            {
                for root in "/mnt/c/Program Files/Rime" "/mnt/c/Program Files (x86)/Rime"; do
                    if [ -d "$root" ]; then
                        find "$root" -maxdepth 3 -type f -iname 'WeaselServer.exe' -print
                    fi
                done
                true
            } | sort -V | tail -1
        )
        if [ -n "$candidate" ]; then
            ws=$(wslpath -w "$candidate")
        fi
    fi

    if [ -z "$ws" ]; then
        echo -e "  ${YELLOW}WeaselServer.exe 未找到，请在 Rime 托盘菜单手动重新部署${NC}"
        return 1
    fi

    echo -e "  ${GRAY}找到: $ws${NC}"
    # PowerShell 不受 UNC 路径问题影响
    if powershell.exe -NoProfile -Command "Start-Process -FilePath '$ws' -ArgumentList '/deploy'" 2>/dev/null; then
        echo -e "  ${GRAY}部署指令已发送，约 10 秒后生效${NC}"
    else
        echo -e "  ${RED}启动 WeaselServer.exe /deploy 失败${NC}"
        return 1
    fi
}

get_schema_names() {
    local pattern="$RIME_DIR"/*.custom.yaml
    for f in $pattern; do
        [ -f "$f" ] && basename "$f" | sed 's/\.custom\.yaml$//'
    done | sort
}

get_yaml_path() {
    local name="$1"
    if echo "$name" | grep -q '\.custom\.yaml$'; then
        echo "$RIME_DIR/$name"
    else
        echo "$RIME_DIR/$name.custom.yaml"
    fi
}

choose_schema() {
    local schemas=()
    while IFS= read -r s; do schemas+=("$s"); done < <(get_schema_names)

    if [ ${#schemas[@]} -eq 0 ]; then
        echo -e "${YELLOW}! Rime 目录中未检测到 *.custom.yaml${NC}" >&2
        echo -e "  ${GRAY}需要手动指定方案名。方案名即 *.schema.yaml 文件名去掉后缀的部分：${NC}" >&2
        echo -e "  ${GRAY}  ls $RIME_DIR/*.schema.yaml${NC}" >&2
        read -r -p "  方案名（如 rime_ice、luna_pinyin）: " name
        echo "$name"
        return
    fi

    echo -e "\n${CYAN}选择 Rime 输入方案${NC}" >&2
    echo -e "  ${GRAY}以下是在你的 Rime 目录（$RIME_DIR）中检测到的已有输入方案：${NC}" >&2
    for i in "${!schemas[@]}"; do
        echo "  [$((i+1))] ${schemas[$i]}" >&2
    done
    echo -e "  ${GRAY}[m] 手动输入方案名（列表中没有的话选这个）${NC}" >&2
    read -r -p "请选择 [1-${#schemas[@]}] 或 [m]: " sel

    if [ "$sel" = "m" ] || [ "$sel" = "M" ]; then
        echo -e "  ${GRAY}输入你的输入方案名，即 *.schema.yaml 文件名去掉后缀的部分${NC}" >&2
        read -r -p "  方案名（如 rime_ice、luna_pinyin）: " name
        echo "$name"
        return
    fi

    if [[ "$sel" =~ ^[0-9]+$ ]] && [ "$sel" -ge 1 ] && [ "$sel" -le "${#schemas[@]}" ]; then
        echo "${schemas[$((sel-1))]}"
    else
        echo -e "${YELLOW}输入无效，使用默认: ${schemas[0]}${NC}" >&2
        echo "${schemas[0]}"
    fi
}

add_processor() {
    local yaml="$1"

    if [ ! -f "$yaml" ]; then
        cat > "$yaml" <<EOF
patch:
$PROCESSOR_LINE
EOF
        echo "创建"
        return
    fi

    # 检查是否已安装（精确行匹配，或包含关键词即视为已安装）
    if grep -Fq "lua_processor@*rimevim_bridge" "$yaml"; then
        echo "已存在"
        return
    fi

    if grep -q '^patch:' "$yaml"; then
        sed -i "/^patch:/a\\$PROCESSOR_LINE" "$yaml"
    else
        printf "\n\npatch:\n%s\n" "$PROCESSOR_LINE" >> "$yaml"
    fi
    echo "添加"
}

remove_processor() {
    local yaml="$1"
    [ ! -f "$yaml" ] && return 1
    if grep -Fq "lua_processor@*rimevim_bridge" "$yaml"; then
        sed -i '/lua_processor@\*rimevim_bridge/d' "$yaml"
        return 0
    fi
    return 1
}

# ===== 监听模式 =====
if $WATCH && ! $UNINSTALL; then
    if [ ! -f "$WATCH_EXE" ]; then
        echo -e "${YELLOW}! ime-watch.exe 未找到${NC}"
        echo -e "  构建: cd ime-sys && cargo build --release --target x86_64-pc-windows-gnu --bin ime-watch${NC}"
        exit 1
    fi
    echo -e "${CYAN}AutoSwitchIME 状态监听${NC}"
    echo -e "  ${GRAY}elapsed   ascii  caps_f  compos  phys_c${NC}"
    echo -e "  ${GRAY}──────────────────────────────────────────────────${NC}"
    # WSL 通过 powershell.exe 运行 Windows 原生 exe
    WIN_PATH=$(echo "$WATCH_EXE" | sed 's|^/mnt/\([a-z]\)/|\1:/|' | sed 's|/|\\|g')
    echo -e "  ${GRAY}Ctrl+C 停止监听${NC}"
    powershell.exe -Command "& '$WIN_PATH'"
    exit 0
fi

# ===== 卸载 =====
if $UNINSTALL; then
    echo -e "\n${CYAN}卸载 AutoSwitchIME Rime Lua 桥${NC}"
    echo -e "  ${GRAY}涉及文件:${NC}"
    echo -e "  ${GRAY}  Rime 目录:  $RIME_DIR${NC}"
    echo -e "  ${GRAY}  Lua 插件:   $LUA_FILE${NC}"

    echo -e "\n${CYAN}[1/3] 删除 lua 插件${NC}"
    echo -e "  ${GRAY}移除 rimevim_bridge.lua，Rime 不再加载此插件${NC}"
    changed=false
    if [ -f "$LUA_FILE" ]; then
        rm -f "$LUA_FILE"
        echo -e "${GREEN}✓ 已删除: $LUA_FILE${NC}"
        changed=true
    fi

    echo -e "\n${CYAN}[2/3] 清理方案配置中的 processor${NC}"
    echo -e "  ${GRAY}从各 *.custom.yaml 中移除 processor 注册行${NC}"
    while IFS= read -r name; do
        [ -z "$name" ] && continue
        yaml=$(get_yaml_path "$name")
        if remove_processor "$yaml"; then
            echo -e "${GREEN}✓ 已清理 processor: $yaml${NC}"
            changed=true
        fi
    done < <(get_schema_names)

    if $changed; then
        echo -e "\n${CYAN}[3/3] 重新部署 Rime${NC}"
        echo -e "  ${GRAY}通知 WeaselServer 重新加载配置，使改动生效${NC}"
        deploy_rime
    else
        echo -e "${YELLOW}未检测到 AutoSwitchIME 桥接${NC}"
    fi
    exit 0
fi

# ===== 安装 =====
echo -e "\n${CYAN}安装 AutoSwitchIME Rime Lua 桥${NC}"
echo -e "  ${GRAY}涉及文件:${NC}"
echo -e "  ${GRAY}  Rime 目录:  $RIME_DIR${NC}"
echo -e "  ${GRAY}  Lua 插件:   $LUA_FILE${NC}"
echo -e "  ${GRAY}  来源:       $SOURCE_LUA${NC}"

if [ ! -d "$RIME_DIR" ]; then
    echo -e "${RED}✗ Rime 用户目录不存在: $RIME_DIR${NC}"
    echo -e "${YELLOW}  请先确认小狼毫(Weasel)已安装${NC}"
    exit 1
fi

# 1. 选择方案
echo -e "\n${CYAN}[1/4] 选择 Rime 输入方案${NC}"
echo -e "  ${GRAY}AutoSwitchIME 需要挂载到你的输入方案（如 rime_ice）的 processor 链上${NC}"
echo -e "  ${GRAY}插件会在此方案激活时拦截按键状态，判断中英文切换时机${NC}"
if [ -n "$SCHEMA" ]; then
    schema_name="$SCHEMA"
else
    schema_name=$(choose_schema)
fi
yaml_path=$(get_yaml_path "$schema_name")
echo -e "${CYAN}方案: $schema_name → $yaml_path${NC}"

# 2. 复制 lua
echo -e "\n${CYAN}[2/4] 安装 Rime Lua 插件${NC}"
echo -e "  ${GRAY}复制 rimevim_bridge.lua → Rime 的 lua 目录，注册为 Rime 的 lua_processor${NC}"
mkdir -p "$LUA_DIR"
cp "$SOURCE_LUA" "$LUA_FILE"
if ! cmp -s "$SOURCE_LUA" "$LUA_FILE"; then
    echo -e "${RED}✗ Lua 脚本复制校验失败: $LUA_FILE${NC}"
    exit 1
fi
echo -e "${GREEN}✓ 已复制: $LUA_FILE${NC}"

# 协议 v2 不再使用通用状态文件，部署时清理旧版本残留。
WINDOWS_TEMP=$(powershell.exe -NoProfile -Command '[IO.Path]::GetTempPath()' 2>/dev/null | tr -d '\r\n')
if [ -n "$WINDOWS_TEMP" ]; then
    for legacy_name in ime-state-rime.json ime-state-rime-unknown.json; do
        LEGACY_STATE_FILE=$(wslpath -u "${WINDOWS_TEMP}${legacy_name}")
        if [ -f "$LEGACY_STATE_FILE" ]; then
            rm -f "$LEGACY_STATE_FILE"
            echo -e "${GREEN}✓ 已清理旧状态文件: ${WINDOWS_TEMP}${legacy_name}${NC}"
        fi
    done
fi

# 3. 配置 yaml
echo -e "\n${CYAN}[3/4] 注册 processor 到方案配置${NC}"
echo -e "  ${GRAY}在 ${schema_name}.custom.yaml 中插入 engine/processors/@before 0，${NC}"
echo -e "  ${GRAY}让 Rime 每次按键时调用 rimevim_bridge 插件判断中英文状态${NC}"
action=$(add_processor "$yaml_path")
case "$action" in
    创建)       echo -e "${GREEN}✓ 已创建: $yaml_path${NC}" ;;
    添加)       echo -e "${GREEN}✓ 已添加 processor: $yaml_path${NC}" ;;
    已存在)     echo -e "${GRAY}→ 跳过，processor 已存在: $yaml_path${NC}" ;;
    *)          echo -e "${GREEN}✓ 已${action}: $yaml_path${NC}" ;;
esac

# 4. 重新部署
echo -e "\n${CYAN}[4/4] 重新部署 Rime${NC}"
echo -e "  ${GRAY}通知 WeaselServer 重新加载配置，使 lua 插件和 yaml 修改生效${NC}"
deploy_rime

# 5. 提示
echo -e "\n${CYAN}部署完成！${NC}"
echo -e "  ${GRAY}$(basename "$0") -w              启动监听${NC}"
echo -e "  ${GRAY}$(basename "$0") -u              卸载${NC}"
echo -e "  ${GRAY}$(basename "$0") -s rime_ice     跳过交互${NC}"
