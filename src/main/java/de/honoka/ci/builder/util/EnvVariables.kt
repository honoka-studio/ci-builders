package de.honoka.ci.builder.util

import de.honoka.ci.builder.main
import kotlin.reflect.jvm.javaMethod

object EnvVariables {

    val builderName: String? = getEnv("BUILDER_NAME")

    var mainClass: String? = getEnv("BUILDER_MAIN_CLASS")
}

private val builderNameClassMap = mapOf(
    "gradle-library" to "gradle.library.LibraryKt",
    "npm-library" to "npm.library.LibraryKt"
)

private lateinit var rootPackage: String

private fun getEnv(name: String): String? {
    val env = System.getenv(name)
    return if(env.isNullOrBlank()) null else env
}

fun initMainClass() {
    val thisClass = ::main.javaMethod!!.declaringClass.name
    rootPackage = thisClass.take(thisClass.lastIndexOf("."))
    EnvVariables.run {
        if(mainClass != null) return
        builderName ?: throw Exception("No BUILDER_NAME or BUILDER_MAIN_CLASS specified!")
        val suffix = builderNameClassMap[builderName] ?: run {
            throw Exception("No builder with name \"$builderName\"!")
        }
        mainClass = "${rootPackage}.$suffix"
    }
}
