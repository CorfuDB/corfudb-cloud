package org.corfudb.cloud.infrastructure.integration.kibana

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import org.corfudb.cloud.infrastructure.integration.IntegrationToolConfig
import org.corfudb.cloud.infrastructure.integration.kv.KvStore
import org.corfudb.cloud.infrastructure.integration.kv.RocksDbManager
import org.slf4j.LoggerFactory

class KibanaDashboardCommand : CliktCommand(name = "kibana-dashboard") {
    private val aggregationUnit: String by argument(help = "In this case An aggregation unit is an equivalent " +
            "of an elasticSearch Index.")
    private val config by option().file().required()

    override fun run() {
        val mapper = jacksonObjectMapper()
        val kvStore = KvStore(RocksDbManager.provider, mapper)

        val toolConfig = mapper.readValue(config, IntegrationToolConfig::class.java)
        KibanaDashboardManager(kvStore, aggregationUnit, toolConfig).execute()
    }
}

class KibanaDashboardManager(
        private val kvStore: KvStore,
        private val aggregationUnit: String,
        private val toolConfig: IntegrationToolConfig
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun execute() {
        try {
            val dashboardCmd = "docker run --rm " +
                    "--name ${aggregationUnit}-kibana-dashboard " +
                    "${toolConfig.kibanaToolsImage} " +
                    "bin/kibana-tools.sh " +
                    "--host=${toolConfig.kibana.host} --port=${toolConfig.kibana.port} " +
                    "--user=${toolConfig.elastic.user} --pass=${toolConfig.elastic.pass} $aggregationUnit"

            ProcessBuilder()
                    .command(listOf("/bin/sh", "-c", dashboardCmd))
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
                    .waitFor()
        } catch (ex: Exception) {
            log.error("Can't deploy kibana dashboards", ex)
        }

        log.info("Kibana dashboard deployment has finished. Aggregation unit: $aggregationUnit")
    }
}