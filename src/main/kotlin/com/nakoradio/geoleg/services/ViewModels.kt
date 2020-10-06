package com.nakoradio.geoleg.services

import com.nakoradio.geoleg.model.Quest

open class ViewModel(open val view: String)

data class CountdownViewModel(
    val now: Long,
    val expiresAt: Long?,
    val fictionalCountdown: Long?,
    val lat: Double,
    val lon: Double
) : ViewModel("countdown")

data class QuestEndViewModel(
    override val view: String,
    val nextQuest: Quest,
    val currentQuest: Quest
) : ViewModel(view)

data class ScenarioEndViewModel(
    override val view: String
) : ViewModel(view)

data class LocationReadingViewModel(
    val action: String,
    val lat: Double?,
    val lon: Double?
) : ViewModel("checkLocation")
