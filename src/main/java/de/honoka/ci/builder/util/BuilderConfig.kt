package de.honoka.ci.builder.util

import cn.hutool.json.JSONObject
import cn.hutool.json.JSONUtil
import de.honoka.ci.builder.envVariables
import java.io.File

object BuilderConfig {

    private val filePath = "${envVariables.projectPath}/.github/workflows/ci-builders-config.json"

    val content: JSONObject = File(filePath).let {
        if(it.exists()) JSONUtil.parseObj(it.readText()) else JSONObject()
    }

    inline fun <reified T> toBean(): T = content.toBean(T::class.java)
}
