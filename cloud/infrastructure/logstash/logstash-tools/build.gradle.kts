plugins {
    java
}

val gradleScriptsDir: String = project.rootDir.parentFile.parent
apply(from = "${gradleScriptsDir}/gradle/dependencies.gradle")
apply(from = "${gradleScriptsDir}/gradle/java.gradle")
apply(from = "${gradleScriptsDir}/gradle/idea.gradle")

val testcontainersVersion = project.ext["testcontainersVersion"] as String
testcontainersVersion

dependencies {
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("org.testcontainers:testcontainers:${testcontainersVersion}")

    testImplementation("org.testcontainers:junit-jupiter:${testcontainersVersion}")

    compileOnly ("org.immutables:value:2.8.2")
    annotationProcessor("org.immutables:value:2.8.2")
}

sourceSets{
    test {
        resources {
            srcDir("../logstash/configuration")
        }
    }
}

