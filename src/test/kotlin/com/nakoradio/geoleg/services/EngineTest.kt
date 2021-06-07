package com.nakoradio.geoleg.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.nakoradio.geoleg.model.LocationReading
import com.nakoradio.geoleg.model.Quest
import com.nakoradio.geoleg.model.Scenario
import com.nakoradio.geoleg.model.State
import com.nakoradio.geoleg.model.TechnicalError
import com.nakoradio.geoleg.model.WebAction
import com.nakoradio.geoleg.utils.Time
import java.time.OffsetDateTime
import java.util.UUID
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail

internal class EngineTest {

    private val jsonmappper = ObjectMapper().registerModule(KotlinModule())
    private val loader = ScenarioLoader(jsonmappper)
    private val timeProvider = object : Time() {
        val now = super.now()
        override fun now(): OffsetDateTime {
            return now
        }
    }

    // Engine has location verification turned on
    private val engine = Engine(
        true,
        timeProvider,
        loader
    )


    /**
     * First quest is the one started and automatically completed by scanning the QR code on the
     * geocaching.com site so this is a bit different from your normal `running a quest` state.
     *
     * These tests are for the case where user has completed first quest (completing quest still
     * keeps it as the active quest) and is reading the success page of the quest (the background
     * story for the scenario).
     *
     * Next step for the user is to click "Go" and start the second quest.
     *
     * User is currently on page: `/engine/complete/:scenario/0/:secret/:location`
     */
    @Nested
    inner class `Running the first quest` {

        val scenario = loader.table.scenarios[1]
        val currentQuest = scenario.quests[0]
        val currentState = State(
            scenario.name,
            currentQuest.order,
            currentQuest.countdown?.let { timeProvider.now().plusSeconds(it) } ,
            timeProvider.now().minusMinutes(1),
            UUID.randomUUID(),
            5
        )

        /**
         * Second quest (target location is the first QR on the field) can be started anywhere,
         * as the "Go" button is shown after the autocompleting first quest. Most likely second
         * quest is started at home.
         */
        @Test
        fun `Clicking GO to start second quest does not require valid location`() {
            // When: Starting the second quest with random location
            val nextQuestOrder = currentQuest.order + 1
            val outcome = engine.startQuest(currentState, scenario.name,
                nextQuestOrder, scenario.quests[nextQuestOrder].secret,
                // Any location will do
                LocationReading(2.0, 3.0, timeProvider.now()).asString())

            // Then: Second quest successfully started
            assertQuestStarted(outcome, currentState, scenario.quests[nextQuestOrder])
        }

        @Test
        fun `Rescanning the QR code will reinitialize the scenario`() {
            // When: Scanning the QR code again, i.e. calling the scenario init
            val outcome =  engine.initScenario(currentState, scenario.name, currentQuest.secret)

            // Then: Scenario is restarted
            assertScenarioRestartAction(currentState, scenario, outcome)
        }

        /**
         * User is currently on the first quest complete page (as it was automatically
         * completed). Reloading page should complete the quest again.
         */
        @Test
        fun `Reloading page completes current quest again`() {
            // When: Completing current quest again
            val outcome = engine.complete(currentState, scenario.name, currentQuest.order, currentQuest.secret,
                // Any location will do
                LocationReading(2.0, 3.0, timeProvider.now()).asString())

            // Then: Quest completed again
            assertQuestCompleted(outcome,currentState,currentQuest,scenario)
        }

        /**
         * A special case for first-second quest.
         *
         * User has scanned the online qr and completed the first quest (#0) (it completes automatically)
         * but has not clicked to start the next quest. Active quest is still 0.
         *
         * He could have received the coordinates for the second quest (#1) by other means or from
         * someone else. Now that he scans the second quest code, we can just restart the scenario.
         * We could just complete the second quest, but maybe safer to just restart the scenario.
         */
        @Test
        fun `Scanning QR of next quest should restart the scenario`() {
            // When: Trying to complete second quest
            val result = engine.complete(currentState, scenario.name, 1, scenario.quests[1].secret, freshLocation(scenario.quests[1]))

            // Then: Restart the scenario
            assertScenarioRestartAction(currentState,scenario,result)
        }

        /**
         * Trying to complete any other later quest than second one should fail with error.
         *
         * User is scanning the QR code of a later quest.
         */
       @Test
       fun `Scanning QR of a later quest should fail with error`() {
           // When: Completing later quest
             val outcome =   engine.complete(currentState, scenario.name, 2, scenario.quests[2].secret, freshLocation(scenario.quests[2]))

            // Then: Restart the scenario
            assertScenarioRestartAction(currentState, scenario, outcome)
       }


        /**
         * Start url action for first quest is never called. Should never happen so is ok to just fail.
         */
        @Test
        fun `Calling start URL of first quest will give error`() {
            assertThrows<TechnicalError> {
                engine.startQuest(
                    currentState,
                    scenario.name,
                    0,
                    scenario.quests[0].secret,
                    LocationReading(2.2, 1.1, timeProvider.now()).asString()
                )
            }
        }

        /**
         * Starting a later quest should never happen.
         *
         * Let's just restart the scenario
         */
        @Test
        fun `Calling start URL of a later quest should restart the scenario`() {
            // Trying to start a later quest
            val outcome = engine.startQuest(
                currentState,
                scenario.name,
                3,
                scenario.quests[3].secret,
                freshLocation(scenario.quests[3]))

            // Then: Scenario is restarted
            assertScenarioRestartAction(currentState, scenario, outcome)
        }

    }

