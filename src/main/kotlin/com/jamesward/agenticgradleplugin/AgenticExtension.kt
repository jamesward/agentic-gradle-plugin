package com.jamesward.agenticgradleplugin

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleBedrockExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.register

sealed interface AIProvider {
    val executor: PromptExecutor
    val model: LLModel
}

private data class Anthropic(
    val apiKey: String?,
    override val model: LLModel
) : AIProvider {
    override val executor: PromptExecutor by lazy {
        val maybeApiKey: String? = apiKey ?: System.getenv("ANTHROPIC_API_KEY")
        if (maybeApiKey == null) {
            throw IllegalArgumentException("You must either provide an Anthropic API key or set the ANTHROPIC_API_KEY environment variable")
        } else {
            simpleAnthropicExecutor(maybeApiKey)
        }
    }
    override fun toString(): String = "Anthropic(apiKey='${apiKey?.take(3)}*********', model=$model)"
}

private data class Bedrock(
    val awsAccessKeyId: String?,
    val awsSecretAccessKey: String?,
    val awsSessionToken: String?,
    override val model: LLModel,
    val region: String?,
) : AIProvider {
    override val executor: PromptExecutor by lazy {
        val maybeAwsAccessKeyId: String? = awsAccessKeyId ?: System.getenv("AWS_ACCESS_KEY_ID")
        val maybeAwsSecretAccessKey: String? = awsSecretAccessKey ?: System.getenv("AWS_SECRET_ACCESS_KEY")
        val maybeAwsSessionToken: String? = awsSessionToken ?: System.getenv("AWS_SESSION_TOKEN")
        if (maybeAwsAccessKeyId == null) {
            throw IllegalArgumentException("You must either provide an AWS access key ID or set the AWS_ACCESS_KEY_ID environment variable")
        } else if (maybeAwsSecretAccessKey == null) {
            throw IllegalArgumentException("You must either provide an AWS secret access key or set the AWS_SECRET_ACCESS_KEY environment variable")
        } else {
            if (region != null)
                simpleBedrockExecutor(
                    maybeAwsAccessKeyId,
                    maybeAwsSecretAccessKey,
                    maybeAwsSessionToken,
                    BedrockClientSettings(region = region)
                )
            else
                simpleBedrockExecutor(maybeAwsAccessKeyId, maybeAwsSecretAccessKey, awsSessionToken)
        }
    }

    override fun toString(): String = "Bedrock(awsAccessKeyId='${awsAccessKeyId?.take(3)}*********', awsSecretAccessKey='${awsSecretAccessKey?.take(3)}*********', awsSessionToken='${awsSessionToken?.take(3)}*********', model=$model)"
}

private data class OpenAI(
    val apiToken: String?,
    override val model: LLModel,
) : AIProvider {
    override val executor: PromptExecutor by lazy {
        val maybeApiKey: String? = apiToken ?: System.getenv("OPENAI_API_KEY")
        if (maybeApiKey == null) {
            throw IllegalArgumentException("You must either provide an OpenAI API key or set the OPENAI_API_KEY environment variable")
        } else {
            simpleOpenAIExecutor(maybeApiKey)
        }
    }

    override fun toString(): String = "OpenAI(apiToken='${apiToken?.take(3)}*********')"
}


abstract class AgenticExtension(val project: org.gradle.api.Project) {
    abstract val provider: Property<AIProvider>

    fun anthropic(apiKey: String? = null, model: LLModel? = null): AIProvider = run {
        if (model != null)
            Anthropic(apiKey, model)
        else
            Anthropic(apiKey, AnthropicModels.Sonnet_4)
    }

    fun bedrock(awsAccessKeyId: String? = null, awsSecretAccessKey: String? = null, awsSessionToken: String? = null, model: LLModel? = null, region: String? = null): AIProvider = run {
        if (model != null)
            Bedrock(awsAccessKeyId, awsSecretAccessKey, awsSessionToken, model, region)
        else
            Bedrock(awsAccessKeyId, awsSecretAccessKey, awsSessionToken, BedrockModels.AmazonNovaPro, region)
    }

    fun openai(apiKey: String? = null, model: LLModel? = null): AIProvider = run {
        if (model != null)
            OpenAI(apiKey, model)
        else
            OpenAI(apiKey, OpenAIModels.Chat.GPT4o)
    }

    fun create(name: String, configuration: AgenticTask.() -> Unit) {
        project.tasks.register<AgenticTask>(name) {
            configuration()
        }
    }

    abstract val debug: Property<Boolean>
}
