package org.corfudb.cloud.infrastructure.integration.server

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import io.ktor.application.Application
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
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import org.corfudb.cloud.infrastructure.integration.ArchiveConfig
import org.corfudb.cloud.infrastructure.integration.IntegrationToolConfig
import java.io.*
import java.util.stream.Collectors

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
                    HttpStatusCode.ServiceUnavailable,
                    mapOf("status" to "error", "error" to exceptionAsString)
            )
        }
    }

    var counter = 0

    routing {
        static("/static") {
            resources("static")
        }

        post("/processing/{aggregation_unit}") {
            val aggregationUnit = call.parameters["aggregation_unit"]
            val post = call.receive<ProcessingRequest>()

            //save json config
            //run python python integration-tool-runner.py agg_unit
            val process = ProcessBuilder()
                    .command(listOf("sh", "-c", "ls -la"))
                    .start()
            process.waitFor()

            //save logs in a log file and send it if the task is still going.
            val output = BufferedReader(InputStreamReader(process.inputStream))
                    .lines()
                    .collect(Collectors.joining())

            call.respond(mapOf("result" to post))
        }

        get("/yay") {
            val mapper = jacksonObjectMapper()
            val toolsConfig = mapper.readValue(File("config.json"), IntegrationToolConfig::class.java)
            call.respondText(mapper.writeValueAsString(toolsConfig));
        }
    }
}

class ServerCommand(private val args: Array<String>) : CliktCommand(name = "server") {

    override fun run() {
        io.ktor.server.netty.EngineMain.main(args)
    }
}

data class ProcessingRequest(val archives: List<ArchiveConfig>) {
}