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
import java.util.UUID
import kotlin.math.absoluteValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.math.log

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
        state: State?,
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
                if (state?.scenario == scenario) state.scenarioRestartCount + 1 else 0,
            userId = state?.userId ?: UUID.randomUUID()
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
        assertEqual(state.scenario, scenario, "Bad cookie scenario")

        // Special handling when the intro quest is active?
        if (state.currentQuest == 0 && questOrderToStart != 1) {
            val questToComplete = loader.questFor(scenario, 0)
            return redirectToQuestCompleteThroughLocationReading(scenario, questToComplete, state)
        }

        val questToStart = loader.questFor(scenario, questOrderToStart, secret)
        val currentQuest = loader.questFor(scenario, questOrderToStart - 1)

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
        // todo: Should make checks for null states before calling this
        state: State?,
        scenario: String,
        questOrder: Int,
        secret: String,
        locationString: String
    ): WebAction {
        if (state == null) {
            return WebAction(OnlyView("missingCookie"), null)
        }

        if(state.scenario != scenario) {
            logger.info("Restarting scenario due to state having different scenario: ${state.scenario}")
            val quest = loader.questFor(scenario, 0)
            return initScenario(state, scenario, quest.secret)
        }


        // Trying to complete earlier quest while passed DL on later quest. User fails to complete
        // quest in given time and then goes back to previous quest and scan it.
        if(state.currentQuest > questOrder && hasQuestDLPassed(state)) {
            logger.info("Restarting scenario due to later quest DL passed: ${state.currentQuest}")
            val quest = loader.questFor(scenario, 0)
            return initScenario(state, state.scenario, quest.secret)
        }

        // TODO: This is overlapping with one check above
        // And edge case trying to complete intro quest while on another scenario
        if (scenario != state.scenario) {
            return initScenario(state, scenario, secret)
        }

        // Special handling for trying to complete second quest (#1) without ever starting it.
        // This could happen when you arrive to the spot with group and only some have scanned
        // the first quest code and clicked to start the next.
        if ((state.currentQuest == 0 && questOrder == 1)) {
            logger.info("Restarting scenario")
//            return WebAction(OnlyView(loader.questFor(scenario,0).successPage),
//            State(scenario,0,null,timeProvider.now(),state.userId,state.scenarioRestartCount+1)
//                )
            return initScenario(state, scenario, loader.questFor(scenario, 0).secret)
        }
        // Let's redirect the user to try to complete the first quest
//        if (state.currentQuest == 0 && questOrder == 1) {
//            val questToComplete = loader.questFor(scenario, 0)
//            return redirectToQuestCompleteThroughLocationReading(scenario, questToComplete, state)
//        }

        val quest = loader.questFor(scenario, questOrder, secret)

        // An edge case of trying to complete intro quest while already further on scenario
        if (questOrder == 0 && state.currentQuest != 0) {
            return initScenario(state, scenario, secret)
        }

        // If trying to complete earlier quest, just continue the timer of current quest
        if (quest.order < state.currentQuest) {
            // The quest user was currently trying to complete
            val currentQuest = loader.questFor(scenario, state.currentQuest)
            val view = CountdownViewModel(
                timeProvider.now().toEpochSecond(),
                state.questDeadline?.toEpochSecond(),
                currentQuest.fictionalCountdown,
                currentQuest.location!!.lat,
                currentQuest.location!!.lon
            )

            return WebAction(view, state)
        }

        val locationReading = LocationReading.fromString(locationString)
        checkIsFresh(locationReading)

        return WebAction(checkQuestCompletion(scenario, quest, locationReading.toCoordinates(), state), state)
    }

    private fun redirectToQuestCompleteThroughLocationReading(scenario: String, questToComplete: Quest, state: State): WebAction {
        return WebAction(
            askForLocation(questCompleteUrl(scenario, questToComplete), questToComplete),
            state
        )
    }

    private fun checkQuestCompletion(scenario: String, quest: Quest, location: Coordinates, state: State): ViewModel {
        assertEqual(scenario, state.scenario, "scenario completion")
        assertEqual(quest.order, state.currentQuest, "quest matching")
        quest.location?.let { assertProximity(it, location) }

        // TODO: Why do we need to check if quest has countdown, why is not enough to check state DL?
        return if (quest.countdown != null && hasQuestDLPassed(state)) {
            logger.info("Quest failed due to time running out")
            OnlyView(quest.failurePage)
        } else {
            logger.info("Quest completed successfully")
            if (loader.isLastQuest(scenario, quest.order)) {
                ScenarioEndViewModel(quest.successPage)
            } else {
                val nextQuest = loader.questFor(scenario, quest.order + 1)
                QuestEndViewModel(quest.successPage, nextQuest, quest)
            }
        }
    }

    private fun hasQuestDLPassed(state: State) =
        timeProvider.now().isAfter(state.questDeadline)

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
        if (!verifyLocation) {
            return
        }

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
