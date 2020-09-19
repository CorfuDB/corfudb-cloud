buildscript {
    dependencies {
        classpath("com.google.guava:guava:28.1-jre")
    }
}

plugins {
    java
    id("io.freefair.lombok") version "4.1.6"
    id("checkstyle")
    id("com.github.spotbugs") version "3.0.0"
    id("jacoco")
    id("maven-publish")
    id("com.jfrog.artifactory") version "4.14.1"
}

apply(from="${rootDir}/gradle/dependencies.gradle")
apply(from="${rootDir}/gradle/jacoco.gradle")
apply(from="${rootDir}/gradle/spotbugs.gradle")
apply(from="${rootDir}/gradle/checkstyle.gradle")
apply(from="${rootDir}/gradle/java.gradle")
apply(from="${rootDir}/gradle/idea.gradle")

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

publishing {
    repositories {
        maven {
            url = uri("https://oss.jfrog.org/artifactory/oss-snapshot-local")
            if (hasProperty("deployUser") && hasProperty("deployPassword")) {
                credentials {
                    username = project.property("deployUser") as String
                    password = project.property("deployPassword") as String
                }
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

