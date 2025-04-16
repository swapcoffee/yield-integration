import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    kotlin("jvm") version ("1.9.10")
    id("com.google.devtools.ksp") version ("1.9.10-1.0.13")
    id("application")
    id("org.openapi.generator") version("7.4.0")
}

buildscript {
    dependencies {
        classpath("ru.tinkoff.kora:openapi-generator:1.1.23")
    }
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("com.example.yield.ApplicationKt")
}

val koraBom: Configuration by configurations.creating
configurations {
    ksp.get().extendsFrom(koraBom)
    compileOnly.get().extendsFrom(koraBom)
    api.get().extendsFrom(koraBom)
    implementation.get().extendsFrom(koraBom)
}

group = "com.example"
version = "1.0-SNAPSHOT"

val generateSelfRest = tasks.register<GenerateTask>("GenerateSelfRest") {
    generatorName = "kora"
    group = "openapi tools"
    inputSpec = "$projectDir/src/main/resources/openapi/yield-spec.yaml"
    outputDir = "$buildDir/generated/openapi/main/kotlin"
    packageName = "com.example.api"
    apiPackage = "com.example.api.controller"
    modelPackage = "com.example.api.model"
    invokerPackage = "com.example.invoker"
    configOptions = mapOf(
        "mode" to "kotlin-suspend-server",
        "enableServerValidation" to "true"
    )
    outputs.cacheIf { false }
}

tasks.all {
    if (name == "kspKotlin") {
        dependsOn(generateSelfRest)
    }
}

dependencies {
    koraBom(platform("ru.tinkoff.kora:kora-parent:1.1.23"))
    ksp("ru.tinkoff.kora:symbol-processors")

    api("ru.tinkoff.kora:micrometer-module")
    api("ru.tinkoff.kora:json-module")
    api("ru.tinkoff.kora:config-hocon")
    api("ru.tinkoff.kora:logging-logback")
    api("ru.tinkoff.kora:logging-common")
    api("ru.tinkoff.kora:http-server-undertow")
    api("ru.tinkoff.kora:http-client-async")
    api("ru.tinkoff.kora:openapi-management")
    api("ru.tinkoff.kora:validation-module")
    api("ru.tinkoff.kora:database-jdbc")
    api("redis.clients:jedis:5.1.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")

    api("org.honton.chas.hocon:jackson-dataformat-hocon:1.1.1")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.0")

    implementation("ch.qos.logback:logback-classic:1.4.8")

    implementation("io.github.neodix42:smartcontract:0.9.5")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    api("org.postgresql:postgresql:42.5.1")
}

kotlin {
    jvmToolchain { languageVersion.set(JavaLanguageVersion.of("17")) }
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
        kotlin.srcDir("build/generated/openapi/main/kotlin")
    }
    sourceSets.test {
        kotlin.srcDir("build/generated/ksp/test/kotlin")
    }
}

tasks.distTar {
    archiveFileName.set("application.tar")
}