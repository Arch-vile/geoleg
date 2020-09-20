package com.nakoradio.geoleg.services

import com.nakoradio.geoleg.model.Coordinates
import com.nakoradio.geoleg.model.LocationReading
import com.nakoradio.geoleg.model.MissingCookieError
import com.nakoradio.geoleg.model.Quest
import com.nakoradio.geoleg.model.State
import com.nakoradio.geoleg.model.TechnicalError
import com.nakoradio.geoleg.model.WebAction
import com.nakoradio.geoleg.utils.distance
import com.nakoradio.geoleg.utils.now
import java.time.Duration
import kotlin.math.absoluteValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class Engine(
    @Value("\${location.verification.enabled:true}") var verifyLocation: Boolean,
    val cookieManager: CookieManager,
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
        val newState = state.copy(
                scenario = scenario,
                deadline = now().plusYears(10),
                started = now(),
                currentQuest = 0,
                scenarioRestartCount = state.scenarioRestartCount+1
                )
        return WebAction(askForLocation(questCompleteUrl(scenario, quest)),newState)
    }

    /**
     * Start next quest. This endpoint is called when clicking "GO" to start the next quest.
     */
    fun startQuest(
        state: State,
        scenario: String,
        questToStart: Int,
        secret: String,
        locationString: String
    ): WebAction {
        val quest = loader.questFor(scenario, questToStart, secret)

        val locationReading = LocationReading.fromString(locationString)
        checkIsFresh(locationReading)
        assertProximity(quest, locationReading.toCoordinates())

        assertEqual(state.scenario, scenario, "Bad cookie scenario")
        assertEqual(state.currentQuest, questToStart - 1, "Bad cookie quest")

        var updatedCookie = state.copy(
            started = now(),
            currentQuest = questToStart,
            deadline = now().plusSeconds(quest.countdown)
        )

        var expiresAt = now().plusSeconds(quest.countdown).toEpochSecond()
        var now = now().toEpochSecond()
        var countdownPageUrl = countdownPage(expiresAt, now, quest.fictionalCountdown, quest.location)

        return WebAction(countdownPageUrl, updatedCookie)
    }

    // This just does the redirection to location granting, which redirects back
    // to the other complete endpoint with location.
    fun initComplete(
        scenario: String,
        questToComplete: Int,
        secret: String
    ): String {
        val quest = loader.questFor(scenario, questToComplete, secret)

        return askForLocation(
            questCompleteUrl(scenario, quest)
        )
    }

    fun complete(
        state: State,
        scenario: String,
        questOrder: Int,
        secret: String,
        locationString: String
    ): String {
        val quest = loader.questFor(scenario, questOrder, secret)
        val locationReading = LocationReading.fromString(locationString)
        checkIsFresh(locationReading)

        val nextPage = checkQuestCompletion(scenario, quest, locationReading.toCoordinates(), state)
        return nextPage
    }

    private fun checkQuestCompletion(scenario: String, quest: Quest, location: Coordinates, state: State): String {
        assertEqual(scenario, state.scenario, "scenario completion")
        assertEqual(quest.order, state.currentQuest, "quest matching")

        if (quest.shouldVerifyLocation) {
            assertProximity(quest, location)
        }

        return if (quest.shouldVerifyCountdown && now().isAfter(state.deadline)) {
            logger.info("Quest failed due to time running out")
            quest.failurePage
        } else {
            logger.info("Quest success!")
            quest.successPage
        }
    }

    private fun assertProximity(quest: Quest, location: Coordinates) {
        if (!verifyLocation) {
            return
        }

        var distance = distance(quest.location, location)
        if (distance > 100) {
            logger.error("quest location [${quest.location}] location [$location] distance [$distance]")
            throw TechnicalError("Bad gps accuracy")
        }
    }

    private fun assertEqual(val1: Any, val2: Any, context: String) {
        if (val1 != val2) {
            throw TechnicalError("Not good $context")
        }
    }

    private fun checkIsFresh(location: LocationReading) {
        // We should receive the location right after granted, if it takes longer, suspect something funny
        if (Duration.between(now(), location.createdAt).seconds.absoluteValue > 10) {
            throw TechnicalError("Something funny with the location")
        }
    }

    private fun questCompleteUrl(scenario: String, quest: Quest): String {
        return "/engine/complete/$scenario/${quest.order}/${quest.secret}"
    }

    private fun askForLocation(questUrl: String) =
        "/checkLocation.html?target=$questUrl"

    private fun countdownPage(expiresAt: Long, now: Long, fictionalCountdown: Long, location: Coordinates) =
        "/countdown.html?expiresAt=$expiresAt&now=$now&countdown=$fictionalCountdown&lat=${location.lat}&lon=${location.lon}"

    fun toggleLocationVerification(): Boolean {
        verifyLocation = !verifyLocation
        return verifyLocation
    }
}