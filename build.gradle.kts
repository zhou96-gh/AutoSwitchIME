// JAVA_HOME = D:\Program Files\Java\java-21
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.8.0"
}

group = property("pluginGroup")!!
version = property("pluginVersion")!!

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // JNA 用于 Windows API 调用
    compileOnly("net.java.dev.jna:jna:5.14.0")
    compileOnly("net.java.dev.jna:jna-platform:5.14.0")
    
    intellijPlatform {
        phpstorm("2026.1")
        plugins("IdeaVIM:2.35.2")
        pluginVerifier()
        zipSigner()
    }
    
    // 测试依赖
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        version.set("${project.version}")
        ideaVersion {
            sinceBuild.set("261")
            untilBuild.set("261.*")
        }
    }
}

tasks {
    compileKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    compileJava {
        options.release.set(17)
    }
    
    test {
        useJUnitPlatform()
    }
}
