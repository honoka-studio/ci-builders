package de.honoka.ci.builder.util

import de.honoka.ci.builder.main
import kotlin.reflect.jvm.javaMethod

data class EnvVariables(

    var builderName: String = "",

    var mainClass: String? = getEnvOrNull("CIB_MAIN_CLASS"),

    var functionName: String = "",

    var commandArgs: List<String> = listOf(),

    var projectPath: String? = getEnvOrNull("CIB_PROJECT_PATH"),

    val subproject: String? = getEnvOrNull("CIB_SUBPROJECT"),

    val workspace: String = getEnv("GITHUB_WORKSPACE"),

    var workspaceName: String = "",

    val githubOutput: String = getEnv("GITHUB_OUTPUT"),

    val artifactName: String = getEnvOrNull("CIB_ARTIFACT_NAME") ?: "artifact",

    @Transient
    val gitInfo: GitInfo? = getEnvOrNull("CIB_GIT_INFO")?.let {
        val parts = it.split("/").map { s -> s.trim().ifBlank { null } }
        GitInfo(parts[0]!!, parts[1]!!)
    }
) {

    data class GitInfo(

        val username: String,

        val email: String
    )

    val projectName: String
        get() = if(!subproject.isNullOrBlank()) subproject else workspaceName

    fun initArgs(args: Array<String>) {
        var startIndex = 2
        if(mainClass == null) {
            builderName = args[0]
            functionName = args[1]
        } else {
            functionName = args[0]
            startIndex = 1
        }
        commandArgs = args.toList().subList(startIndex, args.size)
        initMainClass()
        initWorkspace()
    }

    private fun initMainClass() {
        mainClass?.let { return }
        val prefix = ::main.javaMethod!!.declaringClass.name.run {
            take(lastIndexOf(".") + 1)
        }
        val suffix = builderNameClassMap[builderName] ?: run {
            error("No builder with name \"$builderName\"!")
        }
        mainClass = "$prefix$suffix"
    }

    private fun initWorkspace() {
        projectPath = projectPath ?: "$workspace/repo"
        if(!subproject.isNullOrBlank()) {
            projectPath += "/$subproject"
        }
        workspaceName = workspace.substring(workspace.lastIndexOf("/") + 1)
    }
}

private val builderNameClassMap = mapOf(
    "gradle-library" to "gradle.GradleLibraryBuilder",
    "npm-library" to "npm.NpmLibraryBuilder"
)
