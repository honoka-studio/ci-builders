package de.honoka.ci.builder

import de.honoka.ci.builder.util.EnvVariables
import de.honoka.ci.builder.util.initMainClass

var args = listOf<String>()
    private set

fun main(cmdArgs: Array<String>) {
    args = cmdArgs.toList()
    initMainClass()
    val buildClass = Class.forName(EnvVariables.mainClass)
    val method = runCatching {
        buildClass.getDeclaredMethod(args[0])
    }.getOrElse {
        val msg = "No function with name \"${args[0]}\" in class \"${EnvVariables.mainClass}"
        throw Exception(msg)
    }
    args = args.subList(1, args.size)
    method.invoke(null)
}
