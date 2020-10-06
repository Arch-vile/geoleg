package com.nakoradio.geoleg.services

import com.nakoradio.geoleg.model.Coordinates
import com.nakoradio.geoleg.model.LocationReading
import com.nakoradio.geoleg.model.Quest
import com.nakoradio.geoleg.model.State
import com.nakoradio.geoleg.model.TechnicalError
import com.nakoradio.geoleg.model.WebAction
import com.nakoradio.geoleg.utils.Time
import com.nakoradio.geoleg.utils.distance
import java.time.Duration
import kotlin.math.absoluteValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class Engine(
    @Value("\${location.verification.enabled:true}") var verifyLocation: Boolean,
    val timeProvider: Time,
    val loader: ScenarioLoader
) {
    var logger: Logger = LoggerFactory.getLogger(this::class.java)

    // For scenario init, we will redirect to the complete URL, so that the quest
    // will be automatically completed.
    fun initScenario(
        state: State,
        scenario: String,
        secret: String
    ): WebAction {
        logger.info("Initializing scenario: $scenario")
        val quest = loader.questFor(scenario, 0, secret)
        val newState = State(
            scenario = scenario,
            questDeadline = timeProvider.now().plusYears(10),
            questStarted = timeProvider.now(),
            currentQuest = 0,
            scenarioRestartCount =
                if (state.scenario == scenario) state.scenarioRestartCount + 1 else 0,
            userId = state.userId
        )
        return WebAction(askForLocation(questCompleteUrl(scenario, quest), quest), newState)
    }

    /**
     * Start next quest. This endpoint is called when clicking "GO" to start the next quest.
     */
    fun startQuest(
        state: State,
        scenario: String,
        questOrderToStart: Int,
        secret: String,
        locationString: String
    ): WebAction {
        val questToStart = loader.questFor(scenario, questOrderToStart, secret)
        val currentQuest = loader.questFor(scenario, questOrderToStart - 1)

        assertEqual(state.scenario, scenario, "Bad cookie scenario")

        // Trying to restart the quest
        if (questToStart.order == state.currentQuest) {
            var countDownView = CountdownViewModel(
                state.questStarted.toEpochSecond(),
                state.questDeadline?.toEpochSecond(),
                questToStart.fictionalCountdown,
                questToStart.location!!.lat,
                questToStart.location!!.lon
            )
            return WebAction(countDownView, state)
        }

        assertEqual(state.currentQuest, questOrderToStart - 1, "Bad cookie quest")

        currentQuest.location?.let {
            val locationReading = LocationReading.fromString(locationString)
            checkIsFresh(locationReading)
            assertProximity(it, locationReading.toCoordinates())
        }

        var newState = state.copy(
            questStarted = timeProvider.now(),
            currentQuest = questOrderToStart,
            questDeadline = questToStart.countdown?.let { timeProvider.now().plusSeconds(it) }
        )

        var expiresAt =
            questToStart.countdown?.let { timeProvider.now().plusSeconds(it).toEpochSecond() }
        var now = timeProvider.now().toEpochSecond()
        var countDownView = CountdownViewModel(now, expiresAt, questToStart.fictionalCountdown, questToStart.location!!.lat, questToStart.location!!.lon)

        return WebAction(countDownView, newState)
    }

    // This just does the redirection to location granting, which redirects back
    // to the other complete endpoint with location.
    fun initComplete(
        scenario: String,
        questToComplete: Int,
        secret: String
    ): ViewModel {
        val quest = loader.questFor(scenario, questToComplete, secret)
        return askForLocation(
            questCompleteUrl(scenario, quest), quest
        )
    }

    fun complete(
        state: State,
        scenario: String,
        questOrder: Int,
        secret: String,
        locationString: String
    ): ViewModel {
        val quest = loader.questFor(scenario, questOrder, secret)
        val locationReading = LocationReading.fromString(locationString)
        checkIsFresh(locationReading)

        val nextPage = checkQuestCompletion(scenario, quest, locationReading.toCoordinates(), state)
        if (loader.isLastQuest(scenario, questOrder)) {
            return ScenarioEndViewModel(nextPage)
        } else {
            val nextQuest = loader.questFor(scenario, questOrder + 1)
            return QuestEndViewModel(nextPage, nextQuest, quest)
        }
    }

    private fun checkQuestCompletion(scenario: String, quest: Quest, location: Coordinates, state: State): String {
        assertEqual(scenario, state.scenario, "scenario completion")
        assertEqual(quest.order, state.currentQuest, "quest matching")

        quest.location?.let { assertProximity(it, location) }

        return if (quest.countdown != null && timeProvider.now().isAfter(state.questDeadline)) {
            logger.info("Quest failed due to time running out")
            quest.failurePage
        } else {
            logger.info("Quest success!")
            quest.successPage
        }
    }

    private fun assertProximity(target: Coordinates, location: Coordinates) {
        if (!verifyLocation) {
            return
        }

        var distance = distance(target, location)
        if (distance > 100) {
            logger.error("quest location [$target] location [$location] distance [$distance]")
            throw TechnicalError("Bad gps accuracy")
        }
    }

    private fun assertEqual(val1: Any, val2: Any, context: String) {
        if (val1 != val2) {
            throw TechnicalError("Not good: $context")
        }
    }

    private fun checkIsFresh(location: LocationReading) {
        // We should receive the location right after granted, if it takes longer, suspect something funny
        if (Duration.between(timeProvider.now(), location.createdAt).seconds.absoluteValue > 30) {
            throw TechnicalError("Location not fresh")
        }
    }

    private fun questCompleteUrl(scenario: String, quest: Quest): String {
        return "/engine/complete/$scenario/${quest.order}/${quest.secret}"
    }

    private fun askForLocation(questUrl: String, quest: Quest) =
        LocationReadingViewModel(questUrl, quest?.location?.lat, quest?.location?.lon)

    fun toggleLocationVerification(): Boolean {
        verifyLocation = !verifyLocation
        return verifyLocation
    }
}
