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
