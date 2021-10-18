import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import nebula.plugin.release.git.base.ReleasePluginExtension
import nl.javadude.gradle.plugins.license.LicenseExtension
import java.util.*

plugins {
    `java-library`
    `maven-publish`
    signing

    id("org.jetbrains.kotlin.jvm") version "1.5.31"
    id("com.github.johnrengelman.shadow") version "6.1.0"

    id("nebula.maven-resolved-dependencies") version "17.3.2"
    id("nebula.release") version "15.3.1"
    id("io.github.gradle-nexus.publish-plugin") version "1.0.0"

    id("com.github.hierynomus.license") version "0.16.1"

    id("nebula.maven-publish") version "17.3.2"
    id("nebula.contacts") version "5.1.0"
    id("nebula.info") version "9.3.0"

    id("nebula.javadoc-jar") version "17.3.2"
    id("nebula.source-jar") version "17.3.2"
    id("nebula.maven-apache-license") version "17.3.2"
}

apply(plugin = "nebula.publish-verification")

configure<ReleasePluginExtension> {
    defaultVersionStrategy = nebula.plugin.release.NetflixOssStrategies.SNAPSHOT(project)
}

group = "org.openrewrite"
description = "Support for writing Recipes in GraalVM guest languages."

repositories {
    if (!project.hasProperty("releasing")) {
        mavenLocal()
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
    }
    mavenCentral()
}

nexusPublishing {
    repositories {
        sonatype()
    }
}

signing {
    setRequired({
        !project.version.toString().endsWith("SNAPSHOT") || project.hasProperty("forceSigning")
    })
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["nebula"])
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, TimeUnit.SECONDS)
        cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
    }
}

val rewriteVersion = if (project.hasProperty("releasing")) {
    "latest.release"
} else {
    "latest.integration"
}

val compiler = javaToolchains.compilerFor {
    languageVersion.set(JavaLanguageVersion.of(8))
}
val tools = compiler.get().metadata.installationPath.file("lib/tools.jar")

dependencies {
    compileOnly(files(tools))
    implementation("org.openrewrite:rewrite-java-11:latest.integration")
    runtimeOnly("org.slf4j:slf4j-simple:latest.release")

    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")

    testImplementation("org.jooq:joor:latest.release")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-common")
    testImplementation("org.openrewrite:rewrite-test:latest.integration")
    testImplementation("org.assertj:assertj-core:latest.release")

    testImplementation("org.junit.jupiter:junit-jupiter-api:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter-params:latest.release")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:latest.release")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs = listOf("-XX:+UnlockDiagnosticVMOptions", "-XX:+ShowHiddenFrames")
}

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()

    options.isFork = true
    options.forkOptions.executable = "javac"
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "--release", "8"))
}

configure<nebula.plugin.contacts.ContactsExtension> {
    val j = nebula.plugin.contacts.Contact("team@moderne.io")
    j.moniker("Team Moderne")

    people["team@moderne.io"] = j
}

configure<LicenseExtension> {
    ext.set("year", Calendar.getInstance().get(Calendar.YEAR))
    skipExistingHeaders = true
    header = project.rootProject.file("gradle/licenseHeader.txt")
    mapping(mapOf("kt" to "SLASHSTAR_STYLE", "java" to "SLASHSTAR_STYLE"))
    exclude("src/main/resources/*.java")
    strictCheck = true
}

configure<PublishingExtension> {
    publications {
        named("nebula", MavenPublication::class.java) {
            suppressPomMetadataWarningsFor("runtimeElements")
        }
    }
}

tasks.withType<Javadoc> {
    exclude(
        "org/openrewrite/polyglot/interop/*"
    )
}

tasks.withType<ShadowJar> {
    configurations = listOf(project.configurations.runtimeClasspath.get())
    dependencies {
        include(dependency("org.openrewrite:"))
        include(dependency("org.slf4j:"))
    }
    relocate("org.slf4j", "org.openrewrite.polyglot.shaded.slf4j")
    relocate("org.openrewrite", "org.openrewrite.polyglot.shaded") {
        exclude("org.openrewrite.polyglot.interop.*")
    }
}

tasks.named("jar") {
    enabled = false
    dependsOn(tasks.named("shadowJar"))
}
