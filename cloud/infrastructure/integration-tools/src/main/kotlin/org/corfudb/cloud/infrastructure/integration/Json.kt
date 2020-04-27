package org.corfudb.cloud.infrastructure.integration

data class IntegrationToolConfig(
        val kibana: KibanaConfig,
        val logstash: LogstashConfig,
        val elastic: ElasticConfig,
        val filebeatImage: String,
        val kibanaToolsImage: String,
        val archives: List<ArchiveConfig>
)

data class KibanaConfig(val host: String, val port: Int)
data class LogstashConfig(val host: String, val port: Int) {
    val endpoint = "$host:$port"
}

data class ElasticConfig(val host: String, val port: Int, val user: String, val pass: String)

data class ArchiveConfig(
        val name: String,
        val url: String,
        val logDirectories: List<String>,
        val loggers: List<String>
)
