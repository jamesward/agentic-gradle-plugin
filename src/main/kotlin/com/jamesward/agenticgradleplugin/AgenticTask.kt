package com.jamesward.agenticgradleplugin

import ai.koog.agents.core.agent.*
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
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

    @get:Input
    @get:Optional
    abstract val maxIterations: Property<Int>

    @get:InputFile
    @get:Optional
    abstract val inputFile: RegularFileProperty

    // optional input directory for context
    @get:InputDirectory
    @get:Optional
    abstract val inputDirectory: DirectoryProperty

    // optional additional input files for context
    @get:InputFiles
    @get:Optional
    abstract val inputFileCollection: ConfigurableFileCollection

    // todo: validate this is used with the functionalStrategy
    fun maxIterations(): Int = maxIterations.orNull
        ?: (validationTask.orNull?.let { 100 } ?: 50)

    private val agent by lazy {
        val ext = project.extensions.getByType<AgenticExtension>()
        val debugLogLevel = if (ext.debug.orNull ?: false) LogLevel.LIFECYCLE else LogLevel.DEBUG
//        println("debugLogLevel=$debugLogLevel")

        val provider = ext.provider.orNull ?:
            runCatching {
                ext.bedrock()
            }.recoverCatching {
                ext.anthropic()
            }.recoverCatching {
                ext.openai()
            }.getOrNull() ?: throw IllegalArgumentException("provider must be provided")

        val gradleTools = AgenticGradleToolsLive(logger, debugLogLevel, project)

        val toolRegistry = ToolRegistry {
            tools(gradleTools)
        }

        val systemPrompt = systemPrompt(inputFile, inputDirectory, inputFileCollection)

        logger.log(debugLogLevel, systemPrompt)

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
            strategy = strategy(logger, debugLogLevel, validationTask.orNull, gradleTools),
            installFeatures = {
                install(EventHandler) {
                    onLLMCallStarting { eventContext ->
                        println(eventContext.toString())
                    }
                    onLLMCallCompleted { eventContext ->
                        println(eventContext.toString())
                    }
                }
            }
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

    companion object {
        val baseSystemPrompt =
            """
                Do not ask the user questions.
                You are being run in an existing Gradle project.
                You only need to know about the existing build file if you are going to make changes to it.
                Build files are either build.gradle or build.gradle.kts - verify which one is used and read it before making changes.
                Do not create a new build file.
                When modifying an existing build, do not remove any existing code.
                You do not need to list or verify the existing Gradle tasks before running a task specified by the user. Run the exact task the user has requested.
            """.trimIndent()

        fun systemPrompt(inputFile: RegularFileProperty, inputDirectory: DirectoryProperty, inputFileCollection: ConfigurableFileCollection) = run {
            val inputFilePrompt = inputFile.orNull?.let {
                "You have access to this file: $it"
            } ?: ""

            val inputDirectoryPrompt = inputDirectory.orNull?.let {
                "You have access to this directory: $it"
            } ?: ""

            val inputFileCollectionPrompt = inputFileCollection.files.joinToString("\n") {
                "You have access to these files: $it"
            }

            """
                $baseSystemPrompt
                $inputFilePrompt
                $inputDirectoryPrompt
                $inputFileCollectionPrompt
            """.trimIndent().trimStart().trimEnd()
        }

        fun Logger.log(logLevel: LogLevel, messages: List<Message.Response>) {
            messages.forEach {
                when (it) {
                    is Message.Tool.Call -> log(logLevel, "${it.role} : ${it.tool} ${it.content}\n")
                    else -> log(logLevel, "${it.role} : ${it.content}\n")
                }
            }
        }

        fun strategy(
            logger: Logger,
            debugLogLevel: LogLevel,
            validationTask: String?,
            gradleTools: AgenticGradleTools,
        ): AIAgentFunctionalStrategy<String, String> =
            functionalStrategy("agentic-gradle") { input ->
                var validationResult: Result = Result.Failure("the validation has not run yet")

                // todo: force tool calls and use a finish tool
                var responses = requestLLMMultiple(input)

                while (validationResult is Result.Failure) {
                    logger.log(debugLogLevel, responses)

                    while (responses.containsToolCalls()) {
                        val pendingCalls = extractToolCalls(responses)
                        val results = executeMultipleTools(pendingCalls, true)
//                        logger.log(debugLogLevel, "results=$results")
                        responses = sendMultipleToolResults(results)
                    }

                    if (validationTask != null) {
                        validationResult = gradleTools.runGradleTask(validationTask)

                        logger.log(debugLogLevel, "validation result: $validationResult")

                        responses = when (validationResult) {
                            is Result.Success ->
                                // todo: the result of this isn't used
                                requestLLMMultiple(validationResult.success)

                            is Result.Failure -> {
                                val tryAgainResponses = requestLLMMultiple(
                                    """
                                        The validation task $validationTask has been run and failed with:
                                        ${validationResult.failure}
                                        
                                        Do not run $validationTask again, fix the errors ultimately trying to:
                                        $input
                                    """.trimIndent()
                                )

                                // todo: prevent infinite loop if there are no tool calls
                                if (tryAgainResponses.containsToolCalls()) {
                                    tryAgainResponses
                                }
                                else {
                                    requestLLMMultiple("You need to actually run the required tools.")
                                }
                            }
                        }
                    }
                    else {
                        val responseContent = responses.fold<Message.Response, String?>(null) { contents, response ->
                            when (response) {
                                is Message.Assistant -> if (contents == null) response.content else contents + "\n" + response.content
                                else -> contents
                            }
                        }
                        validationResult = Result.Success(responseContent ?: "Completed successfully")
                    }
                }

                // ugly removal of thinking & response tags
                (validationResult as Result.Success).success.replace("<thinking>", "").replace("</thinking>", "").replace("<response>", "").replace("</response>", "").trim()
            }
    }
}

