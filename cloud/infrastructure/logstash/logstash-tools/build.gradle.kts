plugins {
    java
}

apply(from = "${rootDir}/gradle/dependencies.gradle")
apply(from = "${rootDir}/gradle/java.gradle")
apply(from = "${rootDir}/gradle/idea.gradle")

dependencies {
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("org.testcontainers:testcontainers:1.13.0")

    testImplementation("org.testcontainers:junit-jupiter:1.13.0")

    compileOnly ("org.immutables:value:2.8.2")
    annotationProcessor("org.immutables:value:2.8.2")
}

sourceSets{
    test {
        resources {
            srcDir("../filebeat")
        }
    }
}

