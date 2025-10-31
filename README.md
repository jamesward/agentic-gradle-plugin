# Agentic Gradle Plugin

Gradle tasks driven by AI! What could go wrong?

Add the plugin:
```kotlin
id("com.jamesward.agentic-gradle-plugin") version "0.0.1"
```

Define your agentic task:

```kotlin
agentic {
    create("genCalc") {
        debug = true
        prompt = "create a java class that adds numbers and create the test for it."
        validationTask = "test"
    }
}
```

Set your AI provider API keys in env vars, or explicitly by setting a provider:
```kotlin
agentic {
    provider = anthropic(apiKey = "yours")
    // or
    provider = bedrock(awsBearerTokenBedrock = "yours")
    // or
    provider = bedrock(awsAccessKeyId = "yours", awsSecretAccessKey = "yours", awsSessionToken = "if you have it")
    // or
    provider = openai(apiKey = "yours")
    
    // ...
}
```

Run the task and watch the AI magic happen:
```shell
./gradlew genCalc
```

You can set specific models and regions for Bedrock on the provider, like:
```kotlin
provider = bedrock(model = BedrockModels.AnthropicClaude4Sonnet, region = BedrockRegions.US_EAST_1.regionCode)
```
