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

        config.archives.forEach { archive ->
            val destDir = Paths.get("/data", aggregationUnit, archive.name)

            config.transform.forEach { transformation ->
                kvStore.put(ProcessingMessage.new(aggregationUnit, "Execute transformation: $transformation"))
                try {
                    val logDirFullPath = destDir.resolve(transformation.path)
                    log.info("Execute pre-processing. Work dir: $logDirFullPath")

                    if (logDirFullPath.toFile().exists()) {

                        transformation.commands.forEach { command ->
                            log.info("Pre-processing step: $command")

                            val process = ProcessBuilder()
                                    .directory(logDirFullPath.toFile())
                                    .command(listOf("sh", "-c", command))
                                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                                    .start()

                            process.waitFor()

                            val inputStream = process.inputStream
                            val output: String = inputStream.bufferedReader().use { it.readText() }
                            kvStore.put(ProcessingMessage.new(aggregationUnit, "Transformation completed: $output"))
                        }
                    } else {
                        log.error("Log dir doesn't exists: $logDirFullPath, can't execute pre-processing step")
                    }
                } catch (ex: Exception) {
                    log.error("Error transform logs", ex)
                    kvStore.put(ProcessingMessage.new(aggregationUnit, "Error on transform step: ${ex.message}"))
                }
            }
        }
    }
}
