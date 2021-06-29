val publishing = extensions.getByName("publishing") as org.gradle.api.publish.PublishingExtension

publishing.repositories {
    maven {
        name = "corfudbCloudPackages"
        url = uri("https://maven.pkg.github.com/corfudb/corfudb-cloud")
        credentials {
            username = System.getenv("PKG_USERNAME")
            password = System.getenv("PUBLISH_TOKEN")
        }
    }
}

publishing.publications {
    create<MavenPublication>("maven") {
        from(components["java"])
    }
}
