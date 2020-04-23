package org.corfudb.cloud.infrastructure.integration.loader

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import org.slf4j.LoggerFactory

class LoaderCommand : CliktCommand(name = "loading") {
    private val log = LoggerFactory.getLogger(javaClass)

    private val aggregationUnit: String by argument(help = "In this case An aggregation unit is an equivalent " +
            "of an elasticSearch Index.")

    override fun run() {
        throw NotImplementedError("not implemented")
    }
}
