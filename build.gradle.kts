plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}
// Set as appropriate for your organization
group = "org.openrewrite"
description = "Tools for parsing and rewriting code in multiple languages."

repositories {
    maven {
        url = uri("https://repo.gradle.org/gradle/libs-releases/")
        content {
            excludeVersionByRegex(".+", ".+", ".+-rc-?[0-9]*")
        }
    }
}

val latest = rewriteRecipe.rewriteVersion.get()
dependencies {
    compileOnly("org.projectlombok:lombok:latest.release")
    compileOnly("com.google.code.findbugs:jsr305:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")

    implementation(platform("org.openrewrite:rewrite-bom:$latest"))
    implementation("org.openrewrite:rewrite-groovy")
    implementation("org.openrewrite:rewrite-gradle")
    implementation("org.openrewrite:rewrite-hcl")
    implementation("org.openrewrite:rewrite-json")
    implementation("org.openrewrite:rewrite-maven")
    implementation("org.openrewrite:rewrite-properties")
    implementation("org.openrewrite:rewrite-protobuf")
    implementation("org.openrewrite:rewrite-xml")
    implementation("org.openrewrite:rewrite-yaml")

    testRuntimeOnly("org.openrewrite:rewrite-java-17")
    testRuntimeOnly("org.openrewrite:rewrite-java-21")
}
