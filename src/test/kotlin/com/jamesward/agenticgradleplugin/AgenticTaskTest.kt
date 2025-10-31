package com.jamesward.agenticgradleplugin

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.utils.use
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.params.LLMParams
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import kotlinx.coroutines.runBlocking
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test

class AgenticTaskTest {

    private val logger = Logging.getLogger(AgenticTaskTest::class.java)
    private val bedrockRuntimeClient = runBlocking { BedrockRuntimeClient.fromEnvironment() }
    private val promptExecutor = SingleLLMPromptExecutor(BedrockLLMClient(bedrockRuntimeClient))

    fun aiAgent(gradleTools: AgenticGradleToolsMock, systemPrompt: String, validationTask: String?): AIAgent<String, String> = run {
        logger.lifecycle(systemPrompt)

        val toolRegistry = ToolRegistry {
            tools(gradleTools)
        }

        AIAgent(
            toolRegistry = toolRegistry,
            agentConfig = AIAgentConfig(
                prompt = prompt(
                    id = "agentic-gradle-plugin",
                    params = LLMParams(
                        includeThoughts = false, // doesn't seem to work
                    )
                ) {
                    system(systemPrompt)
                },
                model = BedrockModels.AmazonNovaPro,
                maxAgentIterations = 50,
            ),
            promptExecutor = promptExecutor,
            strategy = AgenticTask.strategy(logger, LogLevel.LIFECYCLE, validationTask, gradleTools),
            /*
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
             */
        )
    }

    @Test
    fun `strategy works with tool calls`(): Unit = runBlocking {
        val gradleTools = AgenticGradleToolsMock(
            mutableMapOf(
                "compile" to mutableListOf(Result.Success("success"))
            )
        )

        val project = ProjectBuilder.builder().build()

        val systemPrompt = AgenticTask.systemPrompt(
            project.objects.fileProperty(),
            project.objects.directoryProperty(),
            project.objects.fileCollection()
        )

        aiAgent(gradleTools, systemPrompt, null).use { agent ->
            val result = agent.run("run the compile gradle task")
            logger.lifecycle(result)
            assert(gradleTools.gradleTasksRun.contains("compile"))
            assert(result.contains("success"))
        }
    }

    @Test
    fun `strategy works with validation`(): Unit = runBlocking {
        val gradleTools = AgenticGradleToolsMock(
            mutableMapOf(
                "test" to mutableListOf(Result.Success("success"))
            )
        )

        val project = ProjectBuilder.builder().build()

        val systemPrompt = AgenticTask.systemPrompt(
            project.objects.fileProperty(),
            project.objects.directoryProperty(),
            project.objects.fileCollection()
        )

        aiAgent(gradleTools, systemPrompt, "test").use { agent ->
            val result = agent.run("say hello")
            logger.lifecycle(result)
            assert(gradleTools.gradleTasksRun.contains("test"))
            assert(result.contains("success"))
        }
    }

    @Test
    fun `strategy works with validation that fails then succeeds`(): Unit = runBlocking {
        val gradleTools = AgenticGradleToolsMock(
            mutableMapOf(
                "compile" to mutableListOf(Result.Success("success")),
                "test" to mutableListOf(Result.Failure("failed"), Result.Success("success"))
            )
        )

        val project = ProjectBuilder.builder().build()

        val systemPrompt = AgenticTask.systemPrompt(
            project.objects.fileProperty(),
            project.objects.directoryProperty(),
            project.objects.fileCollection()
        )

        aiAgent(gradleTools, systemPrompt, "test").use { agent ->
            val result = agent.run("run the compile gradle task")
            logger.lifecycle(result)
            assert(gradleTools.gradleTasksRun == listOf("compile", "test", "compile", "test"))
            assert(result.contains("success"))
        }
    }

    @Test
    fun `update the existing gradle build file`(): Unit = runBlocking {
        val gradleTools = AgenticGradleToolsMock(mutableMapOf())

        val project = ProjectBuilder.builder().build()

        val systemPrompt = AgenticTask.systemPrompt(
            project.objects.fileProperty(),
            project.objects.directoryProperty(),
            project.objects.fileCollection()
        )

        aiAgent(gradleTools, systemPrompt, null).use { agent ->
            val result = agent.run("add the webjars-locator-lite dependency")
            logger.lifecycle(result)
            val fileContents = gradleTools.fileWrites.first { it.first == "build.gradle.kts" }.second
            assert(fileContents.contains("plugins {"))
            assert(fileContents.contains("org.webjars:webjars-locator-lite"))
        }
    }
}

class AgenticGradleToolsMock(val taskRuns: Map<String, MutableList<Result>>) : AgenticGradleTools {
    var gradleTasksRun = mutableListOf<String>()
    var fileWrites = mutableListOf<Pair<String, String>>()

    override fun listFilesInDirectory(path: String): List<String> {
        TODO("Not yet implemented")
    }

    override fun fileExists(path: String): Boolean =
        path.contains("build.gradle.kts")

    override fun readFile(path: String): Result =
        if (path.contains("build.gradle.kts")) {
            Result.Success(
                """
                    plugins {
                        java
                    }
                    """.trimIndent()
            )
        } else {
            Result.Failure("file not found")
        }

    override fun writeFile(path: String, contents: String) {
        fileWrites.add(Pair(path, contents))
    }

    override fun listGradleTasks(): Map<String, String> {
        TODO("Not yet implemented")
    }

    override fun runGradleTask(
        task: String,
        arguments: String?
    ): Result {
        gradleTasksRun.add(task)
        val results = taskRuns[task]
        return results?.removeFirstOrNull() ?: Result.Failure("failed")
    }

}
