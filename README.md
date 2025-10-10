# Agentic Gradle Plugin

A Gradle plugin that provides 



## Mermaid diagram for userPrompt graph

The following Mermaid diagram reflects the graph defined in AgenticTask.kt for the userPrompt subgraph.

```mermaid
flowchart TD
  subgraph userPrompt
    Start([nodeStart])
    CallLLM[nodeCallLLM]
    ExecuteTools[executeToolCall]
    SendToolResult[sendToolResult]
    Finish([nodeFinish])

    Start --> CallLLM
    CallLLM -- onMultipleToolCalls: true --> ExecuteTools
    CallLLM -- onMultipleAssistantMessages: true<br/>transform: first content --> Finish

    ExecuteTools --> SendToolResult
    SendToolResult -- onMultipleToolCalls: true --> ExecuteTools
    SendToolResult -- onMultipleAssistantMessages: true<br/>transform: first content --> Finish
  end
```

## Release Process

```
ORG_GRADLE_PROJECT_mavenCentralUsername=username
ORG_GRADLE_PROJECT_mavenCentralPassword=the_password

ORG_GRADLE_PROJECT_signingInMemoryKey=exported_ascii_armored_key
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=some_password
```

## TODO
- MCP Client?
- CI/CD
- Console for arbitrary runs?
- 