    /**
     * User does not have a state cookie. They have never started any scenario, have cleared
     * the cookies or are using a different browser.
     */
    @Nested
    inner class `User does not have state cookie` {

        val scenario = loader.table.scenarios[0]

        /**
         * Starting the scenario by scanning the QR code on the Geocaching.com website.
         */
        @Test
        fun `Starting the scenario`() {
            // When: Initiating scenario without a state
            val action = engine.initScenario(null, scenario.name, scenario.quests[0].secret)
            // Then: State set to scenario start
            assertThat(
                action.state,
                equalTo(
                    State(
                        scenario = scenario.name,
                        currentQuest = 0,
                        questStarted = timeProvider.now(),
                        questDeadline = timeProvider.now().plusYears(10),
                        userId = action.state!!.userId,
                        scenarioRestartCount = 0
                    )
                )
            )

            // And: Quest complete called next after location location got
            assertThat(
                action.modelAndView as LocationReadingViewModel,
                equalTo(
                    LocationReadingViewModel("/engine/complete/${scenario.name}/0/${scenario.quests[0].secret}", null, null)
                )
            )
        }



        /**
         * Scanning QR code (other then first or second) without having any cookies.
         * Could happen by accidentally switching browser or clearing cookies.
         * Or if you just randomly find the QR code without going through previous
         * quests.
         *
         * MissingCookie page has instructions for the user about the Geocache and how to get
         * started.
         *
         */
        @Test
        fun `Scanning a random qr code`() {
            // When: Completing third quest without state
            val action = engine.complete(
                null,
                scenario.name,
                2,
                scenario.quests[2].secret,
                freshLocation(scenario.quests[2])
            )

            // Then: Missing cookie page shown
            assertMissingCookieErrorShown(action)
        }

        /**
         * Calling a start action for any quest. Start action is called when clicking
         * the "Go" on the web page to start the next quest. It is hard to come up with
         * a scenario where user would call start without any state. Technically possible
         * of course by clearing cookies and navigating browser history.
         */
        @Test
        fun `Starting a quest without a state`() {
            // Not possible as controller does not accept request without cookie
        }

    }

    private fun assertMissingCookieErrorShown(action: WebAction) {
        assertThat(
            action, equalTo(
                // And: Missing cookie error shown
                WebAction(
                    OnlyView("missingCookie"),
                    // And: State not set
                    null
                )
            )
        )
    }

    /**
     * User has scanned the QR code on website (init scenario action) and
     * state was set for quest 0. Location reading view is shown and after
     * successful read user is redirected to the complete-action.
     *
     * User sees the intro quest's success page and go-button to start the
     * next quest.
     */
    @Nested
    inner class `User has state cookie for the scenario's intro quest` {

        val scenario = loader.table.scenarios[0]
        val currentQuest = scenario.quests[0]

        // Given: Location far away from quest location. (intro quest does not check location)
        val locationString = LocationReading(
            37.156027,
            145.379261,
            timeProvider.now()
        )
            .asString()

        // State set for intro quest running
        val currentState = State(
            scenario = scenario.name,
            currentQuest = 0,
            questDeadline = null,
            questStarted = timeProvider.now(),
            userId = UUID.randomUUID(),
            scenarioRestartCount = 0
        )

        /**
         * Init scenario will redirect to this action to automatically complete
         * the intro after location read.
         */
        @Test
        fun `Complete the intro quest`() {
            // When: Intro quest is completed
            val action = engine.complete(currentState, scenario.name, 0, currentQuest.secret, locationString)

            // Then: Redirected to success page and state is unchanged
            assertThat(
                action,
                equalTo(
                    WebAction(
                        QuestEndViewModel(
                            currentQuest.successPage,
                            scenario.nextQuest(currentQuest),
                            currentQuest
                        ),
                        currentState
                    )
                )
            )
        }

        /**
         * Start the next quest
         */
        @Test
        fun `Start next quest`() {
            // When: Starting the second quest
            val questToStart = scenario.quests[1]
            val (viewModel, newState) = engine.startQuest(
                currentState,
                scenario.name,
                1,
                questToStart.secret,
                locationString
            )

            // Then: Redirected to countdown page, but without countdown or expiry
            // Regardless of questDeadline already passed
            // Regardless of location not matching
            assertThat(
                (viewModel as CountdownViewModel),
                equalTo(
                    CountdownViewModel(
                        newState!!.questStarted.toEpochSecond(),
                        null,
                        null,
                        questToStart.location!!.lat,
                        questToStart.location!!.lon
                    )
                )
            )

            // And: Current quest set to second quest
            assertThat(
                newState,
                equalTo(
                    currentState.copy(
                        // And: New state has no deadline set
                        questDeadline = null,
                        // Ans: questStarted timestamp updated
                        questStarted = timeProvider.now(),
                        // And: current quest updated
                        currentQuest = 1
                    )
                )
            )
        }

        /**
         * User calling start-action for some other quest than 1. Not expected to happen
         * but technically possible. Let's just try to complete the quest 0 again.
         */
        @Test
        fun `Trying to start some other quest`() {
            // When: Trying to start out of order quest
            assertProperHandlingOfStartingOurOfOrder(0)
            assertProperHandlingOfStartingOurOfOrder(2)
        }

        @Test
        fun `Trying to complete a further quest`() {
            // When: Completing a further quest
            var questToComplete = scenario.quests[2]

            // Then: Fails with error
            assertThrows<TechnicalError> {
                engine.complete(
                    currentState,
                    scenario.name,
                    questToComplete.order,
                    questToComplete.secret,
                    freshLocation(questToComplete)
                )
            }
        }



        @Test
        fun `Calling start for for anything else then quest 1`() {
            val questToStart = scenario.quests[2]
            val (viewModel, state) =
                engine.startQuest(
                    currentState,
                    scenario.name,
                    questToStart.order,
                    questToStart.secret,
                    freshLocation(questToStart)
                )

            // Then: State is not changed
            assertThat(state, equalTo(currentState))

            // And: Redirect to quest 0 complete
            assertThat(
                viewModel as LocationReadingViewModel,
                equalTo(
                    LocationReadingViewModel(
                        "/engine/complete/ancient-blood/0/6a5fc6c0f8ec",
                        null, null
                    )
                )
            )
        }

        private fun assertProperHandlingOfStartingOurOfOrder(questToStart: Int) {
            val (viewModel, state) =
                engine.startQuest(currentState, scenario.name, questToStart, scenario.quests[questToStart].secret, locationString)

            // Then: State is not changed
            assertThat(state, equalTo(currentState))

            // And: Quest 0 success is shown
            assertThat(
                viewModel as LocationReadingViewModel,
                equalTo(
                    LocationReadingViewModel(
                        "/engine/complete/ancient-blood/0/6a5fc6c0f8ec",
                        null, null
                    )
                )
            )
        }
    }

