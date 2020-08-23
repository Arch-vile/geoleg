package com.nakoradio.geoleg.model

data class Scenario(val name: String, val quests: List<Quest>)

data class Quest(val order: Int, val secret: String, val location: Coordinates, val countdown: Long, val failurePage: String, val successPage: String)

data class Coordinates(val lat: Double, val lon: Double)

data class ScenarioTable(val scenarios: List<Scenario>)