interface AgenticGradleTools : ToolSet {
    @Tool
    fun listFilesInDirectory(path: String): List<String>
    @Tool
    fun fileExists(path: String): Boolean
    @Tool
    fun readFile(path: String): Result
    @Tool
    fun writeFile(path: String, contents: String)
    @Tool
    @LLMDescription("List Gradle tasks")
    fun listGradleTasks(): Map<String, String>
    @Tool
    @LLMDescription("Run a Gradle task. If it fails try to fix the problem.")
    fun runGradleTask(task: String, arguments: String? = null): Result
}

class AgenticGradleToolsLive(val logger: Logger, val debugLogLevel: LogLevel, val project: Project) : AgenticGradleTools {

    private fun resolvePath(path: String): java.io.File {
        val base = project.projectDir.toPath().normalize()
        val candidate = try {
            val p = java.nio.file.Path.of(path)
            if (p.isAbsolute) p else base.resolve(p)
        } catch (e: Exception) {
            // If path is something unusual, fall back to Gradle's resolver then normalize
            base.resolve(project.file(path).toPath())
        }
        val normalized = candidate.normalize()
        if (!normalized.startsWith(base)) {
            throw IllegalArgumentException("Access outside project directory is not allowed: $path")
        }
        return normalized.toFile()
    }

    override fun listFilesInDirectory(path: String): List<String> = run {
        logger.log(debugLogLevel, "listDirectory: path=$path")
        val dir = resolvePath(path)
        if (!dir.exists() || !dir.isDirectory) emptyList() else (dir.listFiles()?.map { it.name } ?: emptyList())
    }

    override fun fileExists(path: String): Boolean = run {
        logger.log(debugLogLevel, "fileExists: path=$path")
        val file = resolvePath(path)
        file.exists()
    }

    override fun readFile(path: String): Result = run {
        logger.log(debugLogLevel, "readFile: path=$path")
        val file = resolvePath(path)
        if (!file.exists() || !file.isFile) {
            Result.Failure("File does not exist or is not a file: $path")
        }
        else {
            Result.Success(file.readText())
        }
    }

    override fun writeFile(path: String, contents: String): Unit = run {
        logger.log(debugLogLevel, "writeFile: path=$path contents=$contents")
        val file = resolvePath(path)
        file.parentFile?.mkdirs()
        file.writeText(contents)
    }

    override fun listGradleTasks(): Map<String, String> = run {
        logger.log(debugLogLevel, "Listing gradle tasks")

        project.tasks.associate {
            it.path to (it.description ?: "")
        }
    }

    override fun runGradleTask(task: String, arguments: String?): Result = run {
        logger.log(debugLogLevel, "Running Gradle task: $task")
        val result = GradleTaskRunner.run(project.projectDir, task, arguments)
        logger.log(debugLogLevel, "Task Output: $result")
        result
    }
}