    @Nested
    inner class `All the hacky stuff`() {

        val scenario = loader.table.scenarios[0]

        /**
         * We should allow "restarting" a quest, of course not resetting the countdown. This allows
         * reloading the start quest page (the countdown view) without ending up in error.
         *
         * Use case is:
         * 1. User completes the quest
         * 2. On the countdown view user reloads the page (so basically requests the quest start action)
         *
         * We should show to countdown view.
         */
        @Test
        fun `Restarting quest by requesting again the start quest url`() {
            // Given: User is currently doing quest 3
            val currentQuest = scenario.quests[3]
            val previousQuest = scenario.quests[2]

            // Given: State is set for running quest 3
            val state = State(
                scenario = scenario.name,
                currentQuest = currentQuest.order,
                // There is still time left
                questDeadline = timeProvider.now().plusMinutes(5),
                questStarted = timeProvider.now(),
                userId = UUID.randomUUID(),
                scenarioRestartCount = 3
            )

            // When: Restarting the quest
            val (viewModel, newState) = engine.startQuest(state, scenario.name, currentQuest.order, currentQuest.secret, freshLocation(previousQuest))

            // Then: State is not changed
            assertThat(newState, equalTo(state))

            // And: Countdown page shown
            // Then: Redirected to countdown page
            assertThat(
                viewModel as CountdownViewModel,
                equalTo(
                    CountdownViewModel(
                        now = state.questStarted.toEpochSecond(),
                        lat = currentQuest.location!!.lat,
                        lon = currentQuest.location!!.lon,
                        expiresAt = state.questDeadline!!.toEpochSecond(),
                        fictionalCountdown = currentQuest.fictionalCountdown
                    )
                )
            )
        }

        @Test
        fun `Start for the first quest should never be called`() {
            fail("foo")
        }

        /**
         * Re-scanning the qr code after starting the quest. So the scenario would be:
         * 1. User arrives at the quest end location
         * 2. User scans the code
         * 3. User starts the next quest
         * 4. User scans the code again (points to quest complete action)
         *
         * In this case we should just show the countdown page for the already started quest.
         */
        @Test
        fun `Trying to complete an already completed quest`() {
            // Given: User is currently doing quest 3
            val currentQuest = scenario.quests[3]
            val previousQuest = scenario.quests[2]

            // Given: State is set for running quest 3
            val state = State(
                scenario = scenario.name,
                currentQuest = currentQuest.order,
                // There is still time left
                questDeadline = timeProvider.now().plusMinutes(5),
                questStarted = timeProvider.now(),
                userId = UUID.randomUUID(),
                scenarioRestartCount = 3
            )

            // When: Scanning the previous QR code, so basically trying to complete an earlier quest
            // Then: No state is returned, only view
            val viewModel = engine.complete(state, scenario.name, previousQuest.order, previousQuest.secret, freshLocation(previousQuest))

            // Then: Countdown view shown for the already started quest
            assertThat(
                viewModel as CountdownViewModel,
                equalTo(
                    CountdownViewModel(
                        now = state.questStarted.toEpochSecond(),
                        lat = currentQuest.location!!.lat,
                        lon = currentQuest.location!!.lon,
                        expiresAt = state.questDeadline!!.toEpochSecond(),
                        fictionalCountdown = currentQuest.fictionalCountdown
                    )
                )
            )
        }
    }

