package org.corfudb.cloud.infrastructure.kibana.tools

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
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

class Main {

    fun main(args: Array<String>) {
        println("Start deploying! Parameters: ${args.joinToString()}")
        Processing().main(args)
    }
}

class Processing : CliktCommand() {
    private val host: String by option(help = "host").required()
    private val port: Int by option(help = "port").int().required()

    private val user: String by option(help = "user").required()
    private val pass: String by option(help = "pass").required()

    private val aggregationUnit: String by argument(help = "aggregationUnit")

    override fun run() {
        val dashboards = dashboards {
            aggregationUnit = this@Processing.aggregationUnit
        }

        HttpClient(CIO) {
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

                client.post<String>("/api/spaces/space") {
                    body = json.write(space)
                }

                /**
                 * Create an index pattern
                 * https://www.elastic.co/guide/en/kibana/6.6/saved-objects-api-get.html
                 */
                client.post<String>("/s/${space.name}/api/saved_objects/index-pattern/${aggregationUnit}") {
                    body = json.write(dashboards.indexPattern())
                }

                dashboards.allDashBoards().forEach { dashboard ->
                    /**
                     * Creating dashboards
                     * https://ktor.io/clients/http-client/quick-start/requests.html
                     */
                    client.post<String>("/s/${space.name}/api/kibana/dashboards/import?exclude=index-pattern") {
                        body = json.write(dashboard)
                    }
                }
            }
        }
    }
}
