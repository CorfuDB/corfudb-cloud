package org.corfudb.cloud.infrastructure.integration.processing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import org.corfudb.cloud.infrastructure.integration.IntegrationToolConfig
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

        val archives = toolsConfig.archives
        try {
            DownloadManager(aggregationUnit, archives).download()
        } catch (e: Exception) {
            log.error("Unexpected error", e)
            return
        }
        try {
            ArchiveManager(aggregationUnit, archives).unArchive()
        } catch (e: Exception) {
            log.error("Unexpected error", e)
        }

        LoaderManager(aggregationUnit, toolsConfig).execute()
    }
}
