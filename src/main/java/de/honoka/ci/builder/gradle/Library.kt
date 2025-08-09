package de.honoka.ci.builder.gradle

import cn.hutool.core.io.FileUtil
import cn.hutool.json.JSONObject
import de.honoka.ci.builder.envVariables
import de.honoka.ci.builder.util.BuilderConfig
import de.honoka.ci.builder.util.execInProjectPath
import de.honoka.ci.builder.util.execInWorkspace
import de.honoka.ci.builder.util.getEnv

@Suppress("unused")
object GradleLibraryBuilder {

    private data class Config(

        var separateProjects: List<String> = listOf(),

        var gitUsername: String? = null,

        var gitEmail: String? = null
    )

    private val config = BuilderConfig.toBean<Config>()

    private lateinit var repositoryName: String

    private var isDevelopmentVersion: Boolean = true

    private val mavenRepoUrl = getEnv("REMOTE_MAVEN_REPO_URL")

    private fun build() {
        //读取当前Gradle项目根模块的版本信息，检查版本号是否符合要求
        val checkTaskOut = execInProjectPath(
            "chmod +x ./gradlew && ./gradlew checkVersionOfProjects"
        )
        val results = JSONObject()
        checkTaskOut.lineSequence().forEach {
            if(!it.contains("results.") || !it.contains("=")) return@forEach
            it.removePrefix("results.").split("=").let { p ->
                results[p[0]] = p[1]
            }
        }
        val projectsPassed = results.getStr("projectsPassed").toBoolean()
        val dependenciesPassed = results.getStr("dependenciesPassed").toBoolean()
        if(projectsPassed && !dependenciesPassed) {
            error("Some projects with release version contain dependencies with development version!")
        }
        //打包，并发布到一个空的Maven仓库中
        repositoryName = if(projectsPassed) "release" else "development"
        isDevelopmentVersion = !projectsPassed
        FileUtil.mkdir("${envVariables.workspace}/maven-repo/repository/$repositoryName")
        /*
         * 若仅在Gradle中指定task的依赖关系，无法保证在B模块依赖A模块时，在A模块的publish任务执行完成之后，
         * 就能在同一次构建的后续任务当中，使B模块能够从本地仓库中找到其所依赖的A模块。
         *
         * 需要根据模块间依赖关系，按顺序多次执行不同的构建。
         */
        config.separateProjects.forEach {
            println("\n\nPublishing $it...\n")
            publishToLocal(it)
        }
        println("\n\nPublishing all projects...\n")
        publishToLocal()
        //将maven-repo/repository目录打包，然后将tar移动到另一个单独的目录中
        val command = """
            find maven-repo/repository -type f -name 'maven-metadata.xml*' -delete
            tar -zcf maven-repo.tar.gz maven-repo/repository
            mkdir maven-repo-changes
            mv maven-repo.tar.gz maven-repo-changes/
            mv maven-repo maven-repo-changes/
        """.trimIndent()
        execInWorkspace(command)
    }

    private fun publishToLocal(project: String? = null) {
        val taskName = if(project == null) "publish" else ":$project:publish"
        val repositoryPath = "${envVariables.workspace}/maven-repo/repository/$repositoryName"
        val command = """
            ./gradlew -PremoteMavenRepositoryUrl=$repositoryPath \
                      -PisDevelopmentRepository=$isDevelopmentVersion $taskName
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
            # 将存储Maven仓库文件的Git仓库clone到workspace下
            git clone "$mavenRepoUrl" maven-repo
            #
            # 将[workspace]/maven-repo-changes/maven-repo/repository下所有内容，复制到
            # [workspace]/maven-repo/repository下，并替换已存在的内容。
            #
            cp -rf maven-repo-changes/maven-repo/repository/. maven-repo/repository/
            rm -rf maven-repo-changes/maven-repo
            # 进入存储Maven仓库文件的Git仓库，设置提交者信息，然后提交并推送
            cd maven-repo/repository
            date > update_time.txt
            git config --global user.name "${config.gitUsername}"
            git config --global user.email "${config.gitEmail}"
            git add .
            git commit -m "$commitMessage"
            git push
        """.trimIndent()
        execInWorkspace(command)
    }
}
