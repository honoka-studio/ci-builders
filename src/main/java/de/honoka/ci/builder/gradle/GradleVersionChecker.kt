package de.honoka.ci.builder.gradle

import cn.hutool.json.JSONObject
import de.honoka.ci.builder.util.exec

class GradleVersionChecker(private val rootProjectPath: String) {

    data class Results(

        var projectsPassed: Boolean = false,

        var dependenciesPassed: Boolean = false,

        var passed: Boolean = false
    )

    fun check(): Results {
        //读取当前Gradle项目根模块的版本信息，检查版本号是否符合要求
        val checkTaskOut = exec(
            "chmod +x ./gradlew && ./gradlew checkVersionOfProjects",
            rootProjectPath
        )
        val jsonResults = JSONObject()
        checkTaskOut.lineSequence().forEach {
            if(!it.contains("results.") || !it.contains("=")) return@forEach
            it.removePrefix("results.").split("=").let { p ->
                jsonResults[p[0]] = p[1].runCatching {
                    toBooleanStrict()
                }.getOrElse {
                    p[1]
                }
            }
        }
        return jsonResults.toBean(Results::class.java)
    }
}
