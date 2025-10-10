package com.jamesward.agenticgradleplugin

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.kotlin.dsl.create

class AgenticGradlePlugin: Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create<AgenticExtension>("agentic")
    }
}
