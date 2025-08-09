@file:Suppress("UnstableApiUsage")

pluginManagement {
    val customRepositories: RepositoryHandler.() -> Unit = {
        mavenLocal()
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        gradlePluginPortal()
    }
    repositories(customRepositories)
    dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        repositories(customRepositories)
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ci-builders"
