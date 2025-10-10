package com.jamesward.agenticgradleplugin

import kotlinx.serialization.Serializable
import org.gradle.tooling.GradleConnector
import java.io.ByteArrayOutputStream
import java.io.File

@Serializable
sealed interface Result {

    fun getOrThrow(): String = when (this) {
        is Failure -> throw Exception(failure)
        is Success -> success
    }

    @Serializable
    data class Failure(val failure: String) : Result

    @Serializable
    data class Success(val success: String) : Result
}

object GradleTaskRunner {
    fun run(projectDir: File, task: String, args: String? = null): Result =
        ByteArrayOutputStream().use { stdOut ->
            ByteArrayOutputStream().use { stdErr ->
                GradleConnector.newConnector()
                    .forProjectDirectory(projectDir)
                    .connect()
                    .use { connection ->

                        val buildLauncher = connection.newBuild()
                        buildLauncher.setColorOutput(false)
                        buildLauncher.setStandardError(stdErr)
                        buildLauncher.setStandardOutput(stdOut)

                        buildLauncher.forTasks(task)

                        if (args != null)
                            buildLauncher.withArguments(args.split(" "))

                        try {
                            buildLauncher.run()

                            Result.Success(
                                """
                                Gradle task '$task' completed successfully.
                                
                                Output:
                                $stdOut
                            """.trimIndent()
                            )
                        }
                        catch (e: Throwable) {
                            Result.Failure(
                                """
                                    Gradle task '$task' failed: ${e.message}
                                    
                                    Output:
                                    $stdOut
                                    
                                    Error:
                                    $stdErr
                                """.trimIndent(),
                            )
                        }
                    }
            }
        }
}
