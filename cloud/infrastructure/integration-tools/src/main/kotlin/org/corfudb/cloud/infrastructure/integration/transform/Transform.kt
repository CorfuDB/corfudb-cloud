package org.corfudb.cloud.infrastructure.integration.transform

import org.corfudb.cloud.infrastructure.integration.IntegrationToolConfig
import org.corfudb.cloud.infrastructure.integration.kv.KvStore
import org.corfudb.cloud.infrastructure.integration.kv.ProcessingMessage
import org.slf4j.LoggerFactory
import java.nio.file.Paths

class Transform(
        private val kvStore: KvStore,
        private val aggregationUnit: String,
        private val config: IntegrationToolConfig
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun transform() {
        log.info("Start transforming step...")

        val destDir = Paths.get("/data", aggregationUnit)

        config.transform.forEach { transformation ->
            kvStore.put(ProcessingMessage.new(aggregationUnit, "Execute transformation: $transformation"))
            try {
                val logDirFullPath = destDir.resolve(transformation.path)
                if (logDirFullPath.toFile().exists()) {

                    transformation.commands.forEach { command ->
                        val process = ProcessBuilder()
                                .command(listOf("/bin/sh", "-c", command))
                                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                                .redirectError(ProcessBuilder.Redirect.INHERIT)
                                .start()
                        process.waitFor()

                        val inputStream = process.inputStream
                        val output: String = inputStream.bufferedReader().use { it.readText() }
                        kvStore.put(ProcessingMessage.new(aggregationUnit, "Transformation completed: $output"))
                    }
                }
            } catch (ex: Exception) {
                log.error("Error transform logs", ex)
                kvStore.put(ProcessingMessage.new(aggregationUnit, "Error on transform step: ${ex.message}"))
            }
        }
    }
}
