package com.jamesward.agenticgradleplugin

import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.StringWriter
import java.io.Writer
import kotlin.test.*

// todo: requires env vars for one of the providers:
//    AWS_BEARER_TOKEN_BEDROCK or (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY)
//    ANTHROPIC_API_KEY
//    OPENAI_API_KEY

class AgenticGradlePluginTest {


    // instead of using the `withPluginClasspath` thing, we instead include the plugin and deps manually as buildscript dependencies
    //   because the inner Gradle build inside the AgenticTask doesn't get the classpath of the outer build
    fun createGradleConfigForTestsThatCallGradleTasks(tmpDir: File, agenticBlock: () -> String) {
        val pluginClasspath = DefaultClassPath.of(PluginUnderTestMetadataReading.readImplementationClasspath())

        val classpathLines = pluginClasspath.asFiles.joinToString("\n") { """classpath(files("${it.absolutePath}"))""" }

        val buildFile = tmpDir.resolve("build.gradle.kts")

        buildFile.writeText("""
            buildscript {
                dependencies {
                    $classpathLines
                }
            }

            apply<com.jamesward.agenticgradleplugin.AgenticGradlePlugin>()

            plugins {
                java
                // does not work
                //    id("com.jamesward.agentic-gradle-plugin")
            }

            configure<com.jamesward.agenticgradleplugin.AgenticExtension> {
                ${agenticBlock()}
            }
        """.trimIndent())
    }

    @Test
    fun `runAgent works`(@TempDir tmpDir: File) {
        val buildFile = tmpDir.resolve("build.gradle.kts")
        buildFile.writeText("""
            plugins {
                id("com.jamesward.agentic-gradle-plugin")
            }

            agentic {
                create("hello") {
                    prompt = "say hello"
                }
            }
        """.trimIndent())

        val stdout: Writer = StringWriter()

        val result = GradleRunner.create()
            .withProjectDir(tmpDir)
            .withArguments("hello")
            .withPluginClasspath()
            .forwardStdOutput(stdout)
            .build()

        assert(result.output.contains("AgenticTask: output=", ignoreCase = true))
        assert(result.output.contains("hello", ignoreCase = true))

        assert(result.task(":hello")?.outcome == TaskOutcome.SUCCESS)
    }

    @Test
    fun `runAgent can read and write files`(@TempDir tmpDir: File) {
        val readmeFile = tmpDir.resolve("README.md")
        val readmeContents = "This is a readme"
        readmeFile.writeText(readmeContents)

        val buildFile = tmpDir.resolve("build.gradle.kts")
        buildFile.writeText("""
            plugins {
                id("com.jamesward.agentic-gradle-plugin")
            }

            agentic {
                create("hello") {
                    inputFile = layout.projectDirectory.file("README.md")
                    prompt = "add more details to the readme"
                }
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(tmpDir)
            .withArguments("hello")
            .withPluginClasspath()
            .forwardOutput()
            .build()

//        println(readmeFile.readText())

        assert(readmeFile.readText() != readmeContents)

        assert(result.task(":hello")?.outcome == TaskOutcome.SUCCESS)
    }

    @Test
    fun `runAgent can run gradle tasks`(@TempDir tmpDir: File) {

        createGradleConfigForTestsThatCallGradleTasks(tmpDir) {
            """
                create("runTask") {
                    debug = true
                    prompt = "run the 'classes' gradle task"
                }
            """
            }

        val result = GradleRunner.create()
            .withProjectDir(tmpDir)
            .withArguments("runTask")
            .forwardOutput()
            .build()

        assert(result.output.contains("Task :classes UP-TO-DATE"))
        assert(result.output.contains("BUILD SUCCESSFUL"))

        assert(result.task(":runTask")?.outcome == TaskOutcome.SUCCESS)
    }

    @Test
    fun `runAgent can run gradle tasks that fail`(@TempDir tmpDir: File) {
        val javaSrcDir = tmpDir.resolve("src/main/java")
        javaSrcDir.mkdirs()
        val javaSrc = javaSrcDir.resolve("Foo.java")
        javaSrc.writeText("bad java")

        createGradleConfigForTestsThatCallGradleTasks(tmpDir) {
            """
                create("runTask") {
                    debug = true
                    prompt = "run the 'classes' gradle task"
                }
            """
        }

        val result = GradleRunner.create()
            .withProjectDir(tmpDir)
            .withArguments("runTask")
            .forwardOutput()
            .build()

        assert(result.output.contains("Task :compileJava FAILED"))
        assert(result.output.contains("BUILD SUCCESSFUL"))

        assert(result.task(":runTask")?.outcome == TaskOutcome.SUCCESS)
    }

    @Test
    fun `runAgent can list gradle tasks`(@TempDir tmpDir: File) {

        val buildFile = tmpDir.resolve("build.gradle.kts")
        buildFile.writeText("""
            plugins {
                id("com.jamesward.agentic-gradle-plugin")
            }

            agentic {
                create("listTasks") {
                    debug = true
                    prompt = "list the gradle tasks - just the task names"
                }
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(tmpDir)
            .withArguments("listTasks")
            .withPluginClasspath()
            .forwardOutput()
            .build()

        assert(result.output.contains("dependencies"))

        assert(result.task(":listTasks")?.outcome == TaskOutcome.SUCCESS)
    }

    @Test
    fun `runAgent can do validation`(@TempDir tmpDir: File) {

        createGradleConfigForTestsThatCallGradleTasks(tmpDir) {
            """
                create("doValidation") {
                    debug = true
                    prompt = "say hello"
                    validationTask = "test"
                }
            """
        }

        val result = GradleRunner.create()
            .withProjectDir(tmpDir)
            .withArguments("doValidation")
            .forwardOutput()
            .build()

        // todo, validate tests ran
        assert(result.output.contains("BUILD SUCCESSFUL"))

        assert(result.task(":doValidation")?.outcome == TaskOutcome.SUCCESS)
    }

}
