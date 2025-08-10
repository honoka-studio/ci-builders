package de.honoka.ci.builder.util.npm

import cn.hutool.json.JSONObject
import cn.hutool.json.JSONUtil
import java.io.File

class NpmVersionChecker(private val rootProjectPath: String) {

    data class Results(

        var projectsPassed: Boolean = true,

        var dependenciesPassed: Boolean = true
    )

    private val packageJsonPaths = ArrayList<String>()

    private val packageJsons = ArrayList<JSONObject>()

    private val results = Results()

    fun check(): Results {
        val packageJsonFile = File("$rootProjectPath/package.json").apply {
            if(exists()) return@apply
            error("The directory \"$rootProjectPath\" is not a root project.")
        }
        packageJsonPaths.add(packageJsonFile.path)
        findPackageJson(rootProjectPath)
        separator()
        println("Versions:\n")
        for(path in packageJsonPaths) {
            val json = JSONUtil.parseObj(File(path).readText())
            packageJsons.add(json)
            println("${json["name"]}=${json["version"]}")
            if(json.getStr("version").contains("dev")) {
                results.projectsPassed = false
                break
            }
        }
        separator()
        if(results.projectsPassed) {
            println("Dependencies:\n")
            for(json in packageJsons) {
                if(!results.dependenciesPassed) break
                checkDependencies(json.getJSONObject("dependencies"))
                checkDependencies(json.getJSONObject("devDependencies"))
            }
            separator()
        }
        println("Results:\n")
        println(JSONUtil.toJsonPrettyStr(results))
        separator()
        return results
    }

    private fun separator() {
        println("-----------------------------------")
    }

    private fun findPackageJson(projectPath: String) {
        File(projectPath).listFiles().forEach {
            if(!it.isDirectory || it.name == "node_modules") return@forEach
            val json = File("$projectPath/${it.name}/package.json")
            if(json.exists()) {
                packageJsonPaths.add(json.path)
            }
            findPackageJson("$projectPath/${it.name}")
        }
    }

    private fun checkDependencies(dependencies: JSONObject?) {
        if(dependencies == null || !results.dependenciesPassed) return
        dependencies.entries.forEach {
            val value = it.value as String?
            println("${it.key}=$value")
            if(value?.contains("dev") == true) {
                results.dependenciesPassed = false
                return
            }
        }
    }
}
