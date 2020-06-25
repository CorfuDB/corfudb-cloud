package org.corfudb.cloud.infrastructure.integration.transform

import org.corfudb.cloud.infrastructure.integration.IntegrationToolConfig
import org.corfudb.cloud.infrastructure.integration.kv.KvStore
import org.corfudb.cloud.infrastructure.integration.kv.ProcessingMessage
import java.nio.file.Paths

class Transform(
        private val kvStore: KvStore,
        private val aggregationUnit: String,
        private val config: IntegrationToolConfig
) {

    fun transform() {
        val destDir = Paths.get("/data", aggregationUnit)

        config.transform.forEach { transformation ->
            try {
                val logDirFullPath = destDir.resolve(transformation.path)
                if (logDirFullPath.toFile().exists()) {

                    transformation.commands.forEach { command ->
                        ProcessBuilder()
                                .command(listOf(command))
                                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                                .redirectError(ProcessBuilder.Redirect.INHERIT)
                                .start()
                                .waitFor()
                    }
                }
            } catch (ex: Exception) {
                kvStore.put(ProcessingMessage.new(aggregationUnit, "Error on transform step: ${ex.message}"))
            }
        }
    }
}