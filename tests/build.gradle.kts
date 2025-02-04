buildscript {
    dependencies {
        classpath("com.google.guava:guava:28.1-jre")
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
    id("com.palantir.docker") version "0.36.0"
}

val gradleScriptsDir: String = project.rootDir.parent
apply(from="${gradleScriptsDir}/gradle/dependencies.gradle")
apply(from="${gradleScriptsDir}/gradle/jacoco.gradle")
apply(from="${gradleScriptsDir}/gradle/configure.gradle")
apply(from="${gradleScriptsDir}/gradle/protobuf.gradle")
apply(from="${gradleScriptsDir}/gradle/checkstyle.gradle")
apply(from="${gradleScriptsDir}/gradle/corfu.gradle")
apply(from="${gradleScriptsDir}/gradle/universe.gradle")
apply(from="${gradleScriptsDir}/gradle/java.gradle")

apply(from="${gradleScriptsDir}/gradle/idea-project.gradle.kts")
apply(from="${gradleScriptsDir}/gradle/idea.gradle")

version = "1.0.0"

val corfuVersion = project.ext["corfuVersion"]
val nettyTcnativeVersion = project.ext["nettyTcnativeVersion"]
val assertjVersion = project.ext["assertjVersion"]
val lombokVersion = project.ext["lombokVersion"]

dependencies {
    implementation("org.corfudb:universe-core:1.0.0-SNAPSHOT")

    // swagger/mangle dependencies
    implementation("io.swagger:swagger-annotations:1.5.17")
    implementation("com.squareup.okhttp:okhttp:2.7.5")
    implementation("com.squareup.okhttp:logging-interceptor:2.7.5")
    implementation("com.google.code.gson:gson:2.8.1")
    implementation("com.google.guava:guava") {
        version {
            strictly("28.0-jre")
        }
    }
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

    implementation("io.netty:netty-tcnative:${nettyTcnativeVersion}:${osdetector.os}-${osdetector.arch}")

    implementation("org.assertj:assertj-core:${assertjVersion}")

    compileOnly("org.projectlombok:lombok:${lombokVersion}")
    annotationProcessor("org.projectlombok:lombok:${lombokVersion}")
}

tasks {
    dockerPrepare {

        doLast {
            project.copy {
                from("${project.rootDir.parent}/gradle")
                into("$buildDir/docker/gradle")
            }

            project.copy {
                from("${project.rootDir}/gradle/wrapper")
                into("$buildDir/docker/tests/gradle/wrapper")
            }

            project.copy {
                from("${project.rootDir}/config")
                into("$buildDir/docker/config")
            }

            project.copy {
                from("${project.rootDir}/src")
                into("$buildDir/docker/tests/src")
            }

            project.copy {
                from("${project.rootDir}/build.gradle.kts")
                into("$buildDir/docker/tests/")
            }

            project.copy {
                from("${project.rootDir}/gradle.properties")
                into("$buildDir/docker/tests/")
            }

            project.copy {
                from("${project.rootDir}/gradlew")
                into("$buildDir/docker/tests/")
            }

            project.copy {
                from("${project.rootDir}/gradlew.bat")
                into("$buildDir/docker/tests/")
            }

            project.copy {
                from("${project.rootDir}/settings.gradle.kts")
                into("$buildDir/docker/tests/")
            }
        }
    }

    //Archive correctness logs
    register<Zip>("correctnessLogDistribution") {
        val longevityDir = project.buildDir.resolve("corfu-longevity-app")

        archiveFileName.set("correctness.log.zip")
        destinationDirectory.set(longevityDir)

        from(longevityDir.resolve("correctness.log"))
    }

    dockerPush {
        dependsOn(check)
    }

    build {
        dependsOn(dockerPush)
    }
}

docker {
    name = "corfudb/${project.name}:latest"
    setDockerfile(file("Dockerfile"))
}
