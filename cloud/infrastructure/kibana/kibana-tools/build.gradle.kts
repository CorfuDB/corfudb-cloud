import java.nio.charset.StandardCharsets

plugins {
    kotlin("jvm") version "1.3.71"
    kotlin("plugin.serialization") version "1.3.71"
    id("com.palantir.docker") version "0.25.0"
}

apply {
    from("${rootDir.parent}/gradle/dependencies.gradle")
    from("${rootDir.parent}/gradle/idea.gradle")
}

val ktorVersion = project.extra.get("ktorVersion")

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.20.0")

    implementation("ch.qos.logback:logback-classic:${project.extra.get("logbackVersion")}")

    implementation("com.github.ajalt:clikt:2.6.0")

    implementation("io.ktor:ktor-client:$ktorVersion")
    implementation("io.ktor:ktor-client-auth:$ktorVersion")
    implementation("io.ktor:ktor-client-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-auth-basic:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-logging-jvm:$ktorVersion")

    implementation("org.apache.commons:commons-lang3:3.10")
}

version = project.file("version")
        .readText(StandardCharsets.UTF_8)
        .trim()
        .substring("version=".length)

tasks.withType<Jar> {
    archiveFileName.set("${project.name}.${archiveExtension.get()}")
    manifest {
        attributes["Main-Class"] = "org.corfudb.cloud.infrastructure.kibana.tools.MainKt"
    }
}

tasks.dockerPrepare {
    dependsOn(tasks.jar)

    doLast {
        project.copy {
            from(configurations.runtimeClasspath.get())
            into("$buildDir/docker/lib")
        }

        project.copy {
            from(tasks.jar.get() as CopySpec)
            into("$buildDir/docker/")
        }

        project.copy {
            from("$projectDir/bin")
            into("$buildDir/docker/bin")
        }

        project.copy {
            from("${projectDir.parentFile}/dashboard/")
            into("$buildDir/docker/")
        }
    }
}

/**
 * https://help.github.com/en/actions/language-and-framework-guides/publishing-docker-images
 */
tasks.dockerPush {
    dependsOn(tasks.check)
}

tasks.build {
    dependsOn(tasks.dockerPush)
}

docker {
    name = "corfudb/${project.name}:latest"
    setDockerfile(file("Dockerfile"))
}

tasks.create("version").doLast {
    println(version)
}
