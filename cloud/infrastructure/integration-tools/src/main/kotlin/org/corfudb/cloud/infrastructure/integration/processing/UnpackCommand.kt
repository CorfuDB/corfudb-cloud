package org.corfudb.cloud.infrastructure.integration.processing

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.IOUtils
import org.apache.tools.ant.Project
import org.apache.tools.ant.taskdefs.GUnzip
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

    private val archiveFile: String by option(help = "archive file").required()
    private val aggregationUnit: String by argument(help = "In this case An aggregation unit is an equivalent " +
            "of an elasticSearchIndex.")

    override fun run() {
        ArchiveManager(archiveFile, aggregationUnit).unArchive()
    }
}

class ArchiveManager(private val archiveFile: String, private val aggregationUnit: String) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun unArchive() {
        val archiveDir = Paths.get("/archives", aggregationUnit)
        val destDir = Paths.get("/data", aggregationUnit)

        archiveDir.toFile().mkdirs();

        val unzipped = GzipFile(archiveDir, archiveDir.resolve(archiveFile), destDir)
                .unzipArchive()

        val logsDir = Paths.get(unzipped).resolve("var").resolve("log")

        val varLogCorfu = logsDir.resolve("corfu")

        unzipLogs(varLogCorfu)
    }

    /**
     * Unzip all *log.gz files
     */
    private fun unzipLogs(logsDir: Path) {
        log.info("Unzip logs: $logsDir")

        val tgzFiles = Files.list(logsDir)
                .filter { file ->
                    file.toFile().extension == "gz"
                }
                .collect(Collectors.toList())

        tgzFiles.forEach { logFile ->
            println("Unzip: $logFile")
            try {
                val unzip = GUnzip()
                unzip.project = UnpackCommand.defaultProject
                unzip.setSrc(logFile.toFile())
                unzip.execute()
            } catch (e: Exception) {
                log.error("Can't unpack file: $logFile")
            }

            logFile.toFile().delete()
        }
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
