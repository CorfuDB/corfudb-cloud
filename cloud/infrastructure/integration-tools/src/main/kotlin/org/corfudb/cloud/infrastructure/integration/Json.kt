package org.corfudb.cloud.infrastructure.integration

import org.apache.commons.io.FilenameUtils
import java.nio.file.Path
import java.nio.file.Paths

data class IntegrationToolConfig(
        val kibana: KibanaConfig,
        val logstash: LogstashConfig,
        val elastic: ElasticConfig,
        val filebeatImage: String,
        val kibanaToolsImage: String,
        val logDirectories: List<String>,
        val loggers: List<String>,
        val transform: List<TransformConfig>,
        val archives: List<ArchiveConfig>
)

data class KibanaConfig(val host: String, val port: Int)
data class LogstashConfig(val host: String, val port: Int) {
    val endpoint = "$host:$port"
}

data class ElasticConfig(val host: String, val port: Int, val user: String, val pass: String)

data class ArchiveConfig(val name: String, val url: String) {

    private fun extension(): String = FilenameUtils.getExtension(url)

    fun file(): Path = Paths.get("${name}.${extension()}")
}

/**
 * "transform": [{
 *    "path": "var/log/corfu",
 *    "commands": "sed -i '/log write/d' corfu.*.log"
 * }]
 */
data class TransformConfig(val path: String, val commands: List<String>)
