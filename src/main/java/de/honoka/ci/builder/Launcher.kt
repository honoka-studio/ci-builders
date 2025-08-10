package de.honoka.ci.builder

import cn.hutool.core.exceptions.ExceptionUtil
import cn.hutool.json.JSONUtil
import de.honoka.ci.builder.util.EnvVariables
import kotlin.reflect.full.memberFunctions

lateinit var envVariables: EnvVariables
    private set

fun main(args: Array<String>) {
    runCatching {
        envVariables = EnvVariables()
        envVariables.initArgs(args)
        launch()
    }.getOrElse {
        throw ExceptionUtil.getRootCause(it)
    }
}

private fun launch() {
    val json = JSONUtil.parse(envVariables).run {
        config.isIgnoreNullValue = false
        toStringPretty()
    }
    println("Environment Variables:\n$json\n\n")
    val buildClass = Class.forName(envVariables.mainClass).kotlin
    val function = buildClass.memberFunctions.firstOrNull { it.name == envVariables.functionName }
    function ?: run {
        error("No function with name \"${envVariables.functionName}\" in class \"${envVariables.mainClass}")
    }
    function.call(buildClass.objectInstance)
}
