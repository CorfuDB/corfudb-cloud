
plugins {
    java
}

apply(from = "${rootDir}/gradle/dependencies.gradle")
apply(from = "${rootDir}/gradle/java.gradle")
apply(from = "${rootDir}/gradle/idea.gradle")

dependencies {
    implementation("ch.qos.logback:logback-classic:1.2.3")

    testImplementation("org.testcontainers:testcontainers:1.13.0")
    testImplementation("org.testcontainers:junit-jupiter:1.13.0")
}

sourceSets.test {
    resources {
        srcDir("../logstash")
    }
}
