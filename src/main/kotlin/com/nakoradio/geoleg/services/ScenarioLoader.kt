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

    fun findScenario(scenario: String) =
        table
            .scenarios.find { it.name == scenario }
            ?: throw TechnicalError("No such scenario for you my friend")

    fun questFor(scenario: String, questOrder: Int): Quest {
        return findScenario(scenario)
            .quests.find { it.order == questOrder }
            ?: throw TechnicalError("No such quest for you my friend")
    }

    fun questFor(scenario: String, questOrder: Int, secret: String): Quest {
        return questFor(scenario, questOrder)
            .takeIf { it.secret == secret }
            ?: throw TechnicalError("No such quest secret for you my friend")
    }

    fun isLastQuest(scenario: String, questOrder: Int) =
        questOrder + 1 >= table.scenarios.find { it.name == scenario }
            ?.quests!!.size
}
