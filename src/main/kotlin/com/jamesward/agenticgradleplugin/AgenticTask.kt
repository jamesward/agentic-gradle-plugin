package com.jamesward.agenticgradleplugin

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestMultiple
import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResults
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.nodeUpdatePrompt
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onIsInstance
import ai.koog.agents.core.dsl.extension.onMultipleAssistantMessages
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.dsl.extension.setToolChoiceRequired
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.executeTool
import ai.koog.agents.core.environment.toSafeResult
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.asToolDescriptor
import ai.koog.agents.core.tools.asToolDescriptorDeserializer
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.ext.agent.CriticResult
import ai.koog.agents.ext.agent.CriticResultFromLLM
import ai.koog.agents.ext.agent.SubgraphWithTaskUtils.FINALIZE_SUBGRAPH_TOOL_NAME
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.ext.agent.subgraphWithVerification
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default
import kotlinx.serialization.serializer
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
import java.lang.IllegalStateException
import kotlin.collections.map


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

    @TaskAction
    fun runAgent() {
        val ext = project.extensions.getByType<AgenticExtension>()
        val debugLogLevel = if (ext.debug.orNull ?: false) LogLevel.LIFECYCLE else LogLevel.DEBUG
        println("debugLogLevel=$debugLogLevel")

        val provider = ext.provider.orNull ?: throw IllegalArgumentException("provider must be provided")
        val promptValue = prompt.orNull ?: throw IllegalArgumentException("prompt must be provided")

        logger.log(debugLogLevel, "AgenticTask: prompt=$promptValue provider=$provider")

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
        val strategy = strategy<String, String>("build-agent") {

//            val generate by subgraphWithTask<String, String>(
//                tools = toolRegistry.tools,
//            ) { input -> "Generate the gradle files. Here is the additional information: $input" }
//
//            val verify: AIAgentSubgraph<String, CriticResult<String>> by subgraphWithVerification<String>(
//                tools = listOf(gradleTools::runGradleTask.asTool()),
//            ) { input -> "Verify that everything is correct" }
//
//            edge(nodeStart forwardTo generate)
//            edge(generate forwardTo verify)
//            edge(verify forwardTo nodeFinish onCondition { it.successful} transformed {it.input})
//            edge(verify forwardTo generate onCondition { !it.successful} transformed {it.feedback})



            /*
            val userPrompt by subgraphWithTask<String>(tools = toolRegistry.tools, llmModel = provider.model) { result ->
                logger.log(debugLogLevel, "AgenticTask: userPrompt result: $result")

                ""
            }

             */

//            val userPrompt by subtaskWithMultipleTools(
//                tools = toolRegistry.tools,
//            ) { input -> "Generate the gradle files. Here is the additional information: $input"}


//            subgraphWithVerification<>()
            val generate by subtaskWithMultipleTools<String, String>(
                tools = toolRegistry.tools,
            ) { input ->
                println("subtaskWithMultipleTools $input")
                input
            }

            validationTask.orNull?.let { validationTaskValue ->
                val verify by subtaskWithVerificationMulti<String>(
                    tools = listOf(gradleTools::runGradleTask.asTool()),
                ) { input ->
                    println("subtaskWithVerificationMulti $input")
                    "Verify that everything is correct by running the $validationTaskValue gradle task"
                }
            }


            edge(nodeStart forwardTo generate)
            edge(generate forwardTo nodeFinish)


            /*
            edge(generate forwardTo verify)
            edge(verify forwardTo nodeFinish onCondition { it.successful} transformed {it.input})
            edge(verify forwardTo generate onCondition { !it.successful} transformed {it.feedback})
             */

            /*
            validationTask.orNull?.let { validationTaskValue ->
                val prompt =
                    """
                    Validate changes by running the gradle task: $validationTaskValue
                    Fix any issues before continuing.
                    """.trimIndent()

                val doValidation by nodeUpdatePrompt<String>("setupContext") {
                    system(prompt)
                }

             */

//            validationTask.orNull?.let { validationTaskValue ->
//                val validation by subgraphWithVerification<String>(
//                    tools = toolRegistry.tools,
//                    llmModel = provider.model
//                ) { something ->
//                    logger.log(debugLogLevel, something)
//
//                    """
//                    Validate changes by running the gradle task: $validationTaskValue
//                    Fix any issues before continuing.
//                    """.trimIndent()
//                }
//
//                val validationFinish by node<VerifiedSubgraphResult, String>("validationFinish") { result ->
//                    println("validationFinish: $result")
//                    "done"
//                }
//
//                nodeStart then userPrompt then validation then validationFinish then nodeFinish
//            } ?: run {
//                nodeStart then userPrompt then nodeFinish
//            }
            /*

                val validationToString by node<VerifiedSubgraphResult, String> {
                    it.message
                }

                nodeStart then userPrompt then validationSubgraph then validationToString then nodeFinish
                 */
            /*
                println("validationTaskValue=$validationTaskValue")

                nodeStart then userPrompt then doValidation then nodeFinish
            } ?: run {
                val noValidation by nodeUpdatePrompt<String>("setupContext") {
                    user("You do NOT need to validate your changes")
                }

                nodeStart then noValidation then userPrompt then nodeFinish
            }

             */

            /*
            val defaultTask = subgraphWithTask<String>(
                tools = toolRegistry.tools,
                llmModel = provider.model
            ) { it }


            val node = validationTask.orNull?.let { validationTaskValue ->
                val verificationTask by subgraphWithVerification<String>(
                    tools = toolRegistry.tools,
                    llmModel = provider.model
                ) { something ->
                    println(something)

                    "Validate changes by running the gradle task: $validationTaskValue"
                }
                verificationTask

//                nodeStart.then(verify).then(nodeFinish)

                //nodeStart.then(nodeFinish)
            } ?: {
                val noValidationTask by subgraphWithTask<>("") //  nodeUpdatePrompt<String>("setupContext") {
                    system("You do NOT need to validate your changes")
                }
                noValidationTask
                //(nodeStart then setupContext then nodeFinish)
            }

            node
             */
//            nodeStart then userPrompt then nodeFinish
        }

        val agent = AIAgent(
//            strategy = strategy,
//            executor = provider.executor,
//            llmModel = provider.model,
            toolRegistry = toolRegistry,
            agentConfig = AIAgentConfig(
                prompt = prompt(
                    id = "agentic-gradle-plugin",
                    params = LLMParams(
//                        temperature = temperature,
//                        numberOfChoices = numberOfChoices,
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
        ) {
            handleEvents {
                onAgentStarting { eventContext ->
                    logger.log(debugLogLevel, "AgenticTask: onAgentStarting: $eventContext")
                }
                onToolCallStarting { eventContext ->
                    logger.log(debugLogLevel, "AgenticTask: onToolCallStarting: ${eventContext.tool.name}") // with args ${eventContext.toolArgs}")
                }
                onToolCallFailed {
                    logger.log(debugLogLevel, "AgenticTask: onToolCallFailed: ${it.tool.name} with args ${it.toolArgs} ${it.throwable.message}")
                }
                onToolCallCompleted {
                    logger.log(debugLogLevel, "AgenticTask: onToolCallCompleted: ${it.tool.name}") // with args ${it.toolArgs} returned ${it.result?.toStringDefault()}")
                }
                onLLMCallStarting { eventContext ->
                    logger.log(debugLogLevel, "AgenticTask: onLLMCallStarting: ${eventContext.prompt}")
                }
                onLLMCallCompleted { eventContext ->
                    logger.log(debugLogLevel, "AgenticTask: onLLMCallCompleted: ${eventContext.responses}")
                }
                onAgentCompleted { eventContext ->
                    logger.log(debugLogLevel, "AgenticTask: onAgentCompleted: ${eventContext.result}")
                }
                onAgentExecutionFailed { eventContext ->
                    logger.log(debugLogLevel, "AgenticTask: onAgentExecutionFailed: ${eventContext.throwable.message}")
                }
            }
        }

        // todo: stream output / iterations so the user see progress?
        val output = runBlocking {
            agent.run(promptValue)
        }

        logger.lifecycle("AgenticTask: output=$output")
    }
}

@OptIn(InternalAgentsApi::class)
private inline fun <reified Input: Any> AIAgentGraphStrategyBuilder<*, *>.subtaskWithVerificationMulti(
    tools: List<ai.koog.agents.core.tools.Tool<* ,*>>,
    crossinline task: (Input) -> String
) = subgraph<Input, CriticResult<Input>> {
    val inputKey = createStorageKey<Input>("subgraphWithVerification-input-key")

    val saveInput by node<Input, Input> { input ->
        storage.set(inputKey, input)

        input
    }

    val verifyTask by this@subtaskWithVerificationMulti.subtaskWithMultipleTools<Input, CriticResultFromLLM>(
        tools = tools,
        task = task
    )

    val provideResult by node<CriticResultFromLLM, CriticResult<Input>> { result ->
        CriticResult(
            successful = result.isCorrect,
            feedback = result.feedback,
            input = storage.get(inputKey)!!
        )
    }

    nodeStart then saveInput then verifyTask then provideResult then nodeFinish
}

@OptIn(InternalAgentToolsApi::class)
private inline fun <reified Input, reified Output> AIAgentGraphStrategyBuilder<*, *>.subtaskWithMultipleTools(
    tools: List<ai.koog.agents.core.tools.Tool<* ,*>>,
    crossinline task: (Input) -> String
): AIAgentSubgraphDelegate<Input, Output> =
    subgraph<Input, Output>("execute-user-prompt") {
        val originalToolsKey = createStorageKey<List<ToolDescriptor>>("all-available-tools")

        val finishToolDescriptor =
            serializer<Output>().descriptor.asToolDescriptor(toolName = FINALIZE_SUBGRAPH_TOOL_NAME, toolDescription = "finish")


        val setupTask by node<Input, String> { input ->
            llm.writeSession {

                // Save tools to restore after subgraph is finished
                storage.set(originalToolsKey, this.tools)

                // Append finish tool to tools if it's not present yet
                if (finishToolDescriptor !in this.tools) {
                    this.tools = tools.map { it.descriptor } + finishToolDescriptor
                }

                this.tools.forEach { println(it) }

                // Model must always call tools in the loop until it decides (via finish tool) that the exit condition is reached
                setToolChoiceRequired()
            }

            // Output task description
            task(input)
        }

        val finalizeTask by node<ReceivedToolResult, Output> { input ->
            llm.writeSession {
                // Restore original tools
                this.tools = storage.get(originalToolsKey)!!
            }

            input.toSafeResult<Output>().asSuccessful().result
        }

        // Helper node to overcome problems of the current api and repeat less code when writing routing conditions
        val nodeDecide by node<List<Message.Response>, List<Message.Response>> { it }

        val nodeCallLLM by nodeLLMRequestMultiple()

        /**
         * Works like a normal `nodeExecuteTool` but a bit hacked: if LLM decides to call the fake "finalize_result" tool,
         * it doesn't execute it.
         * */
        val callToolHacked by node<List<Message.Tool.Call>, List<ReceivedToolResult>> { toolCalls ->
            toolCalls.map { toolCall ->
                if (toolCall.tool == FINALIZE_SUBGRAPH_TOOL_NAME) {
                    val toolResult =
                        Json.decodeFromString(serializer<Output>().asToolDescriptorDeserializer(), toolCall.content)

                    // Append final tool call result to the prompt for further LLM calls to see it (otherwise they would fail)
                    llm.writeSession {
                        updatePrompt {
                            tool {
                                result(toolCall.id, toolCall.tool, toolCall.content)
                            }
                        }
                    }

                    ReceivedToolResult(
                        id = toolCall.id,
                        tool = FINALIZE_SUBGRAPH_TOOL_NAME,
                        content = toolCall.content,
                        result = toolResult
                    )
                } else {
                    environment.executeTool(toolCall)
                }
            }
        }

        val sendToolResult by nodeLLMSendMultipleToolResults()

        nodeStart then setupTask then nodeCallLLM then nodeDecide

        edge(nodeDecide forwardTo callToolHacked onMultipleToolCalls  { true })

        // throw to terminate the agent early with exception
        edge(
            nodeDecide forwardTo nodeFinish transformed {
                throw IllegalStateException(
                    "Subgraph with task must always call tools, but no tool call was generated, got instead: $it"
                )
            }
        )

        edge(
            callToolHacked forwardTo finalizeTask
                onCondition { toolCalls -> toolCalls.any {it.tool == finishToolDescriptor.name } }
                transformed { toolCalls -> toolCalls.find {it.tool == finishToolDescriptor.name }!! }
        )
        edge(callToolHacked forwardTo sendToolResult)

        edge(sendToolResult forwardTo nodeDecide)

        edge(finalizeTask forwardTo nodeFinish)
        //
//                val filterThinking by node<List<Message>, Message> { input ->
//                    input.firstOrNull { it is Message.Tool } ?: input.first()
//                }


//
//                    ...
//                edge(filterThinking forwardTo nodeFinish onAssistantMessage  { true })
//                edge(filterThinking forwardTo executeToolCall onToolCall { true })
//
//                edge(executeToolCall forwardTo sendToolResult)
//
//                edge(sendToolResult forwardTo executeToolCall onToolCall { true })
//                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })

        /*
                validationTask.orNull?.let { validationTaskValue ->
                    val prompt =
                        """
                        Now run the gradle task: $validationTaskValue
                        Fix any issues before continuing.
                        """.trimIndent()

                    val doValidation by nodeUpdatePrompt<String>("setupContext") {
                        user(prompt)
                    }

                    edge(filterThinking forwardTo doValidation onAssistantMessage { true })
                    edge(doValidation forwardTo nodeCallLLM)
                    edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
                } ?: run {
                    edge(filterThinking forwardTo nodeFinish onAssistantMessage { true })
                    edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
                }

                 */
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
    fun runGradleTask(task: String, arguments: String? = null): String = run {
        logger.log(debugLogLevel, "Running Gradle task: $task")
        GradleTaskRunner.run(project.projectDir, task, arguments)
    }
}
