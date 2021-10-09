import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import nebula.plugin.release.git.base.ReleasePluginExtension
import nl.javadude.gradle.plugins.license.LicenseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

plugins {
    `java-library`
    `maven-publish`
    signing

    id("org.jetbrains.kotlin.jvm") version "1.5.21"

    id("nebula.maven-resolved-dependencies") version "17.3.2"
    id("nebula.release") version "15.3.1"
    id("io.github.gradle-nexus.publish-plugin") version "1.0.0"
    id("com.github.johnrengelman.shadow") version "6.1.0"
    id("nebula.maven-shadow-publish") version "18.0.0"

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
//    setRequired({
//        !project.version.toString().endsWith("SNAPSHOT") || project.hasProperty("forceSigning")
//    })
    isRequired = false
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

dependencies {
    runtimeOnly("org.slf4j:slf4j-simple:latest.release")
    compileOnly("org.openrewrite:rewrite-java-11:latest.integration")
    testImplementation("org.openrewrite:rewrite-java-11:latest.integration")

    implementation("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")

    testImplementation("org.jooq:joor:latest.release")

    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-common")
    testImplementation("org.openrewrite:rewrite-test:latest.integration")
    testImplementation("org.assertj:assertj-core:latest.release")

    testImplementation("com.google.auto.service:auto-service:latest.release")
    annotationProcessor("com.google.auto.service:auto-service:latest.release")

    testImplementation("org.junit.jupiter:junit-jupiter-api:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter-params:latest.release")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:latest.release")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.0.13")
}

tasks.withType(KotlinCompile::class.java).configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
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
    options.compilerArgs.add("-parameters")
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

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()

    options.compilerArgs.addAll(
        listOf(
            "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED"
        )
    )
}

tasks.withType<Javadoc> {
    exclude(
        "**/Generate**"
    )
}

tasks.withType<ShadowJar> {
    configurations = listOf(project.configurations.runtimeClasspath.get())
    archiveClassifier.set(null as String?)
    relocate("org.openrewrite", "shaded.org.openrewrite") {
        exclude("org.openrewrite.polyglot.interop.*")
    }
    dependencies {
        include(setOf("org.openrewrite.*"))
    }
}


tasks.named("jar") {
    enabled = false
    dependsOn(tasks.named("shadowJar"))
}
