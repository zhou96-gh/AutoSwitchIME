# 环境与构建配置

## 版本要求

| 组件 | 版本 | 备注 |
|------|------|------|
| JDK | 21 (Temurin) | Docker 镜像内置 |
| Kotlin | 2.3.0 | 匹配 PhpStorm 2026.1 |
| Gradle | 8.10 | Wrapper 自动下载 |
| IntelliJ Platform Gradle Plugin | 2.8.0 | `org.jetbrains.intellij.platform` |
| 目标 IDE | PhpStorm 2026.1 | build 261.* |
| IdeaVim | 2.35.2 | 编译时依赖 |
| Rust | stable | + `x86_64-pc-windows-gnu` target |
| Node.js | 22 | Docker 镜像内置 |
| VSCE | latest | npm global install |

IntelliJ 插件只设置 `pluginSinceBuild=261` 作为最低兼容版本，不设置 `until-build`。这样 IDE 常规升级不会仅因版本号变化而禁用插件；最终 ZIP 的兼容元数据由 `scripts/check-intellij-plugin.py` 校验。

IntelliJ 主 JAR 必须包含 40×40 SVG 插件 Logo：`META-INF/pluginIcon.svg` 和 `META-INF/pluginIcon_dark.svg`。`scripts/check-intellij-plugin.py` 必须同时校验文件存在、SVG 尺寸和体积，避免 IDE 插件列表缺少图片。

公共 PNG Logo 只维护根目录 `resources/icon.png`；VSCode 打包前由构建脚本复制到 `vscode/resources/icon.png`，不得提交该构建副本。

## Docker 构建

### 容器重建与清理边界

- 平时运行测试或重新创建容器不需要重建镜像；只有 `Dockerfile`、`docker-compose.yml` 或基础工具链版本变化时才运行 `docker compose build`。
- 本项目的开发容器用于测试、构建和打包，默认按 `docker compose run --rm dev ...` 临时容器使用；不作为常驻服务接入 Supervisor。
- “清理容器垃圾”默认只清理本项目临时容器、无用网络和 Docker build cache，例如 `docker compose down --remove-orphans`、`docker builder prune -f`。
- 未经明确要求，不删除 `rimevim-dev:latest` 镜像、`rimevimime_*` 依赖缓存 volume、`vscode/node_modules/`、`ime-sys/target/`、`packages/` 等可复用依赖缓存或产物。
- 只有用户明确要求“全清/重新下载依赖/删除镜像或 volume”时，才删除镜像、volume 或工作区构建产物。

### 版本要求

打包前必须根据本次改动更新版本号，不能用旧版本重复打包。

- patch：bug 修复、行为修正、文档或构建修复
- minor：向后兼容的新能力、新配置项、新平台能力
- major：破坏性配置、接口或行为变更

同步更新位置：

- 根目录 `AGENTS.md` 的当前版本
- `gradle.properties` 的 `pluginVersion`
- `vscode/package.json` 的 `version`
- `vscode/package-lock.json` 顶部根包版本

打包前先运行版本一致性检查；`scripts/build-all.sh` 已内置该检查：

```bash
python3 -B scripts/check-version-consistency.py
```

```bash
# 构建镜像
cd /projects/ai_code/RimeVimIME
docker compose build

# 交互式 shell
docker compose run --rm dev

# IntelliJ 插件
./gradlew clean :intellij:buildPlugin -x buildSearchableOptions -x prepareJarSearchableOptions
python3 -B scripts/check-intellij-plugin.py

# VSCode 扩展
cd vscode && npx vsce package --allow-missing-repository

# Rust 原生模块
cd ime-sys && cargo build --release --target x86_64-pc-windows-gnu

# 产物流
ime-sys/target/x86_64-pc-windows-gnu/release/
├── ime_sys.dll         # cdylib → JNA (IntelliJ)
├── libime_sys.a        # staticlib (预留)
└── ime-diag.exe        # CLI 诊断工具
```

## 构建注意事项

- `buildSearchableOptions` 在 PhpStorm 2026.1 下报 `ClassNotFoundException` → `-x buildSearchableOptions -x prepareJarSearchableOptions`
- 多模块架构下必须指定 `:intellij:buildPlugin`
- JNA 依赖为 `compileOnly`（IDE 自带，打包会冲突）
- Rust 交叉编译使用 mingw-w64 linker，产物为 Windows `.dll`

## GitHub 发布

`develop` 是长期开发与发布准备分支，必须同时保留在本地和远端；`master` 是受保护的正式发布分支，只接收从 `develop` 发起并合入的 PR，不得直接推送。发布完成后切回 `develop` 继续开发，并将已合入的 `master` 同步回 `develop`，避免分支历史偏离。

### 发布产物

每个正式 Release 必须同时上传以下三个附件，文件名中的版本必须与插件元数据一致：

- `packages/AutoSwitchIME-IntelliJ-<version>.zip`
- `packages/AutoSwitchIME-VSCode-<version>.vsix`
- `packages/RimeVimIME-Lua-<version>.zip`

Lua ZIP 必须至少包含 `rimevim_bridge.lua`、适用的 `*.custom.yaml` 示例和安装说明。Lua 桥是运行时必需组件，不得只依赖 GitHub 自动生成的 Source code 压缩包提供。

### 发布顺序

1. 根据改动类型升级版本，并同步全部版本源和 `CHANGELOG.md`。
2. 清理 `packages/` 中本次版本的同名残留，运行 `scripts/build-all.sh` 生成三个附件；重新发布时保留上一版本产物直至新 Release 验证成功。
3. 运行版本一致性、IntelliJ 插件、VSCode 扩展和 Lua ZIP 内容检查，并记录三个附件的 SHA-256。
4. 在 `develop` 提交并推送发布变更，创建以 `master` 为目标分支的 PR；验证 PR 内容后合入 `master`。
5. 确认远端 `master` 指向发布提交，再创建带注释的 `v<version>` 标签并推送该标签。
6. 创建非草稿、非预发布的 GitHub Release，上传三个附件，并在正文中写明主要变更、安装入口和 SHA-256。
7. 回读 Release，确认标签目标、附件名称、附件大小和下载地址均正确后，才算发布成功。
8. 将发布后的 `master` 同步回 `develop` 并推送，保留本地和远端 `develop` 供下一版本继续开发。

### 重新发布与清理

- 已公开版本发现产物遗漏或打包错误时必须升级 patch 版本重新发布，不覆盖或复用原标签。
- 只有新 Release 完整发布并回读验证成功后，才可删除用户明确指定的旧 Release、对应远端和本地标签以及 `packages/` 中的旧版本产物。
- 删除前必须再次核对旧版本号和目标 Release；不得清理未明确指定的历史版本。
