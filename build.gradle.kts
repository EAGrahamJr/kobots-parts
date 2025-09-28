import crackers.buildstuff.semver.SimpleSemverVersion

plugins {
    kotlin("jvm") version "2.0.20"
    idea
    // TODO IDEA formatting != "official" (wtf guys)
//    id("org.jmailen.kotlinter") version "3.12.0"
   id("org.jetbrains.dokka") version "1.8.10"
   id("crackers.buildstuff.library-publish") version "1.3.0"
   id("crackers.buildstuff.simple-semver") version "1.3.0"
}

repositories {
    mavenCentral()
    mavenLocal()
}

val DIOZERO_VER = "1.4.0"
val DEVICES_VER = "0.2+"
val HASSK_VER = "0+"

group = "crackers.kobots"

dependencies {
    // these are likely to be the ones in use
    compileOnly("crackers.kobots:kobots-devices:$DEVICES_VER")
    compileOnly("org.json:json:20231013")
    compileOnly("com.typesafe:config:1.4.2")

    // optional parts
    compileOnly("crackers.automation:hassk:$HASSK_VER") {
        exclude(group = "ch.qos.logback")
    }
    compileOnly("org.eclipse.paho:org.eclipse.paho.mqttv5.client:1.2.5")

    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-extensions-junitxml:5.9.1")
    testImplementation("io.mockk:mockk:1.13.5")
    testImplementation("ch.qos.logback:logback-classic:1.5.13")

    // re-create all the depndencies for testing
    testImplementation("crackers.kobots:kobots-devices:$DEVICES_VER")
    testImplementation("org.json:json:20231013")
    testImplementation("com.typesafe:config:1.4.2")
    testImplementation("org.eclipse.paho:org.eclipse.paho.mqttv5.client:1.2.5")
}

kotlin {
    jvmToolchain(17)
}

//kotlinter {
//    // ignore failures because the build re-formats it
//    ignoreFailures = true
//    disabledRules = arrayOf("no-wildcard-imports")
//}
val semver: Provider<SimpleSemverVersion> by project
version = semver.get().toString()

tasks {
    build {
//        dependsOn("formatKotlin")
    }
    test {
        useJUnitPlatform()
        reports {
            junitXml.required.set(true)
        }
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

    register("depSize") {
        doLast {
            val depSize = configurations["compileClasspath"].files.sumOf { it.length() }
            logger.warn(">>> Dependencies size: ${depSize / 1024} KB")
            logger.warn("supposed version $version")
        }
    }
}

defaultTasks("clean", "build", "dokkaJavadocJar", "libraryDistribution")
