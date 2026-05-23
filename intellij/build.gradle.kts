// intellij/build.gradle.kts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.8.0"
}

group = property("pluginGroup")!!
version = rootProject.version

tasks.named("buildPlugin") {
    doLast {
        val distDir = layout.buildDirectory.dir("distributions").get().asFile
        val oldFile = distDir.resolve("intellij-${project.version}.zip")
        val newFile = distDir.resolve("AutoSwitchIME-${project.version}.zip")
        if (oldFile.exists()) {
            oldFile.renameTo(newFile)
        }
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":core"))

    intellijPlatform {
        phpstorm("2026.1")
        plugins("IdeaVIM:2.35.2")
        pluginVerifier()
        zipSigner()
    }

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
