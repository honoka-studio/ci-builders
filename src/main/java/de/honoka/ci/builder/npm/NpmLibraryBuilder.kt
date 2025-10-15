package de.honoka.ci.builder.npm

import cn.hutool.core.io.FileUtil
import cn.hutool.core.util.RandomUtil
import de.honoka.ci.builder.envVariables
import de.honoka.ci.builder.util.BuilderConfig
import de.honoka.ci.builder.util.execInProjectPath
import de.honoka.ci.builder.util.execInWorkspace
import de.honoka.ci.builder.util.getEnv
import java.io.File
import java.util.concurrent.TimeUnit

@Suppress("unused")
object NpmLibraryBuilder {

    private data class Config(var projectsToPublish: List<String> = listOf())

    private val config = BuilderConfig.toBean<Config>()

    private lateinit var registryName: String

    private var isDevelopmentVersion: Boolean = true

    private val npmRegistryUrl = getEnv("REMOTE_NPM_REGISTRY_URL")

    private val userHome = System.getProperty("user.home").apply {
        if(isNullOrBlank()) error("Cannot get the JVM property user.home!")
    }

    private val localRegistryPath = "$userHome/.local/share/verdaccio/storage"

    private val artifactsPath = "${envVariables.workspace}/${envVariables.artifactName}"

    private var verdaccioProcess: Process? = null

    private fun build() {
        //读取当前npm项目根模块的版本信息，检查版本号是否符合要求
        val checkVersionResults = NpmVersionChecker(envVariables.projectPath!!).check()
        if(checkVersionResults.projectsPassed && !checkVersionResults.dependenciesPassed) {
            error("Some projects with release version contain dependencies with development version!")
        }
        //打包，并发布到本地的npm registry中
        val forceDev = envVariables.commandArgs.getOrNull(0) == "dev"
        isDevelopmentVersion = forceDev || !checkVersionResults.projectsPassed
        registryName = if(isDevelopmentVersion) "development" else "release"
        println("\n\nUsing $registryName repository to publish artifacts.\n")
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
        FileUtil.mkdir(artifactsPath)
        config.projectsToPublish.forEach {
            publishToLocal("${envVariables.projectPath}/$it")
        }
    }

    private fun removeExistingPackage(projectPath: String) {
        NpmProject(projectPath, localRegistryPath).run {
            packageJsonInRegistry ?: return
            packageFile.delete()
            packageJsonInRegistry.run {
                getJSONObject("versions").remove(version)
                getJSONObject("time").remove(version)
                getJSONObject("dist-tags").remove("latest")
                getJSONObject("_attachments").remove(packageFile.name)
            }
            packageJsonFileInRegistry.writeText(packageJsonInRegistry.toStringPretty())
        }
    }

    private fun startVerdaccio() {
        verdaccioProcess?.run {
            if(!isAlive) return@run
            destroy()
            waitFor()
        }
        verdaccioProcess = ProcessBuilder("verdaccio").run {
            redirectOutput(ProcessBuilder.Redirect.DISCARD)
            redirectErrorStream(true)
            directory(File(envVariables.projectPath!!))
            start()
        }
        TimeUnit.SECONDS.sleep(3)
    }

    private fun publishToLocal(projectPath: String) {
        NpmProject(projectPath, localRegistryPath).run {
            val artifactPath = "$artifactsPath/$name"
            FileUtil.mkdir(artifactPath)
            val command = """
                cd $projectPath
                cp -f ${envVariables.workspace}/maven-repo/files/verdaccio/.npmrc.honoka ./
                npm publish --userconfig .npmrc.honoka --registry=http://localhost:4873
                cp -f ${packageFile.path} $artifactPath/
                cp -f ${packageJsonFileInRegistry.path} $artifactPath/
            """.trimIndent()
            execInProjectPath(command)
        }
    }

    fun publish() {
        val gitInfo = envVariables.gitInfo!!
        build()
        var commitMessage = "Update ${envVariables.projectName}"
        if(isDevelopmentVersion) {
            commitMessage += " (dev)"
        }
        val command = """
            # 将本地npm仓库复制到Git仓库中
            cp -f $localRegistryPath/.verdaccio-db.json maven-repo/files/verdaccio/storage/$registryName/
            cp -rf $localRegistryPath/. maven-repo/repository/npm/$registryName/
            rm -f maven-repo/repository/npm/$registryName/.verdaccio-db.json
            # 进入存储Maven仓库文件的Git仓库，设置提交者信息，然后提交并推送
            cd maven-repo
            date > repository/npm/update_time.txt
            git config --global user.name "${gitInfo.username}"
            git config --global user.email "${gitInfo.email}"
            git add repository/npm/$registryName
            git add files/verdaccio/storage/$registryName
            git add repository/npm/update_time.txt
            git commit -m "$commitMessage"
            git push
        """.trimIndent()
        var exception: Throwable? = null
        for(i in 1..3) {
            runCatching {
                execInWorkspace(command)
                return
            }.getOrElse {
                exception = it
                println("\nGit push failed (tried $i). Waiting to retry...\n")
                TimeUnit.SECONDS.sleep(RandomUtil.randomLong(3, 11))
                val command = """
                    rm -rf maven-repo
                    git clone "$npmRegistryUrl" maven-repo
                    mkdir -p maven-repo/repository/npm/$registryName
                """.trimIndent()
                execInWorkspace(command)
            }
        }
        exception?.let { throw it }
    }
}
