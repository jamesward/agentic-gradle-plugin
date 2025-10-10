package com.jamesward.agenticgradleplugin

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*


class GradleTaskRunnerTest {

    @Test
    fun `GradleRunner runs tasks`(@TempDir tmpDir: File) {
        val buildFile = tmpDir.resolve("build.gradle.kts")
        buildFile.writeText("""
            plugins {
                java
            }
        """.trimIndent())


        val result = GradleTaskRunner.run(tmpDir, "tasks")

        assert(result.getOrThrow().contains("Tasks runnable from root project"))
        assert(!result.getOrThrow().contains("compileJava - Compiles main Java source."))
    }

    @Test
    fun `GradleRunner handles task failure`(@TempDir tmpDir: File) {
        val buildFile = tmpDir.resolve("build.gradle.kts")
        buildFile.writeText("""
            plugins {
                java
            }
        """.trimIndent())


        val result = GradleTaskRunner.run(tmpDir, "asdf")

        assert(result.getOrThrow().contains("Task 'asdf' not found in root project"))
    }

    // not sure yet how to test this as --all is not the right kind of argument
    /*
    @Test
    fun `GradleRunner works with args`(@TempDir tmpDir: File) {
        val buildFile = tmpDir.resolve("build.gradle.kts")
        buildFile.writeText("""
            plugins {
                java
            }
        """.trimIndent())


        val result = GradleTaskRunner.run(tmpDir, "tasks", "--all")

        assert(result.contains("compileJava - Compiles main Java source."))
    }
     */

}
