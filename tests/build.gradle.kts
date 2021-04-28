buildscript {
    dependencies {
        classpath("com.google.guava:guava:28.1-jre")
    }
}

plugins {
    java
    idea
    id("com.google.protobuf") version "0.8.11"
    id("com.google.osdetector") version "1.6.2"
    id("io.freefair.lombok") version "5.3.0"
    id("checkstyle")
    id("com.github.spotbugs") version "3.0.0"
    id("jacoco")
}

idea {
    project {
        // Set the version control system
        // to Git for this project.
        // All values IntelliJ IDEA supports
        // can be used. E.g. Subversion, Mercurial.
        vcs = "Git"

        setLanguageLevel("1.8")
    }

    module {
        isDownloadJavadoc = true
        isDownloadSources = false
    }
}

val gradleScriptsDir: String = project.rootDir.parent
apply(from="${gradleScriptsDir}/gradle/dependencies.gradle")
apply(from="${gradleScriptsDir}/gradle/jacoco.gradle")
apply(from="${gradleScriptsDir}/gradle/spotbugs.gradle")
apply(from="${gradleScriptsDir}/gradle/configure.gradle")
apply(from="${gradleScriptsDir}/gradle/protobuf.gradle")
apply(from="${gradleScriptsDir}/gradle/checkstyle.gradle")
apply(from="${gradleScriptsDir}/gradle/corfu.gradle")
apply(from="${gradleScriptsDir}/gradle/universe.gradle")
apply(from="${gradleScriptsDir}/gradle/java.gradle")
apply(from="${gradleScriptsDir}/gradle/idea.gradle")

version = "1.0.0"

val corfuVersion = project.ext["corfuVersion"]
val nettyVersion = project.ext["nettyVersion"]
val assertjVersion = project.ext["assertjVersion"]
val lombokVersion = project.ext["lombokVersion"]

dependencies {
    implementation("org.corfudb:universe-core:1.0.0-SNAPSHOT")

    // swagger/mangle dependencies
    implementation("io.swagger:swagger-annotations:1.5.17")
    implementation("com.squareup.okhttp:okhttp:2.7.5")
    implementation("com.squareup.okhttp:logging-interceptor:2.7.5")
    implementation("com.google.code.gson:gson:2.8.1")
    implementation("io.gsonfire:gson-fire:1.8.0")
    implementation("org.threeten:threetenbp:1.3.5")

    implementation("org.corfudb:infrastructure:${corfuVersion}") {
        exclude(group="io.netty", module="netty-tcnative")
    }

    implementation("org.corfudb:corfudb-tools:${corfuVersion}") {
        exclude(group="io.netty", module="netty-tcnative")
    }

    implementation("org.corfudb:runtime:${corfuVersion}") {
        exclude(group="io.netty", module="netty-tcnative")
    }

    implementation("io.netty:netty-tcnative:${nettyVersion}:${osdetector.os}-${osdetector.arch}")

    implementation("org.assertj:assertj-core:${assertjVersion}")

    compileOnly("org.projectlombok:lombok:${lombokVersion}")
    annotationProcessor("org.projectlombok:lombok:${lombokVersion}")
}
