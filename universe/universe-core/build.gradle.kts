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
}

val gradleScriptDir = "${rootDir.parent}/gradle"
apply(from="${gradleScriptDir}/dependencies.gradle")
apply(from="${gradleScriptDir}/jacoco.gradle")
apply(from="${gradleScriptDir}/spotbugs.gradle")
apply(from="${gradleScriptDir}/checkstyle.gradle")
apply(from="${gradleScriptDir}/java.gradle")
apply(from="${gradleScriptDir}/idea.gradle")
apply(from="${gradleScriptDir}/publishing.gradle.kts")

version = "1.0.0-SNAPSHOT"

val corfuVersion = project.ext["corfuVersion"]

dependencies {
    implementation("com.spotify:docker-client:8.16.0")

    implementation("org.corfudb:runtime:${corfuVersion}") {
        exclude(group="io.netty", module="netty-tcnative")
    }

    implementation("org.corfudb:infrastructure:${corfuVersion}") {
        exclude(group="io.netty", module="netty-tcnative")
    }

    implementation("com.cloudbees.thirdparty:vijava:5.5-beta")

    implementation("org.apache.ant:ant-jsch:1.10.7")
}
