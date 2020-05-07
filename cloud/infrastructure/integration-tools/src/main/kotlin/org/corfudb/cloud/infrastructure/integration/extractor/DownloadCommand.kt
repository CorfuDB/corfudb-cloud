package org.corfudb.cloud.infrastructure.integration.extractor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import org.corfudb.cloud.infrastructure.integration.ArchiveConfig
import org.corfudb.cloud.infrastructure.integration.IntegrationToolConfig
import org.corfudb.cloud.infrastructure.integration.kv.KvStore
import org.corfudb.cloud.infrastructure.integration.kv.ProcessingMessage
import org.corfudb.cloud.infrastructure.integration.kv.RocksDbManager
import org.slf4j.LoggerFactory
import java.net.URL
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*

class DownloadCommand : CliktCommand(name = "download") {
    private val aggregationUnit: String by argument(help = "In this case An aggregation unit is an equivalent " +
            "of an elasticSearchIndex.")
    private val config by option().file().required()

    override fun run() {
        val mapper = jacksonObjectMapper()
        val kvStore = KvStore(RocksDbManager.provider, mapper)
        DownloadManager(
                kvStore,
                aggregationUnit,
                mapper.readValue(config, IntegrationToolConfig::class.java).archives
        ).download()
    }
}

class DownloadManager(
        private val kvStore: KvStore,
        private val aggregationUnit: String,
        private val archives: List<ArchiveConfig>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun download() {
        archives.forEach { archive ->
            log.info("start downloading: ${archive.url}")
            kvStore.put(ProcessingMessage.new(aggregationUnit, "start downloading: ${archive.url}"))

            val archiveDir = Paths.get("/data/archives", aggregationUnit)

            archiveDir.toFile().mkdirs();
            download(archive.url, archiveDir.resolve("${archive.name}.tgz"))
        }
    }

    private fun download(url: String, directory: Path) {
        log.info("Download archive: $url")
        kvStore.put(ProcessingMessage.new(aggregationUnit, "Download archive: $url"))

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
