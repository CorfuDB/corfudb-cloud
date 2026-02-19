pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.palantir.docker") {
                useModule("com.palantir.gradle.docker:gradle-docker:${requested.version}")
            }
        }
    }
}

rootProject.name = "corfu-universe-tests"

includeBuild("../universe")
