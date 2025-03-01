buildscript {
    dependencies {
        classpath("com.google.guava:guava:28.1-jre")
    }
}

plugins {
    java
    id("io.freefair.lombok") version "8.12.1"
    id("checkstyle")
    id("jacoco")
    id("maven-publish")
}

val gradleScriptDir = "${rootDir.parent}/gradle"
apply(from="$gradleScriptDir/dependencies.gradle")
apply(from="$gradleScriptDir/publishing.gradle.kts")
apply(from="$gradleScriptDir/jacoco.gradle")
apply(from="$gradleScriptDir/checkstyle.gradle")
apply(from="$gradleScriptDir/java.gradle")
apply(from="$gradleScriptDir/idea.gradle")

version = "1.0.0-SNAPSHOT"

val corfuVersion = project.ext["corfuVersion"]

dependencies {
    implementation(project(":universe-core"))

    implementation("com.github.docker-java:docker-java:3.4.1")

    implementation("org.corfudb:corfudb-common:${corfuVersion}") {
        exclude(group="io.netty", module="netty-tcnative")
    }
}

