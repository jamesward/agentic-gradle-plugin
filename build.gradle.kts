import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `kotlin-dsl`

    embeddedKotlin("jvm")
    embeddedKotlin("plugin.power-assert")
    embeddedKotlin("plugin.serialization")

    id("com.vanniktech.maven.publish") version "0.34.0"
}

group = "com.jamesward"
version = "0.0.1"

gradlePlugin {
    website = "https://github.com/jamesward/agentic-gradle-plugin"
    vcsUrl = "https://github.com/jamesward/agentic-gradle-plugin.git"
    plugins {
        create("AgenticGradlePlugin") {
            id = "com.jamesward.agentic-gradle-plugin"
            implementationClass = "com.jamesward.agenticgradleplugin.AgenticGradlePlugin"
            displayName = "Agentic Gradle Plugin"
            description = "A plugin"
            tags = listOf("ai")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("ai.koog:koog-agents:0.5.0")

    testImplementation(embeddedKotlin("test"))
    testImplementation(gradleTestKit())
}

tasks.withType<Test> {
    useJUnitPlatform()

    testLogging {
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
        events(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    pom {
        name = "Agentic Gradle Plugin"
        description = "A plugin"
        inceptionYear = "2025"
        url = "https://github.com/jamesward/agentic-gradle-plugin"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "jamesward"
                name = "James Ward"
                email = "james@jamesward.com"
                url = "https://jamesward.com"
            }
        }
        scm {
            url = "https://github.com/jamesward/agentic-gradle-plugin"
            connection = "https://github.com/jamesward/agentic-gradle-plugin.git"
            developerConnection = "scm:git:git@github.com:jamesward/agentic-gradle-plugin.git"
        }
    }
}

