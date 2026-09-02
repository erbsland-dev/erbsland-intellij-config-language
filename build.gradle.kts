import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.intellij.platform.grammarkit") version "2.18.1"
}

group = "dev.erbsland"

val fallbackPluginVersion = providers.gradleProperty("pluginVersionFallback").orElse("0.1.0")
val versionFromGitTag = providers.exec {
    commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*", "--match", "[0-9]*")
    isIgnoreExitValue = true
}.standardOutput.asText.map { output ->
    val tag = output.trim()
    if (tag.isEmpty()) return@map ""
    val normalized = tag.removePrefix("v")
    require(normalized.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?"))) {
        "Git tag '$tag' is not a supported semantic plugin version (expected 1.2.3 or v1.2.3)"
    }
    normalized
}
version = versionFromGitTag.zip(fallbackPluginVersion) { tagged, fallback -> tagged.ifEmpty { fallback } }.get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdea("2025.2.6.2")
        testFramework(TestFrameworkType.Platform)
    }
}

val generatedClasses = layout.buildDirectory.dir("classes/elcl-generated")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        name = "Erbsland Configuration Language (ELCL)"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "252"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2.6.2")
            create(IntelliJPlatformType.IntellijIdea, "2026.2.1")
        }
    }
}

tasks.generateLexer {
    mustRunAfter(tasks.clean)
    sourceFile = file("src/main/grammar/Elcl.flex")
    targetRootOutputDir = layout.buildDirectory.dir("generated/sources/elcl-lexer/java/main")
    pathToClass = "dev/erbsland/elcl/lexer/_ElclLexer.java"
    purgeOldFiles = true
}

tasks.generateParser {
    mustRunAfter(tasks.clean)
    sourceFile = file("src/main/grammar/Elcl.bnf")
    targetRootOutputDir = layout.buildDirectory.dir("generated/sources/elcl-parser/java/main")
    pathToParser = "dev/erbsland/elcl/parser/ElclParser.java"
    pathToPsiRoot = "dev/erbsland/elcl/psi"
    purgeOldFiles = true
}

val compileGeneratedElcl by tasks.registering(JavaCompile::class) {
    dependsOn(tasks.generateLexer, tasks.generateParser)
    source(
        layout.buildDirectory.dir("generated/sources/elcl-lexer/java/main").get().asFile,
        layout.buildDirectory.dir("generated/sources/elcl-parser/java/main").get().asFile,
        file("src/main/generatedJava"),
    )
    classpath = sourceSets.main.get().compileClasspath
    destinationDirectory = generatedClasses
    options.release = 21
}

dependencies {
    compileOnly(files(generatedClasses))
    testImplementation(files(generatedClasses))
}

tasks.compileKotlin {
    dependsOn(compileGeneratedElcl)
    libraries.from(files(generatedClasses))
}

tasks.compileJava {
    dependsOn(compileGeneratedElcl)
}

tasks.jar {
    dependsOn(compileGeneratedElcl)
    from(generatedClasses)
}

tasks.test {
    maxHeapSize = "1g"
}
