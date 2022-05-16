import java.nio.charset.StandardCharsets

plugins {
    id("com.palantir.docker") version "0.25.0"
    java
}


val gradleScriptsDir: String = project.rootDir.parent
apply(from="${gradleScriptsDir}/gradle/dependencies.gradle")
val corfuVersion = project.ext["corfuVersion"]


dependencies {

    implementation("ch.qos.logback:logback-classic:1.2.3")


    implementation("org.corfudb:runtime:${corfuVersion}") {
        exclude(group = "io.netty", module = "netty-tcnative")
    }

    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.5.2")
}

version = project.file("version")
        .readText(StandardCharsets.UTF_8)
        .trim()
        .substring("version=".length)

tasks.withType<Jar> {
    archiveFileName.set("${project.name}.${archiveExtension.get()}")
    manifest {
        attributes["Main-Class"] = "org.corfudb.cloud.runtime.example.Main"
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


    }
}

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
