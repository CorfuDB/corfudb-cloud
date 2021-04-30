buildscript {
    dependencies {
        classpath("com.google.guava:guava:28.1-jre")
    }
}

plugins {
    java
    id("io.freefair.lombok") version "5.3.0"
    id("checkstyle")
    id("com.github.spotbugs") version "3.0.0"
    id("jacoco")
    id("maven-publish")
    id("com.jfrog.artifactory") version "4.18.0"
}

val gradleScriptDir = "${rootDir.parent}/gradle"
apply(from="$gradleScriptDir/dependencies.gradle")
apply(from="$gradleScriptDir/publishing.gradle.kts")
apply(from="$gradleScriptDir/jacoco.gradle")
apply(from="$gradleScriptDir/spotbugs.gradle")
apply(from="$gradleScriptDir/checkstyle.gradle")
apply(from="$gradleScriptDir/java.gradle")
apply(from="$gradleScriptDir/idea.gradle")

version = "1.0.0-SNAPSHOT"

val corfuVersion = project.ext["corfuVersion"]

dependencies {
    implementation(project(":universe-core"))

    implementation("org.corfudb:corfudb-common:${corfuVersion}") {
        exclude(group="io.netty", module="netty-tcnative")
    }

    implementation("com.spotify:docker-client:8.16.0")
}

