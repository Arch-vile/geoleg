package com.nakoradio.geoleg.services

import com.nakoradio.geoleg.model.Coordinates
import com.nakoradio.geoleg.model.LocationReading
import com.nakoradio.geoleg.model.Quest
import com.nakoradio.geoleg.model.State
import com.nakoradio.geoleg.model.TechnicalError
import com.nakoradio.geoleg.model.WebAction
import com.nakoradio.geoleg.utils.Time
import com.nakoradio.geoleg.utils.distance
import java.lang.IllegalStateException
import java.time.Duration
import java.util.UUID
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
        state: State?,
        scenario: String,
        secret: String
    ): WebAction {
        logger.info("Initializing scenario: $scenario")
        val quest = loader.questFor(scenario, 0, secret)
        val newState = State(
            scenario = scenario,
            // TODO: not sure why we want set this? to avoid having null?
            questDeadline = timeProvider.now().plusYears(10),
            questStarted = timeProvider.now(),
            questCompleted = null,
            currentQuest = 0,
            scenarioRestartCount =
                if (state?.scenario == scenario) state.scenarioRestartCount + 1 else 0,
            scenarioStarted = timeProvider.now(),
            userId = state?.userId ?: UUID.randomUUID()
        )
        return WebAction(askForLocation(questCompleteUrl(scenario, quest), quest), newState)
    }

    /**
     * Start next quest. This endpoint is called when clicking "GO" to start the next quest.
     */
    fun startQuest(
        state: State,
        // TODO: state has scenario, why need to take that as param at all? Would be safer to just use what is in state?
        scenario: String,
        questOrderToStart: Int,
        secret: String,
        locationString: String
    ): WebAction {
        // Start for the first quest is never called as we go through the scenario init
        if (questOrderToStart == 0) {
            throw IllegalStateException("First quest should never be started")
        }

        // TODO: We should do these checks on the controller already?
        assertEqual(state.scenario, scenario, "Bad cookie scenario")
        val questToStart = loader.questFor(scenario, questOrderToStart, secret)

        val currentQuest = loader.currentQuest(state)

        // Current quest not been completed and DL has passed
        if (hasQuestDLPassed(state) && state.questCompleted == null) {
            // TODO: current quest can be determined from the state
            return questFailedAction(state, currentQuest)
        }

        // Trying to start out of order quest or start next before completing current. Keep on running current one
        if (questOrderToStart != state.currentQuest + 1 || state.questCompleted == null) {
            logger.info("Trying to start out of order quest, showing countdown of current")
            return WebAction(continueCountdownView(state), state)
        }

        // Trying to restart current quest, continue countdown
        if (questToStart.order == currentQuest.order) {
            return WebAction(continueCountdownView(state), state)
        }

        currentQuest.location?.let {
            val locationReading = LocationReading.fromString(locationString)
            checkIsFresh(locationReading)
            assertProximity(it, locationReading.toCoordinates())
        }

        var newState = state.copy(
            questStarted = timeProvider.now(),
            currentQuest = questOrderToStart,
            questDeadline = questToStart.countdown?.let { timeProvider.now().plusSeconds(it) },
            questCompleted = null
        )

        return WebAction(createCountdownView(questToStart), newState)
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
        // Tested out, we can just make the cookie non optional on controller
        state: State?,
        scenario: String,
        questOrder: Int,
        secret: String,
        locationString: String
    ): WebAction {
        // We get rid of this check then
        if (state == null) {
            return WebAction(OnlyView("missingCookie"), null)
        }

        if (state.scenario != scenario) {
            logger.info("Restarting scenario due to state having different scenario: ${state.scenario}")
            return restartScenario(scenario, state)
        }

        // Second QR (first on field qr) should always init the scenario. This allows users to
        // always start from the beginning even if still having time to complete current quest.
        if (questOrder == 1 && state.currentQuest > 1) {
            logger.info("Restarting scenario as second QR was scanned")
            return restartScenario(scenario, state)
        }

        // Trying to complete earlier quest while passed DL on later quest.
        // One use case is that user fails to complete quest in given time and
        // then goes back to previous quest and scans it.
        // This logic not needed, as we decided to show the failure page on this case.
//        if (state.currentQuest > questOrder && hasQuestDLPassed(state)) {
//            logger.info("Restarting scenario due to later quest DL passed: ${state.currentQuest}")
//            val quest = loader.questFor(scenario, 0)
//            return initScenario(state, state.scenario, quest.secret)
//        }

        // TODO: This is overlapping with one check above
        // And edge case trying to complete intro quest while on another scenario
        if (scenario != state.scenario) {
            return initScenario(state, scenario, secret)
        }

        // Special handling when running first quest. Trying to complete any later quest.
        // This could happen when you arrive to the spot with group and only some have scanned
        // the first quest code and clicked to start the second. If use have not, then
        // they try to complete seconds quest without ever starting it.
        //
        // Let's also restart if user is trying to complete any later quest.
        if ((state.currentQuest == 0 && questOrder != 0)) {
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

        // An edge case of trying to complete intro quest while already further on scenario
        if (questOrder == 0 && state.currentQuest != 0) {
            return initScenario(state, scenario, secret)
        }



        // If trying to complete out of order quest, just continue the timer of current quest
        // Unless current quest has shared QR with the one we try to complete
        val questToComplete = loader.questFor(scenario, questOrder, secret)
        if (loader.questFor(scenario, state.currentQuest).sharedQrWithQuest !== questOrder &&
            questToComplete.order !== state.currentQuest
        ) {

            // If curren quest is already completed
                if(state.questCompleted != null)
                    return questEndView(state);


            // If DL for current quest has passed, show failure page
            if (hasQuestDLPassed(state)) {
                // If DL has passed but scanning the online or first on field QR. We should restart the scenario
                // instead of show the quest failure, as this would allow user to restart easily.
                if (questToComplete.order <= 1) {
                    logger.info("Restarting scenario due to quest DL passed: ${state.currentQuest}")
                    val quest = loader.questFor(scenario, 0)
                    return initScenario(state, state.scenario, quest.secret)
                }

                return questFailedAction(state, loader.currentQuest(state))
            }

            return WebAction(continueCountdownView(state), state)
        }

        return complete(scenario, questToComplete, locationString, state)
    }

    private fun complete(scenario: String, questToComplete: Quest, locationString: String, state: State): WebAction {
        val locationReading = LocationReading.fromString(locationString)
        checkIsFresh(locationReading)

        val location = locationReading.toCoordinates()
        var view: ViewModel? = null
//           checkQuestCompletion(scenario,
//           questToComplete,
//           locationReading.toCoordinates(), state)

        var quest = questToComplete
        if (loader.questFor(scenario, state.currentQuest).sharedQrWithQuest == quest.order) {
            quest = loader.questFor(scenario, state.currentQuest)
        }

        assertEqual(scenario, state.scenario, "scenario completion")
        assertEqual(quest.order, state.currentQuest, "quest matching")

        // Let's use the location of the shared quest if applicable
        questToComplete.location?.let { assertProximity(it, location) }

        if (hasQuestDLPassed(state)) {
            logger.info("Quest failed due to time running out")
            return WebAction(questFailedView(quest), state)
        } else {
            logger.info("Quest completed successfully")
            val newState = state.copy(questCompleted = timeProvider.now())
            if (loader.isLastQuest(scenario, quest.order)) {
                val elapsed = Duration.ofSeconds(newState.scenarioStarted.toEpochSecond() - timeProvider.now().toEpochSecond())
                logger.info("Scenario completed successfully in time: $elapsed")
                return questEndView(newState);
            } else {
                return questEndView(newState);
            }
        }
    }

    fun questEndView( state: State): WebAction {
        val quest = loader.currentQuest(state)
        return if (loader.isLastQuest(state.scenario, quest.order)) {
            WebAction(ScenarioEndViewModel(quest.successPage), state)
        } else {
            val nextQuest = loader.nextQuest(state)
            WebAction(QuestEndViewModel(quest.successPage, nextQuest, quest), state)
        }
    }

    private fun restartScenario(scenario: String, state: State): WebAction {
        val quest = loader.questFor(scenario, 0)
        return initScenario(state, scenario, quest.secret)
    }

    private fun continueCountdownView(
        state: State
    ): CountdownViewModel {
        val currentQuest = loader.currentQuest(state)
        return CountdownViewModel(
            state.questStarted.toEpochSecond(),
            state.questDeadline?.toEpochSecond(),
            currentQuest.fictionalCountdown,
            currentQuest.location?.lat,
            currentQuest.location?.lon,
            currentQuest.message
        )
    }

    private fun createCountdownView(forQuest: Quest): CountdownViewModel {
        val now = timeProvider.now()
        var expiresAt =
            forQuest.countdown?.let { now.plusSeconds(it).toEpochSecond() }
        return CountdownViewModel(
            now.toEpochSecond(),
            expiresAt,
            forQuest.fictionalCountdown,
            forQuest.location?.lat,
            forQuest.location?.lon,
            forQuest.message
        )
    }

    private fun questFailedView(quest: Quest) = OnlyView(quest.failurePage)

    private fun questFailedAction(state: State, quest: Quest) =
        WebAction(OnlyView(quest.failurePage), state)

    private fun hasQuestDLPassed(state: State) =
        if(state.questCompleted != null) false else
        state.questDeadline?.let { it.isBefore(timeProvider.now()) } ?: false

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
