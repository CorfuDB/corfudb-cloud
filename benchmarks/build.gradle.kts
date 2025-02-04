buildscript {
    dependencies {
        classpath("com.google.guava:guava:32.1.2-jre")
    }
}

plugins {
    java
    idea
    id("com.google.protobuf") version "0.9.3"
    id("com.google.osdetector") version "1.6.2"
    id("io.freefair.lombok") version "6.6.3"
    id("checkstyle")
    id("jacoco")
    id("maven-publish")
}

val gradleScriptsDir: String = project.rootDir.parent
apply(from = "${gradleScriptsDir}/gradle/dependencies.gradle")
apply(from = "${gradleScriptsDir}/gradle/jacoco.gradle")
apply(from = "${gradleScriptsDir}/gradle/configure.gradle")
apply(from = "${gradleScriptsDir}/gradle/protobuf.gradle")
apply(from = "${gradleScriptsDir}/gradle/checkstyle.gradle")
apply(from = "${gradleScriptsDir}/gradle/corfu.gradle")
apply(from = "${gradleScriptsDir}/gradle/java.gradle")

apply(from = "${gradleScriptsDir}/gradle/idea-project.gradle.kts")
apply(from = "${gradleScriptsDir}/gradle/idea.gradle")

version = "1.0.0"

val corfuVersion = project.ext["corfuVersion"] as String
val nettyTcnativeVersion = project.ext["nettyTcnativeVersion"] as String
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
    implementation("io.netty:netty-tcnative:${nettyTcnativeVersion}:${osdetector.os}-${osdetector.arch}")

    implementation("org.openjdk.jmh:jmh-core:${jmhSdkVersion}")
    implementation("org.openjdk.jmh:jmh-generator-annprocess:${jmhSdkVersion}")
    implementation("org.openjdk.jmh:jmh-generator-annprocess:${jmhSdkVersion}")

    implementation("org.openjdk.jmh:jmh-core:${jmhSdkVersion}")

    implementation("org.ehcache:ehcache:${ehcacheVersion}")

    implementation("org.assertj:assertj-core:${assertjVersion}")

    implementation("ch.qos.logback:logback-classic:${project.extra.get("logbackVersion")}")

    implementation("org.projectlombok:lombok:${lombokVersion}")
    annotationProcessor("org.projectlombok:lombok:${lombokVersion}")
}
repositories {
    mavenCentral()
}
