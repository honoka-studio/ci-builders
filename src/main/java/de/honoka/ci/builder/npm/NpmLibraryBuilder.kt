package de.honoka.ci.builder.npm

import cn.hutool.json.JSONUtil
import de.honoka.ci.builder.envVariables
import de.honoka.ci.builder.util.BuilderConfig
import de.honoka.ci.builder.util.execInProjectPath
import de.honoka.ci.builder.util.execInWorkspace
import de.honoka.ci.builder.util.getEnv
import de.honoka.ci.builder.util.npm.NpmVersionChecker
import java.io.File
import java.util.concurrent.TimeUnit

@Suppress("unused")
object NpmLibraryBuilder {

    private data class Config(

        var projectsToPublish: List<String> = listOf(),

        var gitUsername: String? = null,

        var gitEmail: String? = null
    )

    private val config = BuilderConfig.toBean<Config>()

    private lateinit var registryName: String

    private var isDevelopmentVersion: Boolean = true

    private val npmRegistryUrl = getEnv("REMOTE_NPM_REGISTRY_URL")

    private val userHome = System.getProperty("user.home").apply {
        if(isNullOrBlank()) error("Cannot get the JVM property user.home!")
    }

    private val localRegistryPath = "$userHome/.local/share/verdaccio/storage"

    private var verdaccioProcess: Process? = null

    private fun build() {
        //读取当前npm项目根模块的版本信息，检查版本号是否符合要求
        val checkVersionResults = NpmVersionChecker(envVariables.projectPath!!).check()
        if(checkVersionResults.projectsPassed && !checkVersionResults.dependenciesPassed) {
            error("Some projects with release version contain dependencies with development version!")
        }
        registryName = if(checkVersionResults.projectsPassed) "release" else "development"
        isDevelopmentVersion = !checkVersionResults.projectsPassed
        val command = """
            # 将存储npm仓库文件的Git仓库clone到workspace下
            git clone "$npmRegistryUrl" maven-repo
            mkdir -p $userHome/.config/verdaccio
            mkdir -p $localRegistryPath
            mkdir -p maven-repo/repository/npm/$registryName
            # 还原verdaccio的环境
            npm install -g verdaccio@6.1.6
            cp -f maven-repo/files/verdaccio/htpasswd $userHome/.config/verdaccio/
            cp -rf maven-repo/files/verdaccio/storage/$registryName/. $localRegistryPath/
            cp -rf maven-repo/repository/npm/$registryName/. $localRegistryPath/
        """.trimIndent()
        execInWorkspace(command)
        //移除仓库中已存在的与当前项目中的模块版本相同的包
        config.projectsToPublish.forEach {
            removeExistingPackage("${envVariables.projectPath}/$it")
        }
        startVerdaccio()
        config.projectsToPublish.forEach {
            publishToLocal(it)
        }
    }

    private fun removeExistingPackage(projectPath: String) {
        val file = File("$projectPath/package.json").apply {
            if(!exists()) error("The directory \"$projectPath\" is not a npm project.")
        }
        val json = JSONUtil.parseObj(file.readText())
        val packageName = json.getStr("name").run {
            if(contains("/")) {
                substring(indexOf("/") + 1)
            } else {
                this
            }
        }
        val packagePath = "$localRegistryPath/${json["name"]}"
        if(!File(packagePath).exists()) return
        val tgzName = "$packageName-${json["version"]}.tgz"
        File("$packagePath/$tgzName").delete()
        val infoFile = File("$packagePath/package.json")
        val info = JSONUtil.parseObj(infoFile.readText()).apply {
            config.isIgnoreNullValue = false
        }
        info.run {
            getJSONObject("versions").remove(json["version"])
            getJSONObject("time").remove(json["version"])
            getJSONObject("dist-tags").remove("latest")
            getJSONObject("_attachments").remove(tgzName)
        }
        infoFile.writeText(info.toStringPretty())
    }

    private fun startVerdaccio() {
        verdaccioProcess?.run {
            if(!isAlive) return@run
            destroy()
            waitFor()
        }
        verdaccioProcess = ProcessBuilder("verdaccio").run {
            redirectInput(ProcessBuilder.Redirect.DISCARD)
            redirectOutput(ProcessBuilder.Redirect.DISCARD)
            redirectErrorStream(true)
            directory(File(envVariables.projectPath!!))
            start()
        }
        TimeUnit.SECONDS.sleep(3)
    }

    private fun publishToLocal(projectName: String) {
        val command = """
            cd ${envVariables.projectPath}/$projectName
            cp -f ${envVariables.workspace}/maven-repo/files/verdaccio/.npmrc.honoka ./
            npm publish --userconfig .npmrc.honoka --registry=http://localhost:4873
        """.trimIndent()
        execInProjectPath(command)
    }

    fun publish() {
        build()
        var commitMessage = "Update ${envVariables.workspaceName}"
        if(isDevelopmentVersion) {
            commitMessage += " (dev)"
        }
        val command = """
            # 将本地npm仓库复制到Git仓库中
            mv -f $localRegistryPath/.verdaccio-db.json maven-repo/files/verdaccio/storage/$registryName/
            cp -rf $localRegistryPath/. maven-repo/repository/npm/$registryName/
            # 进入存储Maven仓库文件的Git仓库，设置提交者信息，然后提交并推送
            cd maven-repo
            date > repository/npm/update_time.txt
            git config --global user.name "${config.gitUsername}"
            git config --global user.email "${config.gitEmail}"
            git add repository/npm/$registryName
            git add files/verdaccio/storage/$registryName
            git add repository/npm/update_time.txt
            git commit -m "$commitMessage"
            git push
        """.trimIndent()
        execInWorkspace(command)
    }
}
