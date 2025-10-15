package de.honoka.ci.builder.gradle

import cn.hutool.core.io.FileUtil
import cn.hutool.core.util.RandomUtil
import de.honoka.ci.builder.envVariables
import de.honoka.ci.builder.util.BuilderConfig
import de.honoka.ci.builder.util.execInProjectPath
import de.honoka.ci.builder.util.execInWorkspace
import de.honoka.ci.builder.util.getEnv
import java.util.concurrent.TimeUnit

@Suppress("unused")
object GradleLibraryBuilder {

    private data class Config(var separateProjects: List<String> = listOf())

    private val config = BuilderConfig.toBean<Config>()

    private lateinit var repositoryName: String

    private val repositoryPath: String
        get() = "${envVariables.workspace}/maven-repo/repository/$repositoryName"

    private var isDevelopmentVersion: Boolean = true

    private val mavenRepoUrl = getEnv("REMOTE_MAVEN_REPO_URL")

    private fun build() {
        val checkVersionResults = GradleVersionChecker(envVariables.projectPath!!).check()
        if(checkVersionResults.projectsPassed && !checkVersionResults.dependenciesPassed) {
            error("Some projects with release version contain dependencies with development version!")
        }
        //打包，并发布到一个空的Maven仓库中
        isDevelopmentVersion = !checkVersionResults.projectsPassed
        repositoryName = if(isDevelopmentVersion) "development" else "release"
        println("\n\nUsing $repositoryName repository to publish artifacts.\n")
        FileUtil.mkdir(repositoryPath)
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
            mkdir ${envVariables.artifactName}
            mv maven-repo.tar.gz ${envVariables.artifactName}/
            mv maven-repo ${envVariables.artifactName}/
        """.trimIndent()
        execInWorkspace(command)
    }

    private fun publishToLocal(project: String? = null) {
        val taskName = if(project == null) "publish" else ":$project:publish"
        val command = """
            ./gradlew -PremoteMavenRepositoryUrl=$repositoryPath \
                      -PisDevelopmentRepository=$isDevelopmentVersion $taskName
        """.trimIndent()
        execInProjectPath(command)
    }

    fun publish() {
        val gitInfo = envVariables.gitInfo!!
        build()
        var commitMessage = "Update ${envVariables.projectName}"
        if(isDevelopmentVersion) {
            commitMessage += " (dev)"
        }
        val command = """
            # 将存储Maven仓库文件的Git仓库clone到workspace下
            git clone "$mavenRepoUrl" maven-repo
            #
            # 将[workspace]/[ARTIFACT_NAME]/maven-repo/repository下所有内容，复制到
            # [workspace]/maven-repo/repository下，并替换已存在的内容。
            #
            cp -rf ${envVariables.artifactName}/maven-repo/repository/. maven-repo/repository/
            # 进入存储Maven仓库文件的Git仓库，设置提交者信息，然后提交并推送
            cd maven-repo/repository
            date > update_time.txt
            git config --global user.name "${gitInfo.username}"
            git config --global user.email "${gitInfo.email}"
            git add .
            git commit -m "$commitMessage"
            git push
            rm -rf ${envVariables.artifactName}/maven-repo
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
                FileUtil.del("${envVariables.workspace}/maven-repo")
            }
        }
        exception?.let { throw it }
    }
}
