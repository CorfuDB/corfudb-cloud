buildscript {
    dependencies {
        classpath("com.google.guava:guava:28.1-jre")
    }
}

plugins {
    java
    id("com.google.protobuf") version "0.8.10"
    id("com.google.osdetector") version "1.6.2"
    id("io.freefair.lombok") version "4.1.6"
    id("checkstyle")
    id("com.github.spotbugs") version "3.0.0"
    id("jacoco")
    id("me.champeau.gradle.jmh") version "0.5.2"
}

apply(from = "${rootDir}/gradle/dependencies.gradle")
apply(from = "${rootDir}/gradle/jacoco.gradle")
apply(from = "${rootDir}/gradle/spotbugs.gradle")
apply(from = "${rootDir}/gradle/configure.gradle")
apply(from = "${rootDir}/gradle/protobuf.gradle")
apply(from = "${rootDir}/gradle/checkstyle.gradle")
apply(from = "${rootDir}/gradle/corfu.gradle")
apply(from = "${rootDir}/gradle/java.gradle")
apply(from = "${rootDir}/gradle/idea.gradle")

version = "1.0.0"

val corfuVersion = project.ext["corfuVersion"] as String
val nettyVersion = project.ext["nettyVersion"] as String
val assertjVersion = project.ext["assertjVersion"] as String
val lombokVersion = project.ext["lombokVersion"] as String
val jmhSdkVersion = project.ext["jmhVersion"] as String
val rocksdbVersion = project.ext["rocksdbVersion"] as String
val ehcacheVersion = project.ext["ehcacheVersion"] as String

dependencies {
    implementation(project(":corfu-universe"))

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

    implementation("org.rocksdb:rocksdbjni:${rocksdbVersion}")
    implementation("org.ehcache:ehcache:${ehcacheVersion}")

    implementation("org.assertj:assertj-core:${assertjVersion}")

    compileOnly("org.projectlombok:lombok:${lombokVersion}")
    annotationProcessor("org.projectlombok:lombok:${lombokVersion}")
}

tasks {
    jmh {
        isZip64 = true // Use ZIP64 format for bigger archives
        jmhVersion = jmhSdkVersion
    }

    spotbugsJmh {
        reports {
            xml.isEnabled = false
            html.isEnabled = true
        }
    }
}
