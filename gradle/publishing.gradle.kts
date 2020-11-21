val publishing = extensions.getByName("publishing") as org.gradle.api.publish.PublishingExtension

publishing.repositories {
    maven {
        url = uri("https://oss.jfrog.org/artifactory/oss-snapshot-local")
        if (hasProperty("jfrog_oss_user") && hasProperty("jfrog_oss_password")) {
            credentials {
                username = project.property("jfrog_oss_user") as String
                password = project.property("jfrog_oss_password") as String
            }
        }
    }
}

publishing.publications {
    create<MavenPublication>("maven") {
        from(components["java"])
    }
}