plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "io.github.f_breure"
version = "1.1.3"

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

intellijPlatform {
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
}

tasks {
    withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:deprecation")
    }
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
