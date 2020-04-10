package org.corfudb.cloud.infrastructure.kibana.tools

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.commons.lang3.RandomStringUtils
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

data class DashboardConfig(
        var aggregationUnit: String = "corfu",
        var configFiles: List<Path> = listOf(),

        val indexPatternConfigFile: Path = Paths.get("index-pattern.json")
)

class Dashboards(private val config: DashboardConfig) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val mapper = jacksonObjectMapper()

    fun allDashBoards(): List<KibanaDashboard> {
        return config.configFiles.map { configFile ->
            log.info("Loading a dashboard config: {}", configFile)
            loadAndConfigure(configFile)
        }
    }

    private fun loadAndConfigure(configFile: Path): KibanaDashboard {
        val dashboard = readDashboard(configFile)
        return configure(dashboard, config)
    }

    private fun configure(dashboard: KibanaDashboard, cfg: DashboardConfig): KibanaDashboard {
        val indexPatternDashboard = dashboard.objects.last()
        indexPatternDashboard.id = cfg.aggregationUnit
        indexPatternDashboard.version = RandomStringUtils.randomAlphanumeric(8)
        val indexPatternAttr = indexPatternDashboard.attributes as ObjectNode
        indexPatternAttr.put("title", cfg.aggregationUnit)

        for ((i, visualization) in dashboard.objects.withIndex()) {
            if (i == 0 || i == dashboard.objects.lastIndex) {
                continue
            }

            visualization.id = UUID.randomUUID().toString()
            visualization.version = RandomStringUtils.randomAlphanumeric(8)
            visualization.references[0].id = indexPatternDashboard.id
        }

        //dashboard settings
        val dashboardVisualization = dashboard.objects[0]
        dashboardVisualization.id = UUID.randomUUID().toString()
        dashboardVisualization.version = RandomStringUtils.randomAlphanumeric(8)
        //specify index pattern id for all visualizations
        for ((i, reference) in dashboardVisualization.references.withIndex()) {
            reference.id = dashboard.objects[i + 1].id
        }

        return dashboard
    }

    private fun readDashboard(jsonFile: Path): KibanaDashboard {
        return mapper.readValue(jsonFile.toFile(), KibanaDashboard::class.java)
    }

    fun indexPattern(): IndexPatternCreation {
        val indexPattern = mapper.readValue(config.indexPatternConfigFile.toFile(), IndexPatternCreation::class.java)
        indexPattern.attributes.title = config.aggregationUnit

        return indexPattern
    }
}

fun dashboards(block: DashboardConfig.() -> Unit) = Dashboards(DashboardConfig().apply(block))

