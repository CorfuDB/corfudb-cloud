plugins {
    kotlin("jvm") version "1.3.71"
    kotlin("plugin.serialization") version "1.3.71"
}

apply(from = "${rootDir}/gradle/dependencies.gradle")
apply(from = "${rootDir}/gradle/idea.gradle")

val ktorVersion= project.extra.get("ktorVersion")

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.20.0")

    implementation("ch.qos.logback:logback-classic:${project.extra.get("logbackVersion")}")

    implementation("com.github.ajalt:clikt:2.1.0")

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

sourceSets{
    main {
        resources {
            srcDir("../dashboard")
        }
    }
}