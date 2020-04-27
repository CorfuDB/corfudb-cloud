package org.corfudb.cloud.infrastructure.integration.loader

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import org.corfudb.cloud.infrastructure.integration.IntegrationToolConfig
import org.slf4j.LoggerFactory
import java.lang.ProcessBuilder.Redirect

class LoaderCommand : CliktCommand(name = "loading") {
    private val aggregationUnit: String by argument(help = "In this case An aggregation unit is an equivalent " +
            "of an elasticSearch Index.")
    private val config by option().file().required()

    override fun run() {
        val mapper = jacksonObjectMapper()
        val toolConfig = mapper.readValue(config, IntegrationToolConfig::class.java)
        LoaderManager(aggregationUnit, toolConfig).execute()
    }
}

class LoaderManager(private val aggregationUnit: String, private val toolConfig: IntegrationToolConfig) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun execute() {
        toolConfig.archives.forEach { archive ->
            log.info("Start loading for: ${archive.name}")

            try {
                val filebeatCmd = "docker run --rm " +
                        "--name ${aggregationUnit}-${archive.name} " +
                        "-v ${aggregationUnit}:/data " +
                        "${toolConfig.filebeatImage} " +
                        "filebeat -e --strict.perms=false " +
                        "-E fields.server=${archive.name} " +
                        "-E fields.aggregation_unit=${aggregationUnit} " +
                        "-E BASE_DIR=/data/${aggregationUnit}/${archive.name} " +
                        "-E 'fields.loggers=${archive.loggers.joinToString()}' " +
                        "-E output.logstash.hosts=${listOf(toolConfig.logstash.endpoint)} " +
                        "-E output.logstash.index=$aggregationUnit " +
                        "--once run"

                ProcessBuilder()
                        .command(listOf("/bin/sh", "-c", filebeatCmd))
                        .redirectOutput(Redirect.INHERIT)
                        .redirectError(Redirect.INHERIT)
                        .start()
                        .waitFor()
            } catch (ex: Exception) {
                log.error("Can't load data into elastic", ex)
            }

            log.info("Filebeat loader has finished: " + archive.name)
        }
    }
}