    /**
     * Loading (and reloading) the `/engine/complete/$scenarioName/0/$quest0Secret` action
     */
    @Nested
    inner class `Scenario's intro quest completion` {

        val scenario = loader.table.scenarios[0]

        // Given: Location far away from quest location. (intro quest does not check location)
        val locationString = LocationReading(37.156027, 145.379261, timeProvider.now()).asString()

        // And: Proper state for the scenario intro quest
        val state = State(
            scenario = scenario.name,
            currentQuest = 0,
            // questDeadline is already passed (intro quest does not check questDeadline)
            questDeadline = timeProvider.now().minusDays(10),
            questStarted = timeProvider.now().minusDays(11),
            userId = UUID.randomUUID(),
            scenarioRestartCount = 10
        )

        /**
         * User cannot end up here by scanning QR code (because the intro quest QR code points to
         * init scenario action instead of complete) but can access this e.g. by using browser
         * history or such.
         */
        @Test
        fun `trying to complete intro while already on further quest`() {
            // Given: User has already progressed to a further quest
            val stateForFurtherQuest = state.copy(currentQuest = 1)

            // When: Executing the intro's `complete` action
            val action = engine.complete(stateForFurtherQuest, scenario.name, 0, scenario.quests[0].secret, locationString)

            // Then: Intro quest is successfully restarted
            assertScenarioRestartAction(state, scenario, action)
        }

        @Test
        fun `trying to complete intro while on another scenario`() {
            // Given: User is on another scenario
            val stateForAnotherScenario = state.copy(scenario = "some other scenario")

            // When: Executing the intro's `complete` action
            val action = engine.complete(stateForAnotherScenario, scenario.name, 0, scenario.quests[0].secret, locationString)

            // Then: Intro quest is successfully restarted
            assertScenarioRestartAction(stateForAnotherScenario, scenario, action)
        }

        @Test
        fun `trying to complete intro for non existing scenario`() {
            // Given: User has state set for completing the intro quest
            // When: Completing intro for non existing scenario
            assertThrows<TechnicalError> {
                engine.complete(state, "other scenario", 0, scenario.quests[0].secret, locationString)
            }
        }

        @Test
        fun `trying to complete intro while on unknown scenario`() {
            // Given: State has unknown scenario (only possible if scenarios renamed or removed)
            val badState = state.copy(scenario = "other scenario")

            // When: Executing the intro's `complete` action
            val action = engine.complete(badState, scenario.name, 0, scenario.quests[0].secret, locationString)

            // Then: Scenario restarted
            assertScenarioRestartAction(badState, scenario, action)
        }

        @Test
        fun `trying to complete another scenario's intro with this scenario's secret`() {
            // Given: User has state set for completing the intro quest
            // When: Trying to complete a different scenario with this scenario's secret
            val anotherScenario = loader.table.scenarios[1]
            val scenarioNameOfAnotherExistingScenario = anotherScenario.name
              val result = engine.complete(state, scenarioNameOfAnotherExistingScenario, 0, scenario.quests[0].secret, locationString)

            // Then: Restarting scenario
            assertScenarioRestartAction(state,anotherScenario,result)
        }

        @Test
        fun `trying to complete intro with malformed location`() {
            // When: Intro quest is completed, error is thrown due to malformed location
            assertThrows<TechnicalError> {
                engine.complete(state, scenario.name, 0, scenario.quests[0].secret, "badLocation")
            }
        }

        @Test
        fun `trying to complete intro with bad secret`() {
            // When: Intro quest is completed, error is thrown due to mismatch in secret
            assertThrows<TechnicalError> {
                engine.complete(state, scenario.name, 0, "bad secret", locationString)
            }
        }
    }

