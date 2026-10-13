plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "io.github.f_breure"
version = "1.0.5"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
        intellijDependencies()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    intellijPlatform {
        clion("2026.2.3")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    jar {
        from("LICENSE") {
            into("META-INF")
        }
    }
    patchPluginXml {
        sinceBuild.set("262")
    }
    buildSearchableOptions {
        enabled = false
    }
}
