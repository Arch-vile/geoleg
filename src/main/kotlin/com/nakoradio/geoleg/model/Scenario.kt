package com.nakoradio.geoleg.model

data class Scenario(val name: String, val quests: List<Quest>)

data class Quest(
    val order: Int,
    val secret: String,
    val location: Coordinates,

    // Time in seconds to complete the quest
    val countdown: Long,

    // Fictional time in seconds to complete the quest
    val fictionalCountdown: Long,

    val failurePage: String,
    val successPage: String
)

data class Coordinates(val lat: Double, val lon: Double)

data class ScenarioTable(val scenarios: List<Scenario>)
