package de.honoka.ci.builder.npm

import cn.hutool.json.JSONObject
import cn.hutool.json.JSONUtil
import java.io.File

class NpmProject(val projectPath: String, registryPath: String = "/home/npm-registry") {

    val packageJsonFile = File("$projectPath/package.json")

    val packageJson: JSONObject = packageJsonFile.let {
        if(!it.exists()) error("The directory \"$projectPath\" is not a npm project.")
        JSONUtil.parseObj(it.readText())
    }

    val name: String = packageJson.getStr("name")

    val version: String = packageJson.getStr("version")

    val packageName: String = name.run {
        if(contains("/")) {
            substring(indexOf("/") + 1)
        } else {
            this
        }
    }

    val packagePath = "$registryPath/$name"

    val packageFile = File("$packagePath/$packageName-${packageJson["version"]}.tgz")

    val packageJsonFileInRegistry = File("$packagePath/package.json")

    val packageJsonInRegistry: JSONObject? = packageJsonFileInRegistry.run {
        if(exists()) {
            JSONUtil.parseObj(readText()).apply {
                config.isIgnoreNullValue = false
            }
        } else {
            null
        }
    }
}
