package com.nakoradio.geoleg.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.nakoradio.geoleg.model.ScenarioTable
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

@Service
class ScenarioLoader(val mapper: ObjectMapper) {

    var table: ScenarioTable? = null

    init {
        load()
    }

    fun load(): ScenarioTable {
        if (table == null) {
            var data = ClassPathResource("/data/scenario_table.json")
            table = mapper.readValue(data.inputStream, ScenarioTable::class.java)
        }

        return table!!
    }
}
