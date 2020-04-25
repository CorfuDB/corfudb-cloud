package org.corfudb.cloud.infrastructure.integration.server

import com.fasterxml.jackson.databind.SerializationFeature
import com.github.ajalt.clikt.core.CliktCommand
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing

@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    var counter = 0

    routing {
        post("/processing") {
            throw NotImplementedError()
        }

        get("/") {
            call.respond(mapOf("counter" to counter++))
        }
    }
}

class ServerCommand(private val args: Array<String>) : CliktCommand(name = "server") {

    override fun run() {
        io.ktor.server.netty.EngineMain.main(args)
    }
}
