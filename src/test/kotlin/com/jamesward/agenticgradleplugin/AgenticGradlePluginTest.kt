package com.jamesward.agenticgradleplugin

import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*


class AgenticGradlePluginTest {

    /*
    // todo: requires ANTHROPIC_API_KEY env var to be set
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
                provider = com.jamesward.agenticgradleplugin.Anthropic()
            }

            tasks.register<com.jamesward.agenticgradleplugin.AgenticTask>("hello") {
                inputFile = layout.projectDirectory.file("README.md")
                prompt = "add more details to the readme"
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(tmpDir)
            .withArguments("hello")
            .withPluginClasspath()
            .build()

        println(result.output)

//        println(readmeFile.readText())

        assert(readmeFile.readText() != readmeContents)

        assert(result.task(":hello")?.outcome == org.gradle.testkit.runner.TaskOutcome.SUCCESS)
    }
    */

    // todo: have it fix something when validation fails
    // todo: requires ANTHROPIC_API_KEY env var to be set
    @Test
    fun `runAgent can do validation`(@TempDir tmpDir: File) {

        // instead of using the `withPluginClasspath` thing, we instead include the plugin and deps manually as buildscript dependencies
        //   because the inner Gradle build inside the AgenticTask doesn't get the classpath of the outer build

        val pluginClasspath = DefaultClassPath.of(PluginUnderTestMetadataReading.readImplementationClasspath());

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
                provider = anthropic()
                create("doValidation") {
                    debug = true
                    prompt = "just do the validation"
                    validationTask = "test"
                }
            }

            /*
            agentic {
                provider = com.jamesward.agenticgradleplugin.Anthropic()
            }
            */
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(tmpDir)
            .withArguments("doValidation")
//            .withPluginClasspath()
            .forwardOutput()
            .build()

        assert(result.output.contains("BUILD SUCCESSFUL"))

        assert(result.task(":doValidation")?.outcome == TaskOutcome.SUCCESS)
    }

    /*
    // todo: requires ANTHROPIC_API_KEY env var to be set
    @Test
    fun `runAgent can run gradle tasks`(@TempDir tmpDir: File) {

        // instead of using the `withPluginClasspath` thing, we instead include the plugin and deps manually as buildscript dependencies
        //   because the inner Gradle build inside the AgenticTask doesn't get the classpath of the outer build

        val pluginClasspath = DefaultClassPath.of(PluginUnderTestMetadataReading.readImplementationClasspath());

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
                provider = anthropic()
                create("runTask") {
                    debug = true
                    prompt = "run the 'classes' gradle task"
                }
            }

            /*
            agentic {
                provider = com.jamesward.agenticgradleplugin.Anthropic()
            }
            */
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(tmpDir)
            .withArguments("runTask")
//            .withPluginClasspath()
            .forwardOutput()
            .build()

        assert(result.output.contains("BUILD SUCCESSFUL"))

        assert(result.task(":runTask")?.outcome == TaskOutcome.SUCCESS)
    }
     */

    /*
    // todo: requires ANTHROPIC_API_KEY env var to be set
    @Test
    fun `runAgent can list gradle tasks`(@TempDir tmpDir: File) {
        val buildFile = tmpDir.resolve("build.gradle.kts")
        buildFile.writeText("""
            plugins {
                id("com.jamesward.agentic-gradle-plugin")
            }

            agentic {
                provider = com.jamesward.agenticgradleplugin.Anthropic()
            }

            tasks.register<com.jamesward.agenticgradleplugin.AgenticTask>("listTasks") {
                prompt = "list the gradle tasks - just the task names"
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(tmpDir)
            .withArguments("listTasks")
            .withPluginClasspath()
            .build()

        assert(result.output.contains("dependencies"))

        assert(result.task(":listTasks")?.outcome == TaskOutcome.SUCCESS)
    }
     */

}
