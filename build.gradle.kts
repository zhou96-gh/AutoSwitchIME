// 根项目：仅作为 Gradle 构建入口，不包含插件代码
// 插件代码在 intellij/ 子模块中
// 构建命令：.\gradlew.bat :intellij:buildPlugin -x buildSearchableOptions -x prepareJarSearchableOptions

group = property("pluginGroup")!!
version = property("pluginVersion")!!
