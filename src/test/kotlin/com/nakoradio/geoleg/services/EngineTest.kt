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
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
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
                        questDeadline = null,
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
         * Trying to complete (i.e. scan the first on field qr code)
         * the second quest without any state
         * This could happen when using a desktop browser on home and then scanning the code
         * on the site with mobile. There are other valid reasons also.
         */
        @Test
        fun `Scanning the first on field QR should restart the scenario`() {
            // When: Completing the second quest without any state
            val action = engine.complete(
                null,
                scenario.name,
                1,
                scenario.quests[1].secret,
                freshLocation(scenario.quests[1])
            )

            // Then: State is set to scenario initialization
            assertThat(
                action.state,
                equalTo(
                    State(
                        scenario = scenario.name,
                        currentQuest = 0,
                        questDeadline = null,
                        questStarted = timeProvider.now(),
                        userId = action.state!!.userId,
                        scenarioRestartCount = 0
                    )
                )
            )

            // And: Redirected to quest complete automatically
            assertThat(
                action.modelAndView as LocationReadingViewModel,
                equalTo(
                    LocationReadingViewModel(
                        action = "/engine/complete/ancient-blood/0/6a5fc6c0f8ec",
                        lat = null,
                        lon = null
                    )
                )
            )
        }

        /**
         * Scanning QR code (other then first or second) without having any cookies.
         * Could happen by accidentally switching browser or clearing cookies.
         * Or if you just randomly find the QR code without going through previous
         * quests.
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

            // Then: State not set
            assertThat(action.state, `is`(Matchers.nullValue()))

            // And: Missing cookie error shown
            assertThat(
                action.modelAndView as OnlyView,
                equalTo(
                    OnlyView("missingCookie")
                )
            )
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
            val action = engine.complete(currentState, scenario.name, 0, scenario.quests[0].secret, locationString)

            // Then: Redirected to success page
            assertThat(action.modelAndView.view, equalTo(scenario.quests[0].successPage))

            // And: State is not changed
            assertThat(action.state, equalTo(currentState))
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

        /**
         * User scanning the first on field QR (complete quest 1) without starting the quest 1.
         * (still having quest 0 as active). This is
         * possible for example once going through the flow but then restarting the scenario
         * but never starting the quest 1. They know the coordinates already so they just go
         * the the first on field QR code.
         *
         * In this case, lets show the quest 0 complete and let them start again the quest 1 and
         * then complete it on the spot.
         */
        @Test
        fun `Scanning the first on field QR without yet running the second quest`() {
            val questToComplete = scenario.quests[1]
            val (viewModel, state) =
                engine.complete(
                    currentState,
                    scenario.name,
                    1,
                    questToComplete.secret,
                    freshLocation(questToComplete)
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

            // Then: Intro quest is successfully restarted
            assertScenarioRestartAction(badState, scenario, action)
        }

        @Test
        fun `xxxxxxxxxxxxxxxxxxxx trying to complete intro while on unknown scenario`() {
            // Given: State has unknown scenario (should not technically be possible)
            val badState = state.copy(scenario = "other scenario")

            // When: Intro quest is completed, error is thrown due to mismatch in scenario
            assertThrows<TechnicalError> {
                engine.complete(badState, scenario.name, 0, scenario.quests[0].secret, locationString)
            }
        }

        @Test
        fun `trying to complete another intro with this scenario's secret`() {
            // Given: User has state set for completing the intro quest
            // When: Trying to complete a different scenario with this scenario's secret
            assertThrows<TechnicalError> {
                engine.complete(state, loader.table.scenarios[1].name, 0, scenario.quests[0].secret, locationString)
            }
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
            assertThat((viewModel as LocationReadingViewModel).action, equalTo("/engine/complete/ancient-blood/0/6a5fc6c0f8ec do object comparison here"))
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

        @Test
        fun `fail if quest in state is not the previous one`() {
            // Given: State that has wrong previous quest
            val state = State(
                scenario.name,
                // CurrentQuest is not the previous one
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
            assertThat(error.message, equalTo("Not good: Bad cookie quest"))
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
         * This prevents user from restarting a quest by returning to previous point
         */
        @Test
        fun `fail if quest in state is not the previous one`() {
            // And: State with current quest being the quest you try to start
            val state = State.empty(timeProvider)
                .copy(scenario = scenario.name, currentQuest = questToStart.order)

            // When: Starting the quest
            val error = assertThrows<TechnicalError> {
                engine.startQuest(state, scenario.name, 2, questToStart.secret, freshLocation(questToStart))
            }
            assertThat(error.message, equalTo("Not good: Bad cookie quest"))
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
     * Completing the second quest is a special case. Because the second quest is started
     * at home (this is the first quest that gives coordinates to the field), it does not
     * check for the deadline.
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

        @Test
        fun `Fail if state's scenario does not match params`() {
            // Given: State has bad scenario
            val state = validStateToComplete().copy(scenario = "not correct")

            // When: Starting the quest
            // Then: Error about bad scenario
            val error = assertThrows<TechnicalError> {
                engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, freshLocation(questToComplete))
            }
            assertThat(error.message, equalTo("Not good: scenario completion"))
        }

        @Test
        fun `Fail if state's quest is smaller then params`() {
            // Given: State has different quest
            val state = validStateToComplete().copy(currentQuest = questToComplete.order - 1)

            // When: Starting the quest
            // Then: Error about bad scenario
            val error = assertThrows<TechnicalError> {
                engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, freshLocation(questToComplete))
            }
            assertThat(error.message, equalTo("Not good: quest matching"))
        }

        @Test
        fun `success`() {
            // Given: Valid state to complete this quest
            val state = validStateToComplete()

            // When: Starting the quest
            val viewModel = engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, freshLocation(questToComplete))

            // Then: Success page is shown
            assertThat(viewModel.modelAndView.view, equalTo("quests/testing_1_success"))

            fail("Should check the whole webaction")
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
        val questToComplete = scenario.quests[4]

        @Test
        fun `Fail if not completed in time`() {
            // Given: Deadline has expired
            val state = validStateToComplete()
                .copy(questDeadline = timeProvider.now().minusMinutes(1))

            // When: Starting the quest
            val viewModel = engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, freshLocation(questToComplete))

            // Then: Failure page is shown
            assertThat(viewModel.modelAndView.view, equalTo(questToComplete.failurePage))

            fail("Should check the whole webaction")
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
            assertThat(error.message, equalTo("Not good: scenario completion"))
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

        @Test
        fun `success`() {
            // Given: Valid state to complete this quest
            val state = validStateToComplete()

            // When: Completing the quest
            val viewModel = engine.complete(state, scenario.name, questToComplete.order, questToComplete.secret, freshLocation(questToComplete))

            // Then: Success page is shown
            assertThat(viewModel.modelAndView.view, equalTo(questToComplete.successPage))

            fail("Should check the whole webaction")
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

    fun assertScenarioRestartAction(existingState: State, scenario: Scenario, action: WebAction) {
        // Then: State is reset to the intro quest
        assertThat(
            action.state,
            equalTo(
                State(
                    scenario = scenario.name,
                    currentQuest = 0,
                    questDeadline = null,
                    questStarted = timeProvider.now(),
                    userId = existingState.userId,
                    scenarioRestartCount =
                        if (existingState.scenario == scenario.name)
                            existingState.scenarioRestartCount + 1
                        else 0
                )
            )
        )

        // And: Go through location reading back to complete, now with proper state
        assertThat(
            action.modelAndView as LocationReadingViewModel,
            equalTo(
                LocationReadingViewModel("/engine/complete/${scenario.name}/0/${scenario.quests[0].secret}", null, null)
            )
        )
    }
}
