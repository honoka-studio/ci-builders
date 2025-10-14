package de.honoka.ci.builder.npm

import cn.hutool.core.bean.BeanUtil
import cn.hutool.json.JSONObject
import cn.hutool.json.JSONUtil
import de.honoka.ci.builder.gradle.GradleVersionChecker
import java.io.File

class NpmVersionChecker(private val rootProjectPath: String) {

    data class Results(

        var projectsPassed: Boolean = true,

        var dependenciesPassed: Boolean = true
    )

    private val projects = ArrayList<NpmProject>()

    private val results = Results()

    fun check(): Results {
        val rootProject = NpmProject(rootProjectPath)
        if(rootProject.version != null) {
            projects.add(rootProject)
        } else if(File("$rootProjectPath/build.gradle.kts").exists()) {
            println("Checking the root project version by Gradle...\n")
            val gradleResults = GradleVersionChecker(rootProjectPath).check()
            if(!gradleResults.passed) {
                BeanUtil.copyProperties(gradleResults, results)
                return results
            }
        } else {
            error("The version of the root project is not specified!")
        }
        findPackageJson(rootProject)
        separator()
        println("Versions:\n")
        for(p in projects) {
            println("${p.name}=${p.version}")
            if(p.version!!.contains("dev")) {
                results.projectsPassed = false
                break
            }
        }
        separator()
        if(results.projectsPassed) {
            println("Dependencies:\n")
            for(p in projects) {
                if(!results.dependenciesPassed) break
                checkDependencies(p.packageJson.getJSONObject("dependencies"))
                checkDependencies(p.packageJson.getJSONObject("devDependencies"))
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

    private fun findPackageJson(project: NpmProject) {
        File(project.projectPath).listFiles().forEach {
            if(!it.isDirectory || it.name == "node_modules") return@forEach
            runCatching {
                val subProject = NpmProject("${project.projectPath}/${it.name}")
                projects.add(subProject)
                findPackageJson(subProject)
            }
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