    /**
     * Loading (and reloading) the `/engine/init/$scenarioName/$quest0Secret` action
     */
    @Nested
    inner class `Scenario initialization` {

        @Test
        fun `init scenario will reset the existing state for scenario`() {
            val scenario = loader.table.scenarios[0]

            // Given: State with old dates and later quest order
            val existingState = State(scenario.name, 10, timeProvider.now().minusDays(20), timeProvider.now().minusDays(39), UUID.randomUUID(), 10)

            // When: scenario is initialized
            val (viewModel, newState) = engine.initScenario(
                existingState,
                scenario.name,
                scenario.quests[0].secret
            )

            assertThat(
                newState,
                equalTo(
                    existingState.copy(
                        // Then: State is reset to first quest
                        currentQuest = 0,
                        // And: questDeadline is set far in future, to "never" expire
                        questDeadline = timeProvider.now().plusYears(10),
                        // And: Restart count is increased by one
                        scenarioRestartCount = 11,
                        questStarted = timeProvider.now()
                    )
                )
            )
        }

        @Test
        fun `init scenario will reset the state of another scenario`() {
            val scenario = loader.table.scenarios[0]

            // Given: State for another scenario
            val existingState = State("the other scenario", 1, timeProvider.now().plusDays(1), timeProvider.now(), UUID.randomUUID(), 2)

            // When: scenario is initialized
            val (url, newState) = engine.initScenario(
                existingState,
                scenario.name,
                scenario.quests[0].secret
            )

            assertThat(
                newState,
                equalTo(
                    existingState.copy(
                        // Then: State is intialized for this scenario
                        scenario = scenario.name,
                        // Then: State is reset to first quest
                        currentQuest = 0,
                        // And: questDeadline is set far in future, to "never" expire
                        questDeadline = timeProvider.now().plusYears(10),
                        // And: Restart count is set to 0
                        scenarioRestartCount = 0,
                        questStarted = timeProvider.now()
                    )
                )
            )
        }

        /**
         * Scenario will be initialized when user scans the QR code on the Geocaching site.
         * The trick is that we will set the proper state and then redirect the user to the
         * quest complete url. This will result to the quest success page be shown for the user.
         *
         * It is good that we already use as much as the flow already on initialization so that
         * any technical difficulties will be catched early before heading to the field.
         */
        @Test
        fun `initializing scenario redirects to quest complete url`() {
            val scenario = loader.table.scenarios[0]

            // When: scenario is initialized with bad secret
            val (viewModel, newState) = engine.initScenario(
                State.empty(timeProvider),
                scenario.name,
                scenario.quests[0].secret
            )

            // Then: Redirected to quest complete
            // We want to read the location also although not needed, as this could allow user to
            // catch any technical errors on location reading already at home.
            assertThat(
                (viewModel as LocationReadingViewModel),
                equalTo(
                    LocationReadingViewModel(
                        "/engine/complete/ancient-blood/0/6a5fc6c0f8ec",
                        null, null
                    )
                )
            )
        }

        @Test
        fun `initializing scenario with bad secret should throw error`() {
            val scenario = loader.table.scenarios[0]

            // When: scenario is initialized with bad secret
            // Then: Throws
            assertThrows<TechnicalError> {
                val (url, newState) = engine.initScenario(
                    State.empty(timeProvider),
                    scenario.name,
                    "bad secret"
                )
            }
        }
    }

    @Nested
    inner class `Running the third quest` {

        // todo: expires while reading the success story

        // TODO: If DL has passed and trying to complete later quest, we should show the quest failed error

    }

    @Nested
    inner class `Starting the second quest` {
        /**
         * There is some speciality to the second quest also. Because the first quest is completed
         * at home, the second quest is also started from home. Usually quest need to be started
         * at the same location as the previous one was completed, but this does not apply
         * for the second quest.
         *
         * First quest is the introduction quest that is automatically instantly completed. Second
         * quest is thus also started right away at home. This has the following implications
         * for the second quest processing logic:
         * - Second quest can be started anywhere (not in the end point of previous one, as others)
         * - Second quest has unlimited time to complete
         */
        val scenario = loader.table.scenarios[1]
        val questToStart = scenario.quests[1]

        val currentState = State(
            scenario.name,
            0,
            null,
            timeProvider.now().minusDays(1),
            UUID.randomUUID(),
            5
        )



        @Test
        fun `fail if state's scenario is different from param`() {
            // Given: State that has wrong scenario
            val state = State(
                "this is not correct scenario",
                1,
                timeProvider.now().plusDays(10),
                timeProvider.now(),
                UUID.randomUUID(),
                5
            )

            // When: Starting quest
            // Then: Error on quest
            val error = assertThrows<TechnicalError> {
                engine.startQuest(state, scenario.name, 1, questToStart.secret, freshLocation(questToStart))
            }
            assertThat(error.message, equalTo("Not good: Bad cookie scenario"))
        }

        /**
         * Restarting
         */
        @Test
        fun `Restarting current quest just keeps on running the current one`() {
            // Given: Quest is already started
            val state = engine.startQuest(currentState,scenario.name,1,
                questToStart.secret,
                freshLocation(questToStart)).state

           // When: Restarting the quest
            val outcome = engine.startQuest(currentState,scenario.name,1,
                questToStart.secret,
                freshLocation(questToStart))

            assertThat(outcome, equalTo(
                WebAction(
                    // Then: Back to countdown view
                    CountdownViewModel(timeProvider.now().toEpochSecond(), null, null, questToStart.location!!.lat, questToStart.location!!.lon),
                    // And: State is not changed
                    state)
            ))
        }



        @Test
        fun `Starting later quest (with bad location) then something `() {

            fail("not tested")
        }

        @Test
        fun `Location is not checked`() {
            fail("not tested")
        }
    }

