# 环境与构建配置

## 版本要求

| 组件 | 版本 | 备注 |
|------|------|------|
| JDK | 21 | 路径: `D:\Program Files\Java\java-21` |
| Kotlin | 2.3.0 | 匹配 PhpStorm 2026.1 |
| Gradle | 8.10 | 必须 >= 8.6 (IPGP 2.x 要求) |
| IntelliJ Platform Gradle Plugin | 2.8.0 | `org.jetbrains.intellij.platform` |
| 目标 IDE | PhpStorm 2026.1 | build 261.* |
| IdeaVim | 2.35.2 | 编译时依赖 |

## 构建命令

```powershell
$env:JAVA_HOME="D:\Program Files\Java\java-21"
.\gradlew.bat build          # 编译 + 打包
.\gradlew.bat runIde         # 启动测试 IDE
.\gradlew.bat buildPlugin    # 生成 build/distributions/*.zip
```

## 构建输出

- 产物: `build/distributions/AutoSwitchIME-<version>.zip`
- 大小: ~77 KB
- 最后状态: `BUILD SUCCESSFUL` (14 tasks executed)

## 构建注意事项

- `buildSearchableOptions` 任务在 PhpStorm 2026.1 环境下会报 `ClassNotFoundException: com.intellij.idea.Main`
- 解决方案: `.\gradlew.bat buildPlugin -x buildSearchableOptions`

## gradle.properties

```properties
pluginVersion=1.1.0
pluginGroup=com.autoswitchime
pluginName=自动切换输入
kotlin.stdlib.default.dependency=false
```

## 已知警告

- `runReadAction` 已弃用，建议使用 `ReadAction.nonBlocking`
