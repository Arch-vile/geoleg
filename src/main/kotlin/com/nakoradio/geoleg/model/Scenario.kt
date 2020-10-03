package com.nakoradio.geoleg.model

data class Scenario(val name: String, val quests: List<Quest>)

data class Quest(
    val order: Int,
    val secret: String,

    // Quest end location, can be omitted to indicate no location check.
    val location: Coordinates?,

    // Time in seconds to complete the quest. Can be omitted to indicate no time limit.
    val countdown: Long?,

    // Fictional time in seconds to complete the quest, can be omitted.
    val fictionalCountdown: Long?,

    // Fictional time (as static string) to be shown
    val fictionalClock: String,

    val failurePage: String,
    val successPage: String
)

data class Coordinates(val lat: Double, val lon: Double)

data class ScenarioTable(val scenarios: List<Scenario>)
