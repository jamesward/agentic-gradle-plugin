package com.jamesward.agenticgradleplugin

import ai.koog.agents.core.agent.*
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
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

    // todo: enable override per task
    fun maxIterations(): Int = validationTask.orNull?.let { 100 } ?: 50

    private val agent by lazy {
        val ext = project.extensions.getByType<AgenticExtension>()
        val debugLogLevel = if (ext.debug.orNull ?: false) LogLevel.LIFECYCLE else LogLevel.DEBUG
        println("debugLogLevel=$debugLogLevel")

        val provider = ext.provider.orNull ?:
            runCatching {
                ext.bedrock()
            }.recoverCatching {
                ext.anthropic()
            }.recoverCatching {
                ext.openai()
            }.getOrNull() ?: throw IllegalArgumentException("provider must be provided")

        val gradleTools = AgenticGradleTools(logger, debugLogLevel, project)

        val toolRegistry = ToolRegistry {
            tools(gradleTools)
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
        """.trimIndent()

        logger.log(debugLogLevel, systemPrompt)

        // subgraph the optional validation task so that we can reset the history
        //   no need to continue passing it when we get to validation

        // we maybe should throw parallel tool execution in too

        val strategy = functionalStrategy<String, String> { input ->
            var responses = requestLLMMultiple(input)
            logger.log(debugLogLevel, "responses=$responses")

            while (responses.containsToolCalls()) {
                val pendingCalls = extractToolCalls(responses)
                logger.log(debugLogLevel, "pendingCalls=$pendingCalls")
                val results = executeMultipleTools(pendingCalls, true)
                logger.log(debugLogLevel, "results=$results")
                responses = sendMultipleToolResults(results)
                logger.log(debugLogLevel, "responses=$responses")
            }

            // fugly - why aren't thinking blocks individual messages?
            val resultContent = responses.single().asAssistantMessage().content.split("</thinking>").last().trim()

            /*
            validationTask.orNull?.let { validationTaskValue ->

                while ()

                gradleTools.runGradleTask(validationTaskValue)
            }
             */


            resultContent
        }

        AIAgent(
            toolRegistry = toolRegistry,
            agentConfig = AIAgentConfig(
                prompt = prompt(
                    id = "agentic-gradle-plugin",
                    params = LLMParams(
                        includeThoughts = false,
                    )
                ) {
                    system(systemPrompt)
                },
                model = provider.model,
                maxAgentIterations = maxIterations(),
            ),
            promptExecutor = provider.executor,
            strategy = strategy,
        )
    }


    @TaskAction
    fun runAgent() {
        val promptValue = prompt.orNull ?: throw IllegalArgumentException("prompt must be provided")

        logger.debug("AgenticTask: prompt=$promptValue provider=${agent.agentConfig.model.provider}")

        // todo: stream output / iterations so the user see progress?
        val output = runBlocking {
            agent.run(promptValue)
        }

        logger.lifecycle("AgenticTask: output=$output")
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
    fun listGradleTasks(): Map<String, String> = run {
        logger.log(debugLogLevel, "Listing gradle tasks")

        project.tasks.associate {
            it.path to (it.description ?: "")
        }
    }

    @Tool
    @LLMDescription("Run a Gradle task. If it fails try to fix the problem.")
    fun runGradleTask(task: String, arguments: String? = null): Result = run {
        logger.log(debugLogLevel, "Running Gradle task: $task")
        GradleTaskRunner.run(project.projectDir, task, arguments)
    }
}
