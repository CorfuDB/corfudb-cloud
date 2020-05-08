package org.corfudb.cloud.infrastructure.integration.extractor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.IOUtils
import org.apache.tools.ant.Project
import org.apache.tools.ant.taskdefs.GUnzip
import org.corfudb.cloud.infrastructure.integration.IntegrationToolConfig
import org.corfudb.cloud.infrastructure.integration.kv.KvStore
import org.corfudb.cloud.infrastructure.integration.kv.ProcessingMessage
import org.corfudb.cloud.infrastructure.integration.kv.RocksDbManager
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

class UnpackCommand : CliktCommand(name = "unpack") {
    companion object {
        val defaultProject: Project = Project()
    }

    private val aggregationUnit: String by argument(help = "In this case An aggregation unit is an equivalent " +
            "of an elasticSearchIndex.")
    private val config by option().file().required()

    override fun run() {
        val mapper = jacksonObjectMapper()
        val kvStore = KvStore(RocksDbManager.provider, mapper)
        ArchiveManager(
                kvStore,
                aggregationUnit,
                mapper.readValue(config, IntegrationToolConfig::class.java)
        ).unArchive()
    }
}

class ArchiveManager(
        private val kvStore: KvStore,
        private val aggregationUnit: String,
        private val config: IntegrationToolConfig
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun unArchive() {
        val archiveDir = Paths.get("/data/archives", aggregationUnit)
        val destDir = Paths.get("/data", aggregationUnit)

        archiveDir.toFile().mkdirs();

        config.archives.forEach { archive ->
            try {
                val unzipped = GzipFile(archiveDir, archiveDir.resolve("${archive.name}.tgz"), destDir)
                        .unzipArchive()
                //rename unzipped to the server name
                renameArchiveDir(destDir, unzipped, archive.name)

                config.logDirectories.forEach { logDir ->
                    val logDirFullPath = destDir.resolve(archive.name).resolve(logDir)
                    if (logDirFullPath.toFile().exists()) {
                        unzipLogs(logDirFullPath)
                    }
                }
            } catch (ex: Exception) {
                kvStore.put(ProcessingMessage.new(aggregationUnit, "Fail to unpack the whole Archive: ${archive.name}.tgz"))
            }
        }
    }

    /**
     * Unzip all *log.gz files
     */
    private fun unzipLogs(logsDir: Path) {
        log.info("Unzip logs: $logsDir")

        kvStore.put(ProcessingMessage.new(aggregationUnit, "Unzip logs: $logsDir"))

        val tgzFiles = Files.list(logsDir)
                .filter { file ->
                    file.toFile().extension == "gz"
                }
                .collect(Collectors.toList())

        tgzFiles.forEach { logFile ->
            log.info("Unzip: $logFile")
            kvStore.put(ProcessingMessage.new(aggregationUnit, "Unzip file: $logFile"))

            try {
                val unzip = GUnzip()
                unzip.project = UnpackCommand.defaultProject
                unzip.setSrc(logFile.toFile())
                unzip.execute()
            } catch (e: Exception) {
                log.error("Can't unpack file: $logFile")
                kvStore.put(ProcessingMessage.new(aggregationUnit, "Can't unpack file: $logFile"))
            }

            logFile.toFile().delete()
        }
    }

    private fun renameArchiveDir(dir: Path, oldName: String, newName: String) {

        //check if already renamed
        val newDir = dir.resolve(newName)
        if (newDir.toFile().exists()) {
            newDir.toFile().deleteRecursively();
        }

        Files.move(dir.resolve(oldName), dir.resolve(newName))
    }
}

class GzipFile(private val dataDir: Path, private val inputFile: Path, private val outputFile: Path) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun unzipArchive(): String {
        log.info("Unzip archive: $inputFile")

        val fis = FileInputStream(inputFile.toFile())
        val gzipIs = GzipCompressorInputStream(fis)

        var name: String? = null;

        TarArchiveInputStream(gzipIs).use { tarIs ->
            var entry: TarArchiveEntry?

            while (true) {
                entry = tarIs.nextTarEntry
                if (entry == null) {
                    break
                }

                if (name == null) {
                    name = entry.name
                }

                if (entry.isDirectory) {
                    continue
                }

                val currFile = outputFile.resolve(entry.name)
                val parent: Path = currFile.parent

                val archive = dataDir.resolve(name!!)
                val varLog = archive.resolve(Paths.get("var", "log"))

                val logDirs: Set<Path> = hashSetOf(varLog)

                for (logDir in logDirs) {
                    if (dataDir.resolve(entry.name).startsWith(logDir)) {
                        val parentDir = parent.toFile()
                        if (!parentDir.exists()) {
                            parentDir.mkdirs()
                        }

                        IOUtils.copy(tarIs, FileOutputStream(currFile.toFile()))
                    }
                }
            }
        }

        return name!!
    }
}
