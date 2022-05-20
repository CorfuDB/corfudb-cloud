import java.nio.charset.StandardCharsets

plugins {
    java
}

repositories {
    mavenLocal()
    mavenCentral()

    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/corfudb/corfudb")
        // For accessing GitHub Secrets in CorfuDB repo
        credentials {
            username = System.getenv("PKG_USERNAME")
            password = System.getenv("PUBLISH_TOKEN")
        }
    }
}

val corfuVersion = "0.3.2-SNAPSHOT"
val logbackVersion = "1.2.11"
val junitVersion = "5.8.2"

dependencies {
    implementation("ch.qos.logback:logback-classic:${logbackVersion}")

    implementation("org.corfudb:runtime:${corfuVersion}") {
        exclude(group = "io.netty", module = "netty-tcnative")
    }

    testImplementation("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
}

version = project.file("version")
    .readText(StandardCharsets.UTF_8)
    .trim()
    .substring("version=".length)

//Fat jar
tasks.withType<Jar> {
    archiveFileName.set("${project.name}.${archiveExtension.get()}")
    manifest {
        attributes["Main-Class"] = "org.corfudb.cloud.runtime.example.Main"
    }

    from(configurations.compileClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}

tasks.create("version").doLast {
    println(version)
}
