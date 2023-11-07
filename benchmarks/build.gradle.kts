buildscript {
    dependencies {
        classpath("com.google.guava:guava:30.1.1-jre")
    }
}

plugins {
    java
    idea
    id("com.google.protobuf") version "0.8.10"
    id("com.google.osdetector") version "1.6.2"
    id("io.freefair.lombok") version "5.3.0"
    id("checkstyle")
    id("com.github.spotbugs") version "3.0.0"
    id("jacoco")
    id("me.champeau.gradle.jmh") version "0.5.3"
    id("maven-publish")
}

val gradleScriptsDir: String = project.rootDir.parent
apply(from = "${gradleScriptsDir}/gradle/dependencies.gradle")
apply(from = "${gradleScriptsDir}/gradle/jacoco.gradle")
apply(from = "${gradleScriptsDir}/gradle/spotbugs.gradle")
apply(from = "${gradleScriptsDir}/gradle/configure.gradle")
apply(from = "${gradleScriptsDir}/gradle/protobuf.gradle")
apply(from = "${gradleScriptsDir}/gradle/checkstyle.gradle")
apply(from = "${gradleScriptsDir}/gradle/corfu.gradle")
apply(from = "${gradleScriptsDir}/gradle/java.gradle")

apply(from = "${gradleScriptsDir}/gradle/idea-project.gradle.kts")
apply(from = "${gradleScriptsDir}/gradle/idea.gradle")

version = "1.0.0"

val corfuVersion = project.ext["corfuVersion"] as String
val nettyVersion = project.ext["nettyVersion"] as String
val assertjVersion = project.ext["assertjVersion"] as String
val lombokVersion = project.ext["lombokVersion"] as String
val jmhSdkVersion = project.ext["jmhVersion"] as String
val ehcacheVersion = project.ext["ehcacheVersion"] as String
val guavaVersion = project.ext["guavaVersion"] as String

dependencies {
    implementation("org.corfudb:universe-core:1.0.0-SNAPSHOT")

    implementation("com.google.guava:guava") {
        version {
            strictly(guavaVersion)
        }
    }

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.14.0")

    implementation("org.corfudb:infrastructure:${corfuVersion}") {
        exclude(group = "io.netty", module = "netty-tcnative")
    }
    implementation("org.corfudb:runtime:${corfuVersion}") {
        exclude(group = "io.netty", module = "netty-tcnative")
    }
    implementation("io.netty:netty-tcnative:${nettyVersion}:${osdetector.os}-${osdetector.arch}")

    jmh("org.openjdk.jmh:jmh-core:${jmhSdkVersion}")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:${jmhSdkVersion}")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:${jmhSdkVersion}")

    implementation("org.openjdk.jmh:jmh-core:${jmhSdkVersion}")

    implementation("org.ehcache:ehcache:${ehcacheVersion}")

    implementation("org.assertj:assertj-core:${assertjVersion}")

    implementation("ch.qos.logback:logback-classic:${project.extra.get("logbackVersion")}")

    compileOnly("org.projectlombok:lombok:${lombokVersion}")
    annotationProcessor("org.projectlombok:lombok:${lombokVersion}")
}
repositories {
    mavenCentral()
}

tasks {
    // documentation https://github.com/melix/jmh-gradle-plugin
    jmh {
        isZip64 = true // Use ZIP64 format for bigger archives
        jmhVersion = jmhSdkVersion
        duplicateClassesStrategy = DuplicatesStrategy.EXCLUDE

        failOnError = true // Should JMH fail immediately if any benchmark had experienced the unrecoverable error?

        humanOutputFile = file("${buildDir}/reports/jmh/human.txt") // human-readable output file
        resultsFile = file("${buildDir}/reports/jmh/results.txt") // results file
        resultFormat = "CSV" // Result format type (one of CSV, JSON, NONE, SCSV, TEXT)
    }

    spotbugsJmh {
        reports {
            xml.isEnabled = false
            html.isEnabled = true
        }
    }
}
