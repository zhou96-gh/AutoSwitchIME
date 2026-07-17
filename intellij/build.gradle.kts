// intellij/build.gradle.kts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform") version "2.8.0"
}

group = property("pluginGroup")!!
version = rootProject.version

val copyImeSysDll by tasks.registering(Copy::class) {
    from(rootProject.projectDir.resolve("ime-sys/target/x86_64-pc-windows-gnu/release/ime_sys.dll"))
    into(layout.projectDirectory.dir("src/main/resources/native"))
    onlyIf { rootProject.projectDir.resolve("ime-sys/target/x86_64-pc-windows-gnu/release/ime_sys.dll").exists() }
}

tasks.named("processResources") {
    dependsOn(copyImeSysDll)
}

tasks.named("buildPlugin") {
    doLast {
        val distDir = layout.buildDirectory.dir("distributions").get().asFile
        val src = distDir.resolve("intellij-${project.version}.zip")
        val outputDir = rootProject.layout.projectDirectory.dir("packages").asFile
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
            sinceBuild.set(property("pluginSinceBuild").toString())
            // Keep the upper bound open so routine IDE upgrades do not disable the plugin.
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
