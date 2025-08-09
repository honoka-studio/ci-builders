package de.honoka.ci.builder.util

import de.honoka.ci.builder.main
import kotlin.reflect.jvm.javaMethod

data class EnvVariables(

    var builderName: String = "",

    var mainClass: String? = getEnvOrNull("BUILDER_MAIN_CLASS"),

    var functionName: String = "",

    var commandArgs: List<String> = listOf(),

    var projectPath: String? = getEnvOrNull("PROJECT_PATH"),

    var workspace: String = getEnv("GITHUB_WORKSPACE"),

    var workspaceName: String = "",

    val githubOutput: String = getEnv("GITHUB_OUTPUT")
) {

    fun initArgs(args: Array<String>) {
        var startIndex = 2
        if(mainClass == null) {
            builderName = args[0]
            functionName = args[1]
        } else {
            functionName = args[0]
            startIndex = 1
        }
        this.commandArgs = args.toList().subList(startIndex, args.size)
        initMainClass()
        initWorkspace()
    }

    private fun initMainClass() {
        val thisClass = ::main.javaMethod!!.declaringClass.name
        val prefix = thisClass.take(thisClass.lastIndexOf(".") + 1)
        mainClass?.let { return }
        val suffix = builderNameClassMap[builderName] ?: run {
            error("No builder with name \"$builderName\"!")
        }
        mainClass = "$prefix$suffix"
    }

    private fun initWorkspace() {
        projectPath = projectPath ?: "$workspace/repo"
        workspaceName = workspace.substring(workspace.lastIndexOf("/") + 1)
    }
}

private val builderNameClassMap = mapOf(
    "gradle-library" to "gradle.GradleLibraryBuilder",
    "npm-library" to "npm.NpmLibraryBuilder"
)
