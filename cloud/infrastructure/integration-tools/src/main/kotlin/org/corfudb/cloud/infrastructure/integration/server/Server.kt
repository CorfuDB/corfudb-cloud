package org.corfudb.cloud.infrastructure.integration.server

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.ajalt.clikt.core.CliktCommand
import io.ktor.application.Application
import io.ktor.application.ApplicationStopped
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import org.corfudb.cloud.infrastructure.integration.ArchiveConfig
import org.corfudb.cloud.infrastructure.integration.IntegrationToolConfig
import org.corfudb.cloud.infrastructure.integration.processing.KvStore
import org.corfudb.cloud.infrastructure.integration.processing.ProcessingManager
import org.corfudb.cloud.infrastructure.integration.processing.RocksDbProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

@KtorExperimentalAPI
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
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
                    HttpStatusCode.OK,
                    mapOf("status" to "error", "error" to exceptionAsString)
            )
        }
    }

    environment.monitor.subscribe(ApplicationStopped){
        RocksDbProvider.db.close();
    }

    var counter = 0

    val mapper = jacksonObjectMapper()
    val mainConfig = mapper.readValue(File("config.json"), IntegrationToolConfig::class.java)
    val kvStore = KvStore(RocksDbProvider.db, mapper);

    routing {
        static("/static") {
            resources("static")
        }

        get("/") {
            call.respond(mapOf("counter" to counter++))
        }

        post("/processing") {

            val request = call.receive<ProcessingRequest>()

            val config = IntegrationToolConfig(
                    kibana = mainConfig.kibana,
                    logstash = mainConfig.logstash,
                    elastic = mainConfig.elastic,
                    filebeatImage = mainConfig.filebeatImage,
                    kibanaToolsImage = mainConfig.kibanaToolsImage,
                    loggers = mainConfig.loggers,
                    logDirectories = mainConfig.logDirectories,
                    archives = request.archives
            );
            ProcessingManager(kvStore, request.aggregationUnit, config).execute();

            call.respond(mapOf("result" to request))
        }
    }
}

class ServerCommand(private val args: Array<String>) : CliktCommand(name = "server") {

    override fun run() {
        io.ktor.server.netty.EngineMain.main(args)
    }
}

data class ProcessingRequest(val aggregationUnit: String, val archives: List<ArchiveConfig>)
