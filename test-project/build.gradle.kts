plugins {
    java
    id("com.jamesward.agentic-gradle-plugin")
}

agentic {
//    provider = anthropic()
//    provider = bedrock(model = BedrockModels.AnthropicClaude4Sonnet, region = BedrockRegions.US_EAST_1.regionCode)
    provider = openai()
    create("genCalc") {
        debug = true
        prompt = "create a java class that adds numbers and create the test for it."
        validationTask = "test"
    }
}
