package org.corfudb.cloud.infrastructure.integration.server

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.ajalt.clikt.core.CliktCommand
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlinx.coroutines.async
import org.corfudb.cloud.infrastructure.integration.ArchiveConfig
import org.corfudb.cloud.infrastructure.integration.IntegrationToolConfig
import org.corfudb.cloud.infrastructure.integration.kv.KvStore
import org.corfudb.cloud.infrastructure.integration.kv.ProcessingMessage
import org.corfudb.cloud.infrastructure.integration.kv.RocksDbManager
import org.corfudb.cloud.infrastructure.integration.processing.ProcessingManager
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

@KtorExperimentalAPI
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    var counter = 0

    val mapper = jacksonObjectMapper()
    val mainConfig = mapper.readValue(File("config.json"), IntegrationToolConfig::class.java)
    val kvStore = KvStore(RocksDbManager.provider, mapper);

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    install(StatusPages) {
        exception<Exception> { exception ->
            val sw = StringWriter()
            exception.printStackTrace(PrintWriter(sw))
            val exceptionAsString = sw.toString()

            call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    mapOf("status" to "error", "error" to exceptionAsString)
            )
        }
    }

    environment.monitor.subscribe(ApplicationStopped) {
        RocksDbManager.provider.db.close()
    }

    routing {
        static("/static") {
            resources("static")
        }

        get("/") {
            environment.log.info("index page access")
            call.respond(mapOf("counter" to counter++))
        }

        get("/processing/{aggregation_unit}") {
            val aggregationUnit = call.parameters["aggregation_unit"]!!
            environment.log.info("processing page request: $aggregationUnit")

            val logs = kvStore.findAll(aggregationUnit)
            call.respond(mapOf("result" to logs))
        }

        post("/processing") {
            environment.log.info("Processing handler")
            val request = call.receive<ProcessingRequest>()

            var logs = kvStore.findAll(request.aggregationUnit)
            if (logs.isNotEmpty()) {
                call.respond(mapOf("result" to logs))
            } else {
                val config = IntegrationToolConfig(
                        kibana = mainConfig.kibana,
                        logstash = mainConfig.logstash,
                        elastic = mainConfig.elastic,
                        filebeatImage = mainConfig.filebeatImage,
                        kibanaToolsImage = mainConfig.kibanaToolsImage,
                        loggers = mainConfig.loggers,
                        logDirectories = mainConfig.logDirectories,
                        archives = request.archives,
                        transform = mainConfig.transform
                )

                kvStore.put(ProcessingMessage.new(request.aggregationUnit, "Init processing"))

                async {
                    try {
                        ProcessingManager(kvStore, request.aggregationUnit, config).execute()
                    } catch (ex: Exception) {
                        val sw = StringWriter()
                        val pw = PrintWriter(sw)
                        ex.printStackTrace(pw)
                        val sStackTrace = sw.toString()
                        val errStr = "Processing error: $sStackTrace"
                        kvStore.put(ProcessingMessage.new(request.aggregationUnit, errStr))
                    }
                }

                logs = kvStore.findAll(request.aggregationUnit)
                call.respond(mapOf("result" to logs))
            }
        }
    }
}

class ServerCommand(private val args: Array<String>) : CliktCommand(name = "server") {

    override fun run() {
        io.ktor.server.netty.EngineMain.main(args)
    }
}

data class ProcessingRequest(val aggregationUnit: String, val archives: List<ArchiveConfig>)
