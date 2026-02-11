import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.charset.StandardCharsets

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.spring)
}

group = "de.honoka.ci"
version = libs.versions.p.root.get()

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

dependencies {
    implementation(platform(libs.kotlin.bom))
    implementation(platform(libs.kotlin.coroutines.bom))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation(libs.hutool)
    implementation(libs.slf4j.api)
    implementation(libs.logback)
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