    @Nested
    inner class `Starting Nth quest` {

        private val scenario = loader.table.scenarios[1]
        private val questToStart = scenario.quests[2]

        @Test
        fun `fail if location reading is not fresh enough`() {
            // Given: Location that is old
            val locationString = LocationReading(
                questToStart.location!!.lat, questToStart.location!!.lon,
                timeProvider.now().minusMinutes(2)
            ).asString()

            // And: Valid state
            val state = State.empty(timeProvider)
                .copy(scenario = scenario.name, currentQuest = questToStart.order - 1)

            // When: Starting the quest
            val error = assertThrows<TechnicalError> {
                engine.startQuest(state, scenario.name, 2, questToStart.secret, locationString)
            }
            assertThat(error.message, equalTo("Location not fresh"))
        }

        /**
         * Quest is meant to be started from the previous quest's endpoint. Otherwise
         * you could cheat in the next quest by first failing the quest once
         * to get the location and on a second try go close to the quest endpoint
         * already before starting the timer.
         *
         */
        @Test
        fun `fail if location is not close enough to current quest's endpoint`() {
            // Given: Location that is not close to current quest's end point
            val locationString = LocationReading(
                // About 200 meters off
                questToStart.location!!.lat + 0.002, questToStart.location!!.lon,
                timeProvider.now()
            ).asString()

            // And: Valid state
            val state = State.empty(timeProvider)
                .copy(scenario = scenario.name, currentQuest = questToStart.order - 1)

            // When: Starting the quest
            val error = assertThrows<TechnicalError> {
                engine.startQuest(state, scenario.name, 2, questToStart.secret, locationString)
            }
            assertThat(error.message, equalTo("Bad gps accuracy"))
        }

        @Test
        fun `fail if state's scenario is different from param`() {
            // And: State with different scenario
            val state = State.empty(timeProvider)
                .copy(scenario = "not correct", currentQuest = questToStart.order - 1)

            // When: Starting the quest
            val error = assertThrows<TechnicalError> {
                engine.startQuest(state, scenario.name, 2, questToStart.secret, freshLocation(questToStart))
            }
            assertThat(error.message, equalTo("Not good: Bad cookie scenario"))
        }

        /**
         * If we try to start current quest again, we should just keep on running it without
         * changing anything. This could happen by going back in browser history.
         *
         */
        @Test
        fun `Trying to start quest again keeps it running`() {
            // And: State with current quest being the quest you try to start
            val state = State.empty(timeProvider)
                .copy(scenario = scenario.name, currentQuest = questToStart.order)

            // When: Starting the quest
            val foo = engine.startQuest(state, scenario.name, 2, questToStart.secret, freshLocation(questToStart))

            // Then: State is not changed
            assertThat(foo.state, equalTo(state))

            // And: Countdown page is shown
            assertThat(foo.modelAndView as CountdownViewModel, equalTo(CountdownViewModel(timeProvider.now.toEpochSecond(), state.questDeadline!!.toEpochSecond(), questToStart.fictionalCountdown, questToStart.location!!.lat, questToStart.location!!.lon)))
        }

        @Test
        fun `Restarting current quest just keeps on running the current one`() {
            fail("not tested")
        }

        @Test
        fun `Starting earlier quest just keeps on running the current one`() {
            fail("not tested")
        }

        @Test
        fun `Starting later quest just keeps on running the current one`() {
            fail("not tested")
        }

        @Test
        fun `quest successfully started`() {
            val previousQuest = loader.questFor(scenario.name, questToStart.order - 1)

            // Given: Proper state. The previous quest has been completed (deadline could have passed already on that)
            val state = State(
                scenario.name,
                questToStart.order - 1,
                timeProvider.now().minusDays(1),
                timeProvider.now().minusDays(1),
                UUID.randomUUID(),
                5
            )

            // When: Starting the quest
            val (viewModel, newState) =
                engine.startQuest(
                    state,
                    scenario.name,
                    questToStart.order,
                    questToStart.secret,
                    // At the location of previous quest
                    freshLocation(previousQuest)
                )

            // Then: Redirected to countdown page
            assertThat(
                viewModel as CountdownViewModel,
                equalTo(
                    CountdownViewModel(
                        now = newState!!.questStarted.toEpochSecond(),
                        lat = questToStart.location!!.lat,
                        lon = questToStart.location!!.lon,
                        expiresAt = newState!!.questDeadline!!.toEpochSecond(),
                        fictionalCountdown = questToStart.fictionalCountdown
                    )
                )
            )

            assertThat(
                newState,
                equalTo(
                    state.copy(
                        // And: New state is updated with deadline accordingly to quest spec
                        questDeadline = timeProvider.now().plusSeconds(questToStart.countdown!!),
                        // Ans: questStarted timestamp update
                        questStarted = timeProvider.now(),
                        // And: current quest set to the one to start
                        currentQuest = questToStart.order
                    )
                )
            )
        }
    }

    /**
     * Second quest: the first quest on the field
     *
     * Completing the second quest is a special case. Because the second quest is started
     * at home (this is the first quest that gives coordinates to the field), it does not
     * check for the deadline.
     *
     * Also second quest should act as a reset switch for the scenario. For example if user tries
     * to complete the scenario but fails and wants to restart, it makes sense for them to
     * go and scan again the second QR.
     */
    @Nested
    inner class `Completing the second quest` {

        val scenario = loader.table.scenarios[1]
        val questToComplete = scenario.quests[1]

        @Test
        fun `Fail if location is not fresh`() {
            // Given: Valid state to complete quest
            val state = validStateToComplete()

            // And: Old location reading
            val locationString = LocationReading(
                questToComplete.location!!.lat,
                questToComplete.location!!.lon,
                timeProvider.now().minusDays(200)
            ).asString()

            // When: Completing the quest
            // Then: Error about expired location
            val error = assertThrows<TechnicalError> {
                engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, locationString)
            }
            assertThat(error.message, equalTo("Location not fresh"))
        }

