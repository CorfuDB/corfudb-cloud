package org.corfudb.cloud.infrastructure.integration.processing

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import org.slf4j.LoggerFactory
import java.net.URL
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*

class DownloadCommand : CliktCommand(name = "download") {
    private val archiveFile: String by option(help = "archive file").required()
    private val url: String by option(help = "archive url").required()
    private val aggregationUnit: String by argument(help = "In this case An aggregation unit is an equivalent " +
            "of an elasticSearchIndex.")

    override fun run() {
        DownloadManager(archiveFile, url, aggregationUnit).download()
    }
}

class DownloadManager(private val archiveFile: String, private val url: String, private val aggregationUnit: String) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun download() {
        log.info("start downloading: $url")

        val archiveDir = Paths.get("/archives", aggregationUnit)

        archiveDir.toFile().mkdirs();

        download(url, archiveDir.resolve(archiveFile))
    }

    private fun download(url: String, directory: Path) {
        log.info("Download archive: $url")

        val archiveChannel = Channels.newChannel(URL(url).openStream())

        val options = EnumSet.of(
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE_NEW
        )

        val archiveFile = FileChannel.open(directory, options)
        archiveFile.transferFrom(archiveChannel, 0, Long.MAX_VALUE)
        archiveFile.close()
    }
}