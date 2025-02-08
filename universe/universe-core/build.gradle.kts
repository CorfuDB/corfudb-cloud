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
apply(from="${gradleScriptDir}/dependencies.gradle")
apply(from="${gradleScriptDir}/jacoco.gradle")
apply(from="${gradleScriptDir}/checkstyle.gradle")
apply(from="${gradleScriptDir}/java.gradle")
apply(from="${gradleScriptDir}/idea.gradle")
apply(from="${gradleScriptDir}/publishing.gradle.kts")

version = "1.0.0-SNAPSHOT"

val corfuVersion = project.ext["corfuVersion"]


dependencies {
    implementation("com.github.docker-java:docker-java:3.4.1")
    implementation("org.apache.commons:commons-lang3:3.12.0")

    implementation("org.corfudb:runtime:${corfuVersion}") {
        exclude(group="io.netty", module="netty-tcnative")
    }

    implementation("org.corfudb:infrastructure:${corfuVersion}") {
        exclude(group="io.netty", module="netty-tcnative")
    }

    implementation("com.cloudbees.thirdparty:vijava:5.5-beta")

    implementation("org.apache.ant:ant-jsch:1.10.7")
}
