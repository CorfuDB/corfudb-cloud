package org.corfudb.cloud.infrastructure.kibana.tools

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.sources.ExperimentalValueSourceApi
import com.github.ajalt.clikt.sources.PropertiesValueSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.basic
import io.ktor.client.features.defaultRequest
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.Json
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.host
import io.ktor.client.request.port
import io.ktor.client.request.post
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

@ExperimentalValueSourceApi
fun main(args: Array<String>) {
    Processing().main(args)
}

@ExperimentalValueSourceApi
class Processing : CliktCommand() {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        context {
            val configFile = PropertiesValueSource.from("kibana-tools.config")
            valueSources(configFile)
        }
    }

    private val host: String by option(help = "host").required()
    private val port: Int by option(help = "port").int().required()

    private val user: String by option(help = "user").required()
    private val pass: String by option(help = "pass").required()

    private val dashboardDir: List<Path> by option(help = "Dashboard configuration directory")
            .path(mustExist = true, canBeFile = false)
            .multiple(default = listOf(Paths.get("dashboard"), Paths.get("corfu-dashboard")))

    private val aggregationUnit: String by argument(help = "aggregationUnit")

    override fun run() {
        val dashboards = dashboards {
            log.info("Configuring dashboards from directory: $dashboardDir")

            aggregationUnit = this@Processing.aggregationUnit
            //read dashboards from `dashboard` dir

            configFiles = dashboardDir.flatMap { dir ->
                Files.list(dir).collect(Collectors.toList())
            }
        }

        HttpClient(CIO) {
            log.info("Configuring restful client")

            Logging {
                level = LogLevel.INFO
            }

            defaultRequest {
                host = this@Processing.host
                port = this@Processing.port

                headers["kbn-xsrf"] = "true"
            }

            Auth {
                basic {
                    username = user
                    password = pass
                    sendWithoutRequest = true
                }
            }

            Json {
                serializer = JacksonSerializer()
            }
        }.use { client ->
            runBlocking {
                val json = JacksonSerializer()

                /**
                 * Create a space
                 * https://www.elastic.co/guide/en/kibana/master/spaces-api-post.html
                 */
                val space = KibanaSpace(aggregationUnit, aggregationUnit)

                log.info("Creating a kibana space: {}", space)
                client.post<String>("/api/spaces/space") {
                    body = json.write(space)
                }

                /**
                 * Create an index pattern
                 * https://www.elastic.co/guide/en/kibana/6.6/saved-objects-api-get.html
                 */
                log.info("Creating an index pattern: {}", aggregationUnit)
                client.post<String>("/s/${space.name}/api/saved_objects/index-pattern/${aggregationUnit}") {
                    body = json.write(dashboards.indexPattern())
                }

                dashboards.allDashBoards().forEach { dashboard ->
                    /**
                     * Creating dashboards
                     * https://ktor.io/clients/http-client/quick-start/requests.html
                     *
                     * Get the dashboard from a kibana server:
                     * curl --user ${user}:${pass} -X GET "localhost:5601/s/index_104/api/kibana/dashboards/export?dashboard=${dashboard_id}" -H 'kbn-xsrf: true' > my-dashboard.json
                     */
                    log.info("Creating a dashboard: {}", dashboard)
                    client.post<String>("/s/${space.name}/api/kibana/dashboards/import?exclude=index-pattern") {
                        body = json.write(dashboard)
                    }
                }
            }
        }
    }
}
