package org.corfudb.cloud.infrastructure.integration.processing

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import org.slf4j.LoggerFactory

/**
 * Composed from Download and unpack commands
 */
class ProcessingCommand : CliktCommand(name = "processing") {
    private val log = LoggerFactory.getLogger(javaClass)

    private val archiveFile: String by option(help = "archive file").required()
    private val url: String by option(help = "archive url").required()
    private val aggregationUnit: String by argument(help = "In this case An aggregation unit is an equivalent " +
            "of an elasticSearchIndex.")

    override fun run() {
        try {
            DownloadManager(archiveFile, url, aggregationUnit).download()
        } catch (e: Exception) {
            log.error("Can't download archive file: $archiveFile")
            return
        }
        try {
            ArchiveManager(archiveFile, aggregationUnit).unArchive()
        }catch (e: Exception) {
            log.error("Can't unarchive: $archiveFile")
        }
    }
}
