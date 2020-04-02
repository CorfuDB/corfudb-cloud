package org.corfudb.cloud.infrastructure.kibana.tools

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.commons.lang3.RandomStringUtils
import java.util.UUID

data class DashboardConfig(
        var aggregationUnit: String = "corfu",

        val basicJsonFile: String = "basic.json",
        val corfuJsonFile: String = "corfu.json",
        val checkpointJsonFile: String = "checkpoint.json",

        val indexPatternJsonFile: String = "index-pattern.json"
)

class Dashboards(val config: DashboardConfig) {
    private val mapper = jacksonObjectMapper()

    fun allDashBoards(): List<KibanaDashboard> {
        return listOf(basic(), corfu(), checkpoint())
    }

    fun basic(): KibanaDashboard {
        val dashboard = readDashboard(config.basicJsonFile)
        return configure(dashboard, config)
    }

    fun corfu(): KibanaDashboard {
        val dashboard = readDashboard(config.corfuJsonFile)
        return configure(dashboard, config)
    }

    fun checkpoint(): KibanaDashboard {
        val dashboard = readDashboard(config.checkpointJsonFile)
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

    private fun readDashboard(jsonFile: String): KibanaDashboard {
        val file = ClassLoader.getSystemResource(jsonFile)
        return mapper.readValue(file, KibanaDashboard::class.java)
    }

    fun indexPattern(): IndexPatternCreation {
        val file = ClassLoader.getSystemResource(config.indexPatternJsonFile)
        val indexPattern = mapper.readValue(file, IndexPatternCreation::class.java)
        indexPattern.attributes.title = config.aggregationUnit

        return indexPattern
    }
}

fun dashboards(block: DashboardConfig.() -> Unit) = Dashboards(DashboardConfig().apply(block))

