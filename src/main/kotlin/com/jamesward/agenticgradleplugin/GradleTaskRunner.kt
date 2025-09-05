package com.jamesward.agenticgradleplugin

import org.gradle.tooling.GradleConnector
import java.io.ByteArrayOutputStream
import java.io.File

object GradleTaskRunner {
    fun run(projectDir: File, task: String, args: String? = null): String =
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
                            """
                                Gradle task '$task' completed successfully.
                                
                                Output:
                                $stdOut
                            """.trimIndent()
                        }
                        catch (e: Exception) {
                            """
                                Gradle task '$task' failed: ${e.message}
                                
                                Output:
                                $stdOut
                                
                                Error:
                                $stdErr
                            """.trimIndent()
                        }
                    }
            }
        }
}
