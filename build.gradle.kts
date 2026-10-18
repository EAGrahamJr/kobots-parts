import crackers.buildstuff.semver.SimpleSemverVersion

plugins {
    kotlin("jvm") version "2.4.21"
    idea
//    id("org.jmailen.kotlinter") version "5.4.2"
    id("org.jetbrains.dokka") version "2.2.0"
    id("crackers.buildstuff.library-publish") version "1.3.0"
    id("crackers.buildstuff.simple-semver") version "1.3.0"
    id("com.github.ben-manes.versions") version "0.66.0"
}

repositories {
    mavenCentral()
    mavenLocal()
}


group = "crackers.kobots"

dependencies {
    val DEVICES_VER = "0.2+"
    val HASSK_VER = "0+"

    val MQTT_VER = "1.2.5"
    val COROUT_VER = "1.11.0"

    val KOTEST_VER = "6.2.6"
    val JSON_VER = "20260814"
    val TYPESAVE_VER = "1.4.9"

    // these are likely to be the ones in use
    compileOnly("crackers.kobots:kobots-devices:$DEVICES_VER")
    compileOnly("org.json:json:$JSON_VER")
    compileOnly("com.typesafe:config:$TYPESAVE_VER")

    // optional parts
    compileOnly("crackers.automation:hassk:$HASSK_VER") {
        exclude(group = "ch.qos.logback")
    }
    compileOnly("org.eclipse.paho:org.eclipse.paho.mqttv5.client:$MQTT_VER")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:$COROUT_VER")

    testImplementation("io.kotest:kotest-runner-junit5:$KOTEST_VER")
    testImplementation("io.kotest:kotest-extensions-junitxml:$KOTEST_VER")
    testImplementation("io.kotest:kotest-property:$KOTEST_VER")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("ch.qos.logback:logback-classic:1.6.5")

    // re-create all the depndencies for testing
    testImplementation("crackers.kobots:kobots-devices:$DEVICES_VER")
    testImplementation("crackers.automation:hassk:$HASSK_VER")
    testImplementation("org.json:json:$JSON_VER")
    testImplementation("com.typesafe:config:$TYPESAVE_VER")
    testImplementation("org.eclipse.paho:org.eclipse.paho.mqttv5.client:$MQTT_VER")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$COROUT_VER")
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
            val depSize = configurations["compileClasspath"].files.sumOf {
                logger.warn("Dependency: ${it.name} : ${it.length() / 1024} KB")
                it.length()
            }
            logger.warn(">>> Dependencies size: ${depSize / 1024} KB")
            logger.warn("supposed version $version")
        }
    }
    dependencyUpdates {
        rejectPreReleases = true
    }

}

defaultTasks("clean", "build", "libraryDistribution")
