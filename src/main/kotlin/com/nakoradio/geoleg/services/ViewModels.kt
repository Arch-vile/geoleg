package com.nakoradio.geoleg.services

import com.nakoradio.geoleg.controllers.HallOfFameController
import com.nakoradio.geoleg.model.LocalizedMessage
import com.nakoradio.geoleg.model.Quest
import com.nakoradio.geoleg.model.State

open class ViewModel(open val view: String)

data class OnlyView(override val view: String) : ViewModel(view)

data class CountdownViewModel(
    val now: Long,
    val expiresAt: Long?,
    val fictionalCountdown: Long?,
    val lat: Double?,
    val lon: Double?,
    val message: LocalizedMessage? = null
) : ViewModel("countdown")

data class QuestEndViewModel(
    override val view: String,
    val nextQuest: Quest,
    val currentQuest: Quest
) : ViewModel(view)

data class QuestFailedViewModel(
    override val view: String,
    val state: State
) : ViewModel(view)

data class ScenarioEndViewModel(
    override val view: String
) : ViewModel(view)

data class LocationReadingViewModel(
    val action: String,
    val lat: Double?,
    val lon: Double?
) : ViewModel("checkLocation")

data class HallOfFameFormViewModel(
    val yourResult: String,
    val results: List<HallOfFameController.ResultForView>
) : ViewModel("hallOfFameForm")

data class HallOfFameListViewModel(
    val results: List<HallOfFameController.ResultForView>
) : ViewModel("hallOfFameList")
