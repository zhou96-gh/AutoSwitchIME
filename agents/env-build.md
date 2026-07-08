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

## Docker 构建

```bash
# 构建镜像
cd /projects/ai_code/RimeVimIME
docker compose build

# 交互式 shell
docker compose run --rm dev

# IntelliJ 插件
./gradlew clean :intellij:buildPlugin -x buildSearchableOptions -x prepareJarSearchableOptions

# VSCode 扩展
cd vscode && npx vsce package --no-dependencies --allow-missing-repository

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
