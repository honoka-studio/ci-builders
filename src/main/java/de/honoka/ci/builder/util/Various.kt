package de.honoka.ci.builder.util

import cn.hutool.core.io.FileUtil
import de.honoka.ci.builder.envVariables
import org.intellij.lang.annotations.Language
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset

fun getEnv(name: String): String = run {
    getEnvOrNull(name) ?: error("The environment variable $name is not specified!")
}

fun getEnvOrNull(name: String): String? {
    val env = System.getenv(name)
    return if(env.isNullOrBlank()) null else env
}

fun exec(
    @Language("bash")
    command: String,
    workDirectory: String? = null,
    charset: String = "UTF-8"
): String {
    val process = ProcessBuilder("bash").run {
        redirectInput(ProcessBuilder.Redirect.PIPE)
        redirectOutput(ProcessBuilder.Redirect.PIPE)
        redirectErrorStream(true)
        workDirectory?.let {
            directory(File(it))
        }
        start()
    }
    val out = ByteArrayOutputStream()
    process.outputStream.run {
        write("set -e\n\n$command\n\nexit\n".toByteArray())
        flush()
    }
    val redirectThread = Thread {
        while(true) {
            val alive = process.isAlive
            val bytes = process.inputStream.run {
                if(alive) {
                    val avaliable = available()
                    readNBytes(if(avaliable > 0) avaliable else 1)
                } else {
                    readAllBytes()
                }
            }
            System.out.write(bytes)
            out.write(bytes)
            if(!alive) return@Thread
        }
    }
    redirectThread.start()
    process.waitFor()
    if(process.exitValue() != 0) {
        error("Command exited with value ${process.exitValue()}.")
    }
    redirectThread.join()
    return out.toByteArray().toString(Charset.forName(charset))
}

fun execInProjectPath(@Language("bash") command: String): String = run {
    exec(command, envVariables.projectPath)
}

fun execInWorkspace(@Language("bash") command: String): String = run {
    exec(command, envVariables.workspace)
}

fun writeToGithubOutput(key: String, value: Any?) {
    FileUtil.appendUtf8String("$key=$value\n", envVariables.githubOutput)
}