        @Test
        fun `Fail if location is not close to quest location`() {
            // Given: Valid state to complete quest
            val state = validStateToComplete()

            // And: Location not close to target
            val locationString = LocationReading(
                questToComplete.location!!.lat - 0.002,
                questToComplete.location!!.lon,
                timeProvider.now()
            ).asString()

            // When: Completing the quest
            // Then: Error about not being close to target location
            val error = assertThrows<TechnicalError> {
                engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, locationString)
            }
            assertThat(error.message, equalTo("Bad gps accuracy"))
        }


        /**
         * One could just go to second quest's location without starting the first quest (for example
         * using laptop on the first quest and mobile phone on the location). Or just random guy
         * scanning the code.
         *
         * User would not have state at all. We should show the missing cookie page that also
         * has instructions for the user to restart the scenario.
         */
        @Test
        fun `Should show missing cookie page if user has no state`() {
            // Given: User has no state
            val state = null

            // When: Trying to complete second quest
            val result = engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, freshLocation(questToComplete))

            // Then: Restart the scenario
            assertMissingCookieErrorShown(result)
        }

        @Test
        fun `Should restart the scenario if user has wrong scanario`() {
            // Given: User has the wrong scenario
            val state = validStateToComplete().copy(scenario = "not correct")

            // When: Trying to complete second quest
            val result = engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, freshLocation(questToComplete))

            // Then: Restart the scenario
            assertScenarioRestartAction(state,scenario,result)
        }

        /**
         * Very likely scenario. User has failed to complete later quest due to DL, now they come
         * back to the starting position to scan the first QR code on the field again.
         */
        @Test
        fun `Should restart the scenario if user has run out of time on later quest`() {
            // Given: State for later quest with DL already passed
            val state = validStateToComplete().copy(currentQuest = 3, questDeadline = timeProvider.now.minusDays(20))

            // When: Trying to complete second quest
            val result = engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, freshLocation(questToComplete))

            // Then: Restart the scenario
            assertScenarioRestartAction(state,scenario,result)
        }

        @Test
        fun success() {
            // Given: Valid state to complete this quest
            val state = validStateToComplete()

            // When: Completing the quest
            val viewModel = engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, freshLocation(questToComplete))

            // Then: Success page is shown
            assertThat(viewModel.modelAndView as QuestEndViewModel, equalTo(QuestEndViewModel("quests/testing_1_success", scenario.nextQuest(questToComplete), questToComplete)))
        }

        private fun validStateToComplete(): State {
            return State(
                scenario = scenario.name,
                currentQuest = questToComplete.order,
                // Quest has been started ages ago
                questStarted = timeProvider.now().minusDays(100),
                userId = UUID.randomUUID(),
                scenarioRestartCount = 0,
                // No deadline for the second quest
                questDeadline = null
            )
        }
    }

    @Nested
    inner class `Completing the Nth quest` {

        val scenario = loader.table.scenarios[1]
        val questToComplete = scenario.quests[3]

        @Test
        fun `Fail if not completed in time`() {
            // Given: Deadline has expired
            val state = validStateToComplete()
                .copy(questDeadline = timeProvider.now().minusMinutes(1))

            // When: Starting the quest
            val viewModel = engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, freshLocation(questToComplete))

            // Then: Failure page is shown, state not changed
            assertThat(viewModel.modelAndView.view, equalTo(questToComplete.failurePage))
            assertThat(
                viewModel,
                equalTo(
                    WebAction(OnlyView("quests/testing_3_fail"), state)
                )
            )
        }

        @Test
        fun `Fail if location is not fresh`() {
            // Given: Valid state to complete quest
            val state = validStateToComplete()

            // And: Old location reading
            val locationString = LocationReading(
                questToComplete.location!!.lat,
                questToComplete.location!!.lon,
                timeProvider.now().minusDays(200)
            ).asString()

            // When: Completing the quest
            // Then: Error about expired location
            val error = assertThrows<TechnicalError> {
                engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, locationString)
            }
            assertThat(error.message, equalTo("Location not fresh"))
        }

        @Test
        fun `Fail if location is not close to quest location`() {
            // Given: Valid state to complete quest
            val state = validStateToComplete()

            // And: Location not close to target
            val locationString = LocationReading(
                questToComplete.location!!.lat - 0.002,
                questToComplete.location!!.lon,
                timeProvider.now()
            ).asString()

            // When: Completing the quest
            // Then: Error about not being close to target location
            val error = assertThrows<TechnicalError> {
                engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, locationString)
            }
            assertThat(error.message, equalTo("Bad gps accuracy"))
        }

        @Test
        fun `Fail if state's scenario does not match params`() {
            // Given: State has bad scenario
            val state = validStateToComplete().copy(scenario = "not correct")

            // When: Starting the quest
            // Then: Error about bad scenario
            val error = assertThrows<TechnicalError> {
                engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, freshLocation(questToComplete))
            }
            assertThat(error.message, equalTo("No such quest secret for you my friend"))
        }

        // Scanning QR code of some upcoming future quest
        @Test
        fun `Fail if state's quest is smaller than parameter quest`() {
            // Given: State has different quest
            val state = validStateToComplete().copy(currentQuest = questToComplete.order - 1)

            // When: Starting the quest
            // Then: Error about bad scenario
            val error = assertThrows<TechnicalError> {
                engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, freshLocation(questToComplete))
            }
            assertThat(error.message, equalTo("Not good: quest matching"))
        }

        // 60.291667, 24.924222
        /**
         * Regression: So I was in the middle of quest #5 started many days ago, then I
         * scanned the QR of the quest #2.
         *
         * Expected: To see a failure as I have run out of time for quest #5
         *
         * Observed: Quest #5 coordinates without a timer.
         *
         */
        @Test
        fun `Fail when scanning code of earlier quest if run out of time for current quest`() {
            // Given: Current quest has expired
            val state = validStateToComplete().copy(questDeadline = timeProvider.now.minusDays(20))

            // When: Scanning code of earlier quest (
            fail("not tested")
        }

        @Test
        fun `Scanning code of earlier quest will show countdown to current quest if time is still remaining`() {
            fail("not tested")
        }

        @Test
        fun success() {
            // Given: Valid state to complete this quest
            val state = validStateToComplete()

            // When: Completing the quest
            val viewModel = engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, freshLocation(questToComplete))

            // Then: Success page is shown, state is not changed
            assertThat(
                viewModel,
                equalTo(
                    WebAction(
                        QuestEndViewModel(
                            questToComplete.successPage,
                            scenario.nextQuest(questToComplete),
                            questToComplete
                        ),
                        state
                    )
                )
            )
        }

        @Test
        fun `Should restart the scenario if user has run out of time on later quest`() {
            // Given: State for later quest with DL already passed
            val state = validStateToComplete().copy(currentQuest = 3, questDeadline = timeProvider.now.minusDays(20))

            // When: Trying to complete second quest
            val result = engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, freshLocation(questToComplete))

            // Then: Restart the scenario
            assertScenarioRestartAction(state,scenario,result)
        }

        private fun validStateToComplete(): State {
            return State(
                scenario = scenario.name,
                currentQuest = questToComplete.order,
                // Quest has been started ages ago
                questStarted = timeProvider.now().minusDays(100),
                userId = UUID.randomUUID(),
                scenarioRestartCount = 0,
                // Deadline not yet reached
                questDeadline = timeProvider.now().plusMinutes(5)
            )
        }
    }

    @Nested
    inner class `Completing the last quest` {

        val scenario = loader.table.scenarios[1]
        val questToComplete = scenario.quests[3]

        @Test
        fun `success`() {
            // Given: Valid state to complete this quest
            val state = validStateToComplete()

            // When: Completing the quest
            val viewModel = engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, freshLocation(questToComplete))

            // Then: Success page is shown
            assertThat(
                viewModel as ScenarioEndViewModel,
                equalTo(
                    ScenarioEndViewModel("quests/testing_3_success")
                )
            )
        }

        private fun validStateToComplete(): State {
            return State(
                scenario = scenario.name,
                currentQuest = questToComplete.order,
                // Quest has been started ages ago
                questStarted = timeProvider.now().minusDays(100),
                userId = UUID.randomUUID(),
                scenarioRestartCount = 0,
                // Deadline not yet reached
                questDeadline = timeProvider.now().plusMinutes(5)
            )
        }
    }

    private fun freshLocation(questToStart: Quest) =
        LocationReading(
            questToStart.location!!.lat, questToStart.location!!.lon,
            timeProvider.now()
        ).asString()

    fun assertScenarioRestartAction(existingState: State?, scenario: Scenario, action: WebAction) {
        assertThat(
            action,
            equalTo(
                WebAction(
                    // Then: Go through location reading back to completing the first quest
                    LocationReadingViewModel("/engine/complete/${scenario.name}/0/${scenario.quests[0].secret}", null, null),
                    // Then: State is reset to the intro quest
                State(
                    scenario = scenario.name,
                    currentQuest = 0,
                    questDeadline = timeProvider.now().plusYears(10),
                    questStarted = timeProvider.now(),
                    userId = existingState?.userId ?: action.state!!.userId,
                    scenarioRestartCount =
                        if (existingState?.scenario == scenario.name)
                            existingState.scenarioRestartCount + 1
                        else 0
                )
            )
        )
        )
    }


    fun assertQuestCompleted(outcome: WebAction, currentState: State, questToComplete: Quest, scenario: Scenario) {
        assertThat(outcome, equalTo(
            WebAction(
                // Then: Show quest success view
               QuestEndViewModel(questToComplete.successPage, scenario.nextQuest(questToComplete),questToComplete),
                // And: State is not changing
                currentState)
        ))
    }
    fun assertQuestStarted(outcome: WebAction, currentState: State, questToStart: Quest) {
        assertThat(outcome, equalTo(
            WebAction(
                // Then: Show countdown view. Second quest has no DL.
                CountdownViewModel(
                    timeProvider.now().toEpochSecond(),
                    null,
                    null,
                    questToStart.location!!.lat, questToStart.location!!.lon),
                // And: State is updated for the quest to start
                currentState.copy(
                    currentQuest = questToStart.order,
                    questStarted = timeProvider.now(),
                    questDeadline = questToStart.countdown?.let { timeProvider.now().plusSeconds(it) }
                ))
        ))
    }
}
