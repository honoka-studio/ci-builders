package de.honoka.ci.builder

import cn.hutool.core.exceptions.ExceptionUtil
import cn.hutool.json.JSONUtil
import de.honoka.ci.builder.util.EnvVariables
import kotlin.reflect.full.memberFunctions

val envVariables = EnvVariables()

fun main(args: Array<String>) {
    envVariables.initArgs(args)
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
    runCatching {
        function.call(buildClass.objectInstance)
    }.getOrElse {
        throw ExceptionUtil.getRootCause(it)
    }
}
