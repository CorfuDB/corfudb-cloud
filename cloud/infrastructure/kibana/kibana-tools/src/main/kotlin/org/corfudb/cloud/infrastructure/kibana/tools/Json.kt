package org.corfudb.cloud.infrastructure.kibana.tools

import com.fasterxml.jackson.databind.JsonNode

data class KibanaDashboard(val version: String, val objects: List<KibanaObject>)

data class KibanaObject(
        var id: String,
        val type: String,
        val updated_at: String,
        var version: String,
        val attributes: JsonNode,
        val references: List<Reference>,
        val migrationVersion: JsonNode
)

data class IndexPatternCreation(
        val attributes: IndexPatternAttributes,
        val references: List<Reference>,
        val migrationVersion: JsonNode
)

data class IndexPatternAttributes(var title: String, val timeFieldName: String)

data class Reference(var id: String, val name: String, val type: String)

data class KibanaSpace(val name: String){
    val id: String = name.toLowerCase()
}
