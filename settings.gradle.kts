rootProject.name = "rewrite-polyglot"

pluginManagement {
    repositories {
        mavenLocal()
        maven {
            name = "codegenome"
            url = uri("https://artifacts.codegenomeproject.org/maven")
            credentials {
                username = providers.gradleProperty("codegenomeUsername").orNull ?: System.getenv("CODEGENOME_USERNAME")
                password = providers.gradleProperty("codegenomePassword").orNull ?: System.getenv("CODEGENOME_TOKEN")
            }
            content {
                includeGroupAndSubgroups("org.openrewrite")
                includeGroupAndSubgroups("io.moderne")
            }
        }
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.develocity") version "latest.release"
    id("com.gradle.common-custom-user-data-gradle-plugin") version "latest.release"
}
