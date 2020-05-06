package org.corfudb.cloud.infrastructure.integration.processing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import org.corfudb.cloud.infrastructure.integration.IntegrationToolConfig
import org.corfudb.cloud.infrastructure.integration.extractor.ArchiveManager
import org.corfudb.cloud.infrastructure.integration.extractor.DownloadManager
import org.corfudb.cloud.infrastructure.integration.kibana.KibanaDashboardManager
import org.corfudb.cloud.infrastructure.integration.kv.KvStore
import org.corfudb.cloud.infrastructure.integration.kv.ProcessingMessage
import org.corfudb.cloud.infrastructure.integration.kv.RocksDbManager
import org.corfudb.cloud.infrastructure.integration.loader.LoaderManager
import org.slf4j.LoggerFactory

/**
 * Composed from Download and unpack commands
 */
class ProcessingCommand : CliktCommand(name = "processing") {
    private val log = LoggerFactory.getLogger(javaClass)

    private val aggregationUnit: String by argument(help = "In this case An aggregation unit is an equivalent " +
            "of an elasticSearchIndex.")
    private val config by option().file().required()

    override fun run() {
        val mapper = jacksonObjectMapper()
        val toolsConfig = mapper.readValue(config, IntegrationToolConfig::class.java)
        val kvStore = KvStore(RocksDbManager.provider, jacksonObjectMapper())

        try {
            ProcessingManager(kvStore, aggregationUnit, toolsConfig).execute()
        } catch (e: Exception) {
            log.error("Unexpected error", e)
        }
    }
}

class ProcessingManager(
        private val kvStore: KvStore,
        private val aggregationUnit: String,
        private val toolsConfig: IntegrationToolConfig
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun execute() {
        log.info("Start processing for: $aggregationUnit")
        var message = ProcessingMessage.new(aggregationUnit, "start processing")
        kvStore.put(message.key, message);

        val archives = toolsConfig.archives

        message = ProcessingMessage.new(aggregationUnit, "downloading archives")
        kvStore.put(message.key, message)
        DownloadManager(kvStore, aggregationUnit, archives).download()
        message = ProcessingMessage.new(aggregationUnit, "Downloading completed. Step 1 of 4 finished")
        kvStore.put(message.key, message)

        message = ProcessingMessage.new(aggregationUnit, "start unarchive process")
        kvStore.put(message.key, message)
        ArchiveManager(kvStore, aggregationUnit, toolsConfig).unArchive()
        message = ProcessingMessage.new(aggregationUnit, "Unarchive - completed. Step 2 of 4 finished")
        kvStore.put(message.key, message)

        message = ProcessingMessage.new(aggregationUnit, "start loading logs")
        kvStore.put(message.key, message)
        LoaderManager(kvStore, aggregationUnit, toolsConfig).load()
        message = ProcessingMessage.new(aggregationUnit, "Loading completed. Step 3 of 4 finished")
        kvStore.put(message.key, message)

        message = ProcessingMessage.new(aggregationUnit, "deploying kibana dashboards")
        kvStore.put(message.key, message)
        KibanaDashboardManager(kvStore, aggregationUnit, toolsConfig).execute()
        message = ProcessingMessage.new(aggregationUnit, "Dashboards deployment completed. Step 4 of 4 finished")
        kvStore.put(message.key, message)

        message = ProcessingMessage.new(aggregationUnit, "Done")
        kvStore.put(message.key, message)
    }
}