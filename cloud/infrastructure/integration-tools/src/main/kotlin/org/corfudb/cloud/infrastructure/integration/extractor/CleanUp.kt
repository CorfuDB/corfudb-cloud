package org.corfudb.cloud.infrastructure.integration.extractor

import java.nio.file.Paths

class CleanUp(private val aggregationUnit: String) {

    fun cleanUpArchives() {
        val archiveDir = Paths.get("/data/archives", aggregationUnit)
        archiveDir.toFile().deleteRecursively()
    }

    fun cleanUpUnPacked() {
        val dataDir = Paths.get("/data", aggregationUnit)
        dataDir.toFile().deleteRecursively()
    }
}