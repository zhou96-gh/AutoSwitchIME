// intellij/build.gradle.kts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform") version "2.8.0"
}

group = property("pluginGroup")!!
version = rootProject.version

tasks.named("buildPlugin") {
    doLast {
        val distDir = layout.buildDirectory.dir("distributions").get().asFile
        val src = distDir.resolve("intellij-${project.version}.zip")
        val outputDir = rootProject.layout.projectDirectory.dir("build/distributions").asFile
        outputDir.mkdirs()
        val dest = outputDir.resolve("AutoSwitchIME-IntelliJ-${project.version}.zip")
        if (src.exists()) {
            src.copyTo(dest, overwrite = true)
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
