buildscript {
    dependencies {
        classpath("crackers.buildstuff:crackers-gradle-plugins:1.0.1")
    }
}

plugins {
    kotlin("jvm") version "1.9.0"
    idea
    id("org.jmailen.kotlinter") version "3.12.0"
    id("library-publish") version "1.0.1"
    id("org.jetbrains.dokka") version "1.8.10"
}

repositories {
    mavenCentral()
    mavenLocal()
}

val DIOZERO_VER = "1.4.0"
val DEVICES_VER = "0.2.0"

group = "crackers.kobots"
version = "0.0.1"

dependencies {
    // these are likely to be the ones in use
    compileOnly("crackers.kobots:kobots-devices:$DEVICES_VER")
    compileOnly("org.json:json:20230227")
    compileOnly("com.typesafe:config:1.4.2")

    // optional parts
    compileOnly("crackers.automation:hassk:0.0.1") {
        exclude(group = "ch.qos.logback")
    }
    compileOnly("org.eclipse.paho:org.eclipse.paho.mqttv5.client:1.2.5")

    testImplementation("io.kotest:kotest-runner-junit5:5.6.2")
    testImplementation("io.mockk:mockk:1.13.5")
    testImplementation("crackers.kobots:kobots-devices:$DEVICES_VER")
}

kotlin {
    jvmToolchain(17)
}

kotlinter {
    // ignore failures because the build re-formats it
    ignoreFailures = true
    disabledRules = arrayOf("no-wildcard-imports")
}

tasks {
    build {
        dependsOn("formatKotlin")
    }
    test {
        useJUnitPlatform()
    }
    javadoc {
        mustRunAfter("test")
    }
    // make docs
    dokkaJavadoc {
        mustRunAfter("javadoc")
        outputDirectory.set(file("$projectDir/build/docs"))
    }
    javadocJar {
        mustRunAfter("dokkaJavadoc")
        include("$projectDir/build/docs")
    }
    // jar docs
    register<Jar>("dokkaJavadocJar") {
        dependsOn(dokkaJavadoc)
        from(dokkaJavadoc.flatMap { it.outputDirectory })
        archiveClassifier.set("javadoc")
    }
    generateMetadataFileForLibraryPublication {
        mustRunAfter("dokkaJavadocJar")
    }
}

defaultTasks("clean", "build", "dokkaJavadocJar", "libraryDistribution")
