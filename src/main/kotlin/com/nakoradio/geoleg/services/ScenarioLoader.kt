package com.nakoradio.geoleg.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.nakoradio.geoleg.model.Quest
import com.nakoradio.geoleg.model.ScenarioTable
import com.nakoradio.geoleg.model.TechnicalError
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

@Service
class ScenarioLoader(mapper: ObjectMapper) {

    lateinit var table: ScenarioTable

    init {
        var data = ClassPathResource("/data/scenario_table.json")
        table = mapper.readValue(data.inputStream, ScenarioTable::class.java)
    }

    fun questFor(scenario: String, questOrder: Int, secret: String): Quest {
        return table
            .scenarios.find { it.name == scenario }
            ?.quests
            ?.find { it.order == questOrder }
            ?.takeIf { it.secret == secret }
            ?: throw TechnicalError("No such quest for you my friend")
    }

    fun firstQuestFor(scenario: String) =
        table.scenarios.find { it.name == scenario }
            ?.quests
            ?.find { it.order == 1 }
            ?: throw TechnicalError("No such quest for you my friend")
}
