import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.charset.StandardCharsets

plugins {
    java
    id("org.springframework.boot") version "3.5.4"
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
}

group = "de.honoka.ci"
version = "1.0.0-dev"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.2.0"))
    implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.2"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("cn.hutool:hutool-all:5.8.25")
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

tasks {
    withType<JavaCompile> {
        options.run {
            encoding = StandardCharsets.UTF_8.name()
            val compilerArgs = compilerArgs as MutableCollection<String>
            compilerArgs += listOf("-parameters")
        }
    }

    withType<KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xjvm-default=all")
        }
    }

    bootJar {
        archiveFileName = "${project.name}.jar"
    }

    withType<Test> {
        useJUnitPlatform()
    }
}
