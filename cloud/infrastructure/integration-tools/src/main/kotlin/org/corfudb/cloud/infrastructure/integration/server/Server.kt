package org.corfudb.cloud.infrastructure.integration.server

import com.fasterxml.jackson.databind.SerializationFeature
import com.github.ajalt.clikt.core.CliktCommand
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.default
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import org.corfudb.cloud.infrastructure.integration.IntegrationToolConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.stream.Collectors


@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    install(StatusPages) {
        exception<Exception> { exception ->
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "error", "error" to (exception.message ?: "")))
        }
    }

    var counter = 0

    routing {
        static("/static") {
            resources("static")
        }

        post("/processing/{aggregation_unit}") {
            val aggregationUnit = call.parameters["aggregation_unit"]
            val post = call.receive<IntegrationToolConfig>()

            //save json config
            //run python python integration-tool-runner.py agg_unit
            val process = ProcessBuilder()
                    .command(listOf("sh", "-c", "ls -la"))
                    .start()
            process.waitFor()
            val output = BufferedReader(InputStreamReader(process.inputStream))
                    .lines()
                    .collect(Collectors.joining())

            call.respond(mapOf("result" to post))
        }
    }
}

class ServerCommand(private val args: Array<String>) : CliktCommand(name = "server") {

    override fun run() {
        io.ktor.server.netty.EngineMain.main(args)
    }
}
