package com.jamesward.agenticgradleplugin

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.features.eventHandler.feature.handleEvents
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByType


abstract class AgenticTask : DefaultTask() {

    @get:Input
    abstract val prompt: Property<String>

    @get:Input
    @get:Optional
    abstract val validationTask: Property<String>

    @get:InputFile
    @get:Optional
    abstract val inputFile: RegularFileProperty

    // todo
    @get:InputDirectory
    @get:Optional
    abstract val inputDirectory: DirectoryProperty

    // todo
    @get:InputFiles
    @get:Optional
    abstract val inputFileCollection: ConfigurableFileCollection


    fun inputFilePrompt(): String =
        inputFile.orNull?.let {
            "You have access to this file: $it"
        } ?: ""

    fun inputDirectoryPrompt(): String =
        inputDirectory.orNull?.let {
            "You have access to this directory: $it"
        } ?: ""

    fun inputFileCollectionPrompt(): String =
        inputFileCollection.files.joinToString("\n") {
            "You have access to these files: $it"
        }

    fun validationTaskPrompt(): String =
        validationTask.orNull?.let {
            "Validate changes by running the gradle task: $it"
        } ?: "You do NOT need to validate your changes"

    // todo: enable override per task
    fun maxIterations(): Int = validationTask.orNull?.let { 40 } ?: 10

    @TaskAction
    fun runAgent() {
        val ext = project.extensions.getByType<AgenticExtension>()
        val debugLogLevel = if (ext.debug.orNull ?: false) LogLevel.LIFECYCLE else LogLevel.DEBUG

        val provider = ext.provider.orNull ?: throw IllegalArgumentException("provider must be provided")
        val promptValue = prompt.orNull ?: throw IllegalArgumentException("prompt must be provided")

        logger.log(debugLogLevel, "AgenticTask: prompt=$promptValue provider=$provider")

        val toolRegistry = ToolRegistry {
            tools(AgenticGradleTools(logger, debugLogLevel, project))
        }

        val baseSystemPrompt = """
            Do not ask the user questions.
            Just try your best to accomplish what they ask.
            A project can have either build.gradle.kts or build.gradle but not both.
        """.trimIndent()

        val systemPrompt = """
            $baseSystemPrompt
            ${inputFilePrompt()}
            ${inputDirectoryPrompt()}
            ${inputFileCollectionPrompt()}
            ${validationTaskPrompt()}
        """.trimIndent()

        logger.log(debugLogLevel, systemPrompt)

        val agent = AIAgent(
            systemPrompt = systemPrompt,
            executor = provider.executor,
            llmModel = provider.model,
            toolRegistry = toolRegistry,
            maxIterations = maxIterations(),
        ) {
            handleEvents {
                onToolCall { eventContext ->
                    logger.log(debugLogLevel, "AgenticTask: onToolCall: ${eventContext.tool.name} with args ${eventContext.toolArgs}")
                }
                onBeforeLLMCall { eventContext ->
                    logger.log(debugLogLevel, "AgenticTask: onBeforeLLMCall: ${eventContext.prompt}")
                }
                onAfterLLMCall { eventContext ->
                    logger.log(debugLogLevel, "AgenticTask: onAfterLLMCall: ${eventContext.responses}")
                }
            }
        }

        // todo: stream output / iterations so the user see progress
        runBlocking {
            val output = agent.run(promptValue)
            logger.log(debugLogLevel, "AgenticTask: output=$output")
        }
    }
}

class AgenticGradleTools(val logger: Logger, val debugLogLevel: LogLevel, val project: Project) : ToolSet {

    // todo: only files in project dir
    @Tool
    fun listFilesInDirectory(path: String): List<String> = run {
        logger.log(debugLogLevel, "listDirectory: path=$path")
        project.file(path).listFiles()?.map { it.name } ?: emptyList()
    }

    // todo: only files in project dir
    @Tool
    fun fileExists(path: String): Boolean = run {
        logger.log(debugLogLevel, "fileExists: path=$path")
        project.file(path).exists()
    }

    // todo: only files in project dir
    @Tool
    fun readFile(path: String): String = run {
        logger.log(debugLogLevel, "readFile: path=$path")
        val file = project.file(path)
        file.readText()
    }

    // todo: only files in project dir
    @Tool
    fun writeFile(path: String, contents: String): Unit = run {
        logger.log(debugLogLevel, "writeFile: path=$path contents=$contents")
        val file = project.file(path)
        file.parentFile.mkdirs()
        file.writeText(contents)
    }

    @Tool
    @LLMDescription("List Gradle tasks")
    fun listGradleTasks(): Map<String, String> =
        project.tasks.associate {
            it.path to (it.description ?: "")
        }

    @Tool
    @LLMDescription("Run a Gradle task. If it fails try to fix the problem.")
    fun runGradleTask(task: String, arguments: String? = null): String = run {
        logger.log(debugLogLevel, "Running Gradle task: $task")
        GradleTaskRunner.run(project.projectDir, task, arguments)
    }
}
