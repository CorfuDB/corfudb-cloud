package org.corfudb.cloud.infrastructure.integration

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import org.corfudb.cloud.infrastructure.integration.loader.LoaderCommand
import org.corfudb.cloud.infrastructure.integration.processing.DownloadCommand
import org.corfudb.cloud.infrastructure.integration.processing.ProcessingCommand
import org.corfudb.cloud.infrastructure.integration.processing.UnpackCommand
import org.corfudb.cloud.infrastructure.integration.server.ServerCommand

fun main(args: Array<String>) {
    IntegrationApp()
            .subcommands(
                    DownloadCommand(),
                    UnpackCommand(),
                    ProcessingCommand(),
                    LoaderCommand(),
                    ServerCommand(args)
            )
            .main(args)
}

class IntegrationApp : CliktCommand() {
    override fun run() = Unit
}
