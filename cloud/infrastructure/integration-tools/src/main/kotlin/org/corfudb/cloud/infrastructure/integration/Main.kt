package org.corfudb.cloud.infrastructure.integration

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import org.corfudb.cloud.infrastructure.integration.kibana.KibanaDashboardCommand
import org.corfudb.cloud.infrastructure.integration.loader.LoaderCommand
import org.corfudb.cloud.infrastructure.integration.extractor.DownloadCommand
import org.corfudb.cloud.infrastructure.integration.processing.ProcessingCommand
import org.corfudb.cloud.infrastructure.integration.extractor.UnpackCommand
import org.corfudb.cloud.infrastructure.integration.server.ServerCommand

fun main(args: Array<String>) {
    IntegrationApp()
            .subcommands(
                    DownloadCommand(),
                    UnpackCommand(),
                    ProcessingCommand(),
                    LoaderCommand(),
                    KibanaDashboardCommand(),
                    ServerCommand(args)
            )
            .main(args)
}

class IntegrationApp : CliktCommand() {
    override fun run() = Unit
}
