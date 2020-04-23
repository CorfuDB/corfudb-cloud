package org.corfudb.cloud.infrastructure.integration

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import org.corfudb.cloud.infrastructure.integration.loader.LoaderCommand
import org.corfudb.cloud.infrastructure.integration.processing.DownloadCommand
import org.corfudb.cloud.infrastructure.integration.processing.ProcessingCommand
import org.corfudb.cloud.infrastructure.integration.processing.UnpackCommand

fun main(args: Array<String>) {
    IntegrationApp()
            .subcommands(
                    DownloadCommand(),
                    UnpackCommand(),
                    ProcessingCommand(),
                    LoaderCommand()
            )
            .main(args)
}

class IntegrationApp : CliktCommand() {
    override fun run() = Unit
}
