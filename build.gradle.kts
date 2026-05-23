// 根项目：统一管理插件版本，子模块不再声明版本号
// 构建命令：.\gradlew.bat :intellij:buildPlugin -x buildSearchableOptions -x prepareJarSearchableOptions

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.0" apply false
}

group = property("pluginGroup")!!
version = property("pluginVersion")!!
