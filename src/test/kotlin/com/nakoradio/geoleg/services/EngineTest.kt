package com.nakoradio.geoleg.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.nakoradio.geoleg.model.LocalizedMessage
import com.nakoradio.geoleg.model.LocationReading
import com.nakoradio.geoleg.model.Quest
import com.nakoradio.geoleg.model.Scenario
import com.nakoradio.geoleg.model.State
import com.nakoradio.geoleg.model.TechnicalError
import com.nakoradio.geoleg.model.WebAction
import com.nakoradio.geoleg.utils.Time
import java.lang.IllegalStateException
import java.time.OffsetDateTime
import java.util.UUID
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class EngineTest {

    private val jsonmappper = ObjectMapper().registerModule(KotlinModule())
    private val loader = ScenarioLoader(jsonmappper)

    var now: OffsetDateTime = OffsetDateTime.now()
    private val timeProvider = object : Time() {
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

    @BeforeEach
    fun beforeEach() {
        now = OffsetDateTime.now()
    }

    @Test
    fun `Happy flow entire scenario`() {
        // Given: Testing the real scenario from config
        val scenario = loader.table.scenarios[0]

        // When: Scanning the first QR (will point to scenario init)
        var action = engine.initScenario(null, scenario.name)

        // Then: Scenario is initialized
        assertThat(
            action,
            equalTo(
                // User directed to complete first quest
                WebAction(
                    LocationReadingViewModel("/engine/complete/ancient-blood/0/6a5fc6c0f8ec", null, null),
                    // State initialized for scenario
                    State(
                        scenario.name, 0, timeProvider.now().plusYears(10), timeProvider.now(), null,
                        action.state!!.userId, 0, timeProvider.now()
                    )
                )
            )
        )
        var state = action.state!!
        tick()

        // User redirected to complete first quest, location does not matter
        action = engine.complete(state, scenario.name, 0, scenario.quests[0].secret, locationSomewhere().asString())

        // Then: Quest completed
        assertQuestCompleted(action, state)
        state = action.state!!
        tick()

        // When: User starts next quest, this is the quest to the sign, again location where starting this does not matter
        action = startQuest(state, loader.nextQuest(state), locationSomewhere())

        // Then: Quest started
        assertQuestStarted(action, state)
        state = action.state!!
        tick()

        // When: User scans the first QR on the field
        action = scanTargetQR(state)

        // Then: Quest completed
        assertQuestCompleted(action, state)
        state = action.state!!
        tick()

        /**
         * SILTA quest
         */
        state = startAndCompleteNextQuest(state)

        /**
         * KUUSI quest
         */
        state = startAndCompleteNextQuest(state)

        /**
         * KATAJA quest
         */
        state = startAndCompleteNextQuest(state)

        /**
         * KELO quest
         */
        state = startAndCompleteNextQuest(state)

        /**
         * MASTO quest
         */
        state = startAndCompleteNextQuest(state)

        /**
         * HELIKOPTERI quest
         */
        state = startAndCompleteNextQuest(state)

        /**
         * KATAJA quest (again)
         */
        state = startAndCompleteNextQuest(state)

        /**
         * SIILO quest
         */
        // When: User starts the quest
        action = startNextQuest(state)

        // Then: Quest started
        assertQuestStarted(action, state)
        state = action.state!!
        tick()

        // When: User completes the quest
        action = scanTargetQR(state)

        // Then: Quest completed
        assertThat(
            action,
            equalTo(
                WebAction(
                    // Then: Show quest success view
                    ScenarioEndViewModel(loader.currentQuest(state).successPage),
                    // And: Quest completion marked to state
                    state.copy(questCompleted = timeProvider.now())
                )
            )
        )
        tick()
    }

    private fun startAndCompleteNextQuest(currentState: State): State {
        var state = currentState

        // When: User starts the quest
        var action = startNextQuest(state)

        // Then: Quest started
        assertQuestStarted(action, state)
        state = action.state!!
        tick()

        // When: User completes the quest
        action = scanTargetQR(state)

        // Then: Quest completed
        assertQuestCompleted(action, state)
        state = action.state!!
        tick()

        return state
    }

    private fun tick() {
        now = now.plusMinutes(5)
    }

    /**
     * Flow for the first quest is:
     * 1) User scans the QR on Geocaching.com
     * 2) On a redirect to `/engine/init/:scenario` state cookie is set for `currentQuest=0`
     * 3) User is automatically redirected to complete the first quest (state not changed)
     *
     * So first quest is the one started and automatically completed by scanning the QR code on the
     * geocaching.com site so this is a bit different from your normal `running a quest` state.
     *
     * These tests are for the case where user has visited the init page (has `currentQuest=0` state)
     * and is directed to complete the first quest. After first quest completion page shows the background
     * story for the scenario and  "Go" button to start the second quest.
     *
     */
    @Nested
    inner class `Running the first quest` {

        private val scenario = loader.table.scenarios[1]
        private val currentQuest = scenario.quests[0]

        /**
         * User has visited the `/engine/init/:scenario` and is now being redirected to complete
         * the first quest.
         */
        @Nested
        inner class `Quest ongoing` {

            private val currentState = stateForRunningQuest(scenario, currentQuest)

            /**
             * Init scenario will redirect to this action to automatically complete
             * the intro after location read.
             */
            @Test
            fun `Complete the quest does not check location`() {
                // When: Intro quest is completed with random location
                val action = engine.complete(
                    currentState, scenario.name, 0, currentQuest.secret,
                    locationSomewhere().asString()
                )

                // Then: Show success page
                assertQuestCompleted(action, currentState)
            }

            @Test
            fun `Rescanning the QR code will reinitialize the scenario`() {
                // When: Scanning the QR code again, i.e. calling the scenario init
                val outcome = engine.initScenario(currentState, scenario.name)

                // Then: Scenario is restarted
                assertScenarioRestartAction(currentState, scenario, outcome)
            }

            /**
             * Trying to complete any other later quest than second one should restart the scenario.
             * Special handling for the first quest only. No real reason apart for the second quest
             * , see above test case but let's make it work the same for other later quests also.
             *
             * User is scanning the QR code of a later quest.
             */
            @Test
            fun `Scanning QR of a later quest should restart the scenario`() {
                // When: Completing later quest
                val outcome = scanQR(currentState, scenario, scenario.quests[3])

                // Then: Restart the scenario
                assertScenarioRestartAction(currentState, scenario, outcome)
            }

            /**
             * Start url action for first quest is never called. Should never happen so is ok to just fail.
             */
            @Test
            fun `Calling start URL of first quest will give error`() {
                assertThrows<IllegalStateException> {
                    engine.startQuest(
                        currentState,
                        scenario.name,
                        0,
                        scenario.quests[0].secret,
                        LocationReading(2.2, 1.1, timeProvider.now()).asString()
                    )
                }
            }

            @Test
            fun `Calling start URL of a later quest will continue countdown`() {
                // Trying to start a later quest
                val outcome = engine.startQuest(
                    currentState,
                    scenario.name,
                    3,
                    scenario.quests[3].secret,
                    freshLocation(scenario.quests[3])
                )
                assertCountdownContinues(outcome, currentState)
            }
        }

        /**
         * User has visited the automatic redirection to `/engine/complete/...` and completed the
         * quest. He is currently shown the quest success page.
         */
        @Nested
        inner class `Quest completed` {

            private val currentState = stateForRunningQuest(scenario, currentQuest).copy(questCompleted = timeProvider.now().minusSeconds(13))

            /**
             * User is currently on the first quest complete page (as it was automatically
             * completed). Reloading page should complete the quest again.
             */
            @Test
            fun `Reloading page completes current quest again`() {
                // When: Completing current quest again
                val outcome = engine.complete(
                    currentState, scenario.name, currentQuest.order, currentQuest.secret,
                    // Any location will do
                    // TODO: Why cannot this just take actual location instead. Do the string parsing on controller.
                    locationSomewhere().asString()
                )

                // Then: Quest success page shown
                assertQuestSuccessViewShown(outcome, currentState)
            }

            /**
             * Second quest (target location is the first QR on the field) can be started anywhere,
             * as the "Go" button is shown after the autocompleting first quest. Most likely second
             * quest is started at home.
             */
            @Test
            fun `Clicking GO to start second quest does not require valid location`() {
                // When: Starting the second quest with random location
                val secondQuest = nextQuest(scenario, currentQuest)
                val outcome = startQuest(
                    currentState, secondQuest, locationSomewhere()
                )

                // Then: Second quest successfully started
                assertQuestStarted(outcome, currentState, secondQuest)
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
                val result = scanQR(currentState, scenario, nextQuest(scenario, currentQuest))

                // Then: Restart the scenario
                assertScenarioRestartAction(currentState, scenario, result)
            }
        }
    }

    /**
     *
     * First quest is the introduction quest that is automatically instantly completed. Second
     * quest is thus also started right away at home. This has the following implications
     * for the second quest processing logic:
     * - Second quest has unlimited time to complete
     *
     * For these tests, user has just started second quest by clicking the "GO" and is on the
     * countdown page (`/engine/start/:scenario/1/:secret/:location`)
     */
    @Nested
    inner class `Second quest` {

        private val scenario = loader.table.scenarios[1]
        private val currentQuest = scenario.quests[1]

        @Nested
        inner class `Quest ongoing` {

            // Second quest has unlimited time to complete
            private val currentState = stateForRunningQuest(scenario, currentQuest)

            @Nested
            inner class `Restarting current quest (template)` :
                BaseTestClassForRestartingCurrentQuest(
                    currentState,
                    { outcome -> assertCountdownContinues(outcome, currentState) }
                )

            @Test
            fun `Second quest has no DL`() {
                assertThat(currentState.questDeadline, nullValue())
            }

            @Test
            fun `Scanning QR completes the quest`() {
                //  When: Completing the quest
                val outcome = scanQR(currentState, scenario, currentQuest)

                // Then: Quest completed
                assertQuestCompleted(outcome, currentState)
            }

            @Test
            fun `Scanning QR past deadline`() {
                // Given: DL is long gone already
                now = OffsetDateTime.now().plusYears(100)

                // When: Scanning the QR
                val outcome = scanQR(currentState, scenario, currentQuest)

                // Then: Quest completed, second quest has no deadline
                assertQuestCompleted(outcome, currentState)
            }

            @Test
            fun `Scanning QR should fail if location is not fresh`() {
                // And: Old location reading
                val expiredLocationRead =
                    locationFor(currentQuest).copy(createdAt = timeProvider.now().minusDays(200))

                // When: Scanning the QR code
                // Then: Error about expired location
                val error = assertThrows<TechnicalError> {
                    scanQR(currentState, scenario, currentQuest, expiredLocationRead)
                }
                assertThat(error.message, equalTo("Could not get fresh location, try rescanning the QR"))
            }

            @Test
            fun `Scanning QR should fail if location is not close to quest location`() {
                // Given: Location far from quest location
                var location = locationSomewhere()
                // When: Scanning the QR
                // Then: Error about not being close to target location
                val error = assertThrows<TechnicalError> {
                    scanQR(currentState, scenario, currentQuest, location)
                }
                assertThat(error.message, equalTo("Coordinates not matching QR location, try rescanning the QR"))
            }

            @Test
            fun `Scanning later quest's QR code should continue countdown`() {
                // When: Scanning QR code of later quest
                val outcome = scanQR(currentState, scenario, scenario.quests[4])

                // Then: Countdown continues
                assertCountdownContinues(outcome, currentState)
            }

            @Test
            fun `Scanning later quest's QR code with bad location should continue countdown`() {
                // When: Scanning QR code of later quest
                val outcome =
                    scanQR(currentState, scenario, scenario.quests[4], locationSomewhere())

                // Then: Countdown continues
                assertCountdownContinues(outcome, currentState)
            }

            @Test
            fun `Starting next quest before completing this keeps countdown running`() {
                // When: Starting the next quest
                val outcome = startQuest(currentState, loader.nextQuest(currentState))

                // Then: Countdown keeps running
                assertCountdownContinues(outcome, currentState)
            }

            @Test
            fun `Starting next quest with wrong location keeps running the countdown`() {
                // When: Starting the next quest with too far location
                val action =
                    startQuest(currentState, loader.nextQuest(currentState), locationSomewhere())

                // Then: Countdown keeps on running
                assertCountdownContinues(action, currentState)
            }

            @Test
            fun `Starting next quest with stale location before completing this keeps countdown running`() {
                val nextQuest = loader.nextQuest(currentState)

                // When: Starting the next quest before completing this
                var action = startQuest(
                    currentState,
                    nextQuest,
                    locationFor(nextQuest).copy(createdAt = timeProvider.now().minusYears(1))
                )

                // Then: Countdown continues (even with location reading expired)
                assertCountdownContinues(action, currentState)
            }

            @Test
            fun `Starting later quest should keep on running countdown`() {
                // When: Trying to start upcoming quest
                val outcome = startQuest(currentState, scenario.quests[4])

                // Then: Countdown continues
                assertCountdownContinues(outcome, currentState)
            }

            @Test
            fun `Starting later quest (with bad location) should keep on running countdown`() {
                // When: Trying to start upcoming quest with bad location
                val outcome =
                    startQuest(currentState, scenario.quests[4], locationSomewhere())

                // Then: Countdown continues
                assertCountdownContinues(outcome, currentState)
            }
        }
    }

    @Nested
    inner class `After completing second quest` {

        private val scenario = loader.table.scenarios[1]
        private val currentQuest = scenario.quests[1]

        // Second quest has unlimited time to complete
        private val currentState = stateForRunningQuest(scenario, currentQuest).copy(questCompleted = timeProvider.now())

        @Test
        fun `Starting next quest fails if expired location`() {
            val nextQuest = nextQuest(scenario, currentQuest)

            // When: Starting the next quest with stale location
            // Then: Will give location expired error
            val error = assertThrows<TechnicalError> {
                startQuest(
                    currentState,
                    nextQuest,
                    locationFor(nextQuest).copy(createdAt = timeProvider.now().minusYears(1))
                )
            }
            assertThat(error.message, equalTo("Could not get fresh location, try rescanning the QR"))
        }

        @Test
        fun `Starting next quest fails if wrong location`() {
            val nextQuest = nextQuest(scenario, currentQuest)

            // When: Starting the next quest with too far location
            // Then: Will give GPS error
            val error = assertThrows<TechnicalError> {
                startQuest(currentState, nextQuest, locationSomewhere())
            }
            assertThat(error.message, equalTo("Coordinates not matching QR location, try rescanning the QR"))
        }

        @Test
        fun `Starting next quest successful`() {
            // When: Starting the next quest
            val nextQuest = nextQuest(scenario, currentQuest)
            val outcome = startQuest(currentState, nextQuest)

            // Then: Quest is started
            assertQuestStarted(outcome, currentState, nextQuest)
        }
    }

    /**
     * User has state for currently doing the Nth quest
     */
    @Nested
    inner class `Nth quest` {

        private val scenario = loader.table.scenarios[1]
        private val currentQuest = scenario.quests[5]

        /**
         * Quest has been started but not yet completed
         */
        @Nested
        inner class `Quest ongoing` {
            private val currentState = stateForRunningQuest(scenario, currentQuest)

            @Nested
            inner class `Quest DL has expired` {
                // Quest DL has expired
                private val currentState = stateForRunningQuest(scenario, currentQuest)
                    .copy(questDeadline = timeProvider.now().minusYears(1))

                @Nested
                inner class `Completing quest should show timeout failure (template)` : BaseTestClassForCompletingCurrentQuest(
                    currentState,
                    { outcome -> assertQuestFailed(outcome, currentState) }
                )

                @Nested
                inner class `Completing out of order quests should show failure (template)` :
                    BaseTestClassForCompletingOutOfOrderQuests(
                        currentState,
                        { outcome -> assertQuestFailed(outcome, currentState) }
                    )

                @Nested
                inner class `Restarting current quest should show failure (template)` : BaseTestClassForRestartingCurrentQuest(
                    currentState,
                    { outcome -> assertQuestFailed(outcome, currentState) }
                )

                @Nested
                inner class `Starting another quest should show failure (template)` :
                    BaseTestClassForStartingAnotherQuest(
                        currentState,
                        { o -> assertQuestFailed(o, currentState) }
                    )
            }

            @Nested
            inner class `Quest DL not exceeded` {

                @Nested
                inner class `Should complete current quest (template)` : BaseTestClassForCompletingCurrentQuest(
                    currentState,
                    { outcome -> assertQuestCompleted(outcome, currentState) }
                )

                @Nested
                inner class `Should continue countdown (template)` : BaseTestClassForStartingAnotherQuest(
                    currentState,
                    { outcome -> assertCountdownContinues(outcome, currentState) }
                )

                @Nested
                inner class `Should continue countdown also (template)` :
                    BaseTestClassForRestartingCurrentQuest(currentState, { outcome -> assertCountdownContinues(outcome, currentState) })

                @Nested
                inner class `Completing out of order quests (template)` :
                    BaseTestClassForCompletingOutOfOrderQuests(
                        currentState,
                        { outcome -> assertCountdownContinues(outcome, currentState) }
                    )
            }
        }

        /**
         * User has completed this quest successfully by scanning the QR. Next quest has not yet
         * been started.
         */
        @Nested
        inner class `Quest completed` {
            private val currentState =
                stateForRunningQuest(scenario, currentQuest)
                    .copy(
                        questCompleted = timeProvider.now().minusSeconds(13)
                    )

            @Nested
            inner class `Quest DL has expired` {
                @Test
                fun `MISSING TESTS`() {
                }
            }

            @Nested
            inner class `Quest DL not exceeded` {

                @Nested
                inner class `Recompleting current quest (template)` : BaseTestClassForReCompletingCurrentQuest(currentState)

                @Nested
                inner class `Starting next quest` {

                    @Test
                    fun `Successfully start next quest`() {
                        // When: Starting next quest
                        val action = startNextQuest(currentState)

                        // Then: Countdown for the next quest
                        assertQuestStarted(action, currentState)
                    }

                    /**
                     * Important scenario to cover. After completing the quest, user has all the time
                     * they need to read the next story before clicking go. It is very likely that
                     * current quest's DL expires while reading the story. Starting the next quest
                     * should be possible nevertheless.
                     */
                    @Test
                    fun `Successfully start next quest after this quest's DL expired`() {
                        val nextQuest = nextQuest(scenario, currentQuest)

                        // Given: DL for current quest has expired (after successfully completing it)
                        val state = currentState.copy(
                            questDeadline = timeProvider.now().minusSeconds(10)
                        )

                        // When: Starting next quest
                        val action = startQuest(state, nextQuest)

                        // Then: Quest is started
                        assertQuestStarted(action, currentState)
                    }

                    @Test
                    fun `fail if location reading is not fresh enough`() {
                        // Given: Location that is old
                        val location = LocationReading(
                            currentQuest.location!!.lat, currentQuest.location!!.lon,
                            timeProvider.now().minusMinutes(2)
                        )

                        // When: Starting the quest
                        val error = assertThrows<TechnicalError> {
                            startQuest(currentState, loader.nextQuest(currentState), location)
                        }
                        assertThat(error.message, equalTo("Could not get fresh location, try rescanning the QR"))
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
                        val location = LocationReading(
                            // About 200 meters off
                            currentQuest.location!!.lat + 0.002, currentQuest.location!!.lon,
                            timeProvider.now()
                        )

                        // When: Starting the quest
                        val error = assertThrows<TechnicalError> {
                            startQuest(currentState, loader.nextQuest(currentState), location)
                        }

                        // Then: Error about location
                        assertThat(error.message, equalTo("Coordinates not matching QR location, try rescanning the QR"))
                    }

                    @Test
                    fun `fail if state's scenario is different from param`() {
                        // And: State with different scenario
                        val state = State.empty(timeProvider)
                            .copy(scenario = "not correct")

                        // When: Starting the quest
                        val error = assertThrows<TechnicalError> {
                            engine.startQuest(
                                state,
                                scenario.name,
                                state.currentQuest + 1,
                                loader.nextQuest(currentState).secret,
                                freshLocation(loader.nextQuest(currentState))
                            )
                        }
                        assertThat(error.message, equalTo("Not good: Bad cookie scenario"))
                    }

                    /**
                     * If we try to start current quest again, we should just keep on running it without
                     * changing anything. This could happen by reloading the browser window.
                     *
                     */
                    @Test
                    fun `Trying to start quest again keeps it running`() {
                        // When: Starting this quest again
                        val action = startQuest(currentState, currentQuest)

                        // Then: Just continue countdown
                        assertCountdownContinues(action, currentState)
                    }
                }
            }
        }
    }

    @Nested
    inner class `Quest without coordinates` {

        @Nested
        inner class `Running` {
            private val scenario = loader.table.scenarios[0]
            private val questWithoutCoordinates = scenario.quests[8]
            private val currentState = stateForRunningQuest(scenario, questWithoutCoordinates)

            @Test
            fun `Asserting test preconditions`() {
                // Just making sure that the quest actually does not have coordinates
                assertThat(questWithoutCoordinates.location, nullValue())
            }

            @Test
            fun `Location reading page, should not include coordinates`() {
                // When initiating complete
                val outcome = engine.initComplete(scenario.name, questWithoutCoordinates.order, questWithoutCoordinates.secret)
                // Location reading should not include quest coordinates
                assertThat((outcome as LocationReadingViewModel).lat, nullValue())
            }

            @Nested
            inner class `Should continue countdown (template)` : BaseTestClassForRestartingCurrentQuest(
                currentState, { outcome -> assertCountdownContinues(outcome, currentState) }
            )
        }
    }

    @Nested
    inner class `Quests that share QR codes` {

        // Quest 8 is reusing quest 4 QR
        private val scenario = loader.table.scenarios[0]
        private val laterQuestWithSharedQR = scenario.quests[8]
        private val earlierQuestWithSharedQR = scenario.quests[4]

        @Nested
        inner class `Running the later quest` {
            private val currentState = stateForRunningQuest(scenario, laterQuestWithSharedQR)

            @Test
            fun `Can be completed with the shared QR`() {
                // When: Completing the quest, with the shared QR of quest 2. Values come from URL params.
                val outcome = scanQR(currentState, scenario, earlierQuestWithSharedQR)

                // Then: Quest successfully completed
                assertQuestCompleted(outcome, currentState)
            }

            @Test
            fun `Uses the location of the earlier QR quest`() {
                // The later quest does not have location set
                assertThat(laterQuestWithSharedQR.location, nullValue())

                // Earlier quest has location
                assertThat(earlierQuestWithSharedQR.location, not(nullValue()))

                // Scanning QR will fail if not valid location
                assertThrows<TechnicalError> {
                    scanQR(currentState, scenario, earlierQuestWithSharedQR, locationSomewhere())
                }
            }
        }

        @Nested
        inner class `Starting the later quest sharing the QR` {

            // User has completed previous quest and is about to start the later quest that
            // uses the shared QR
            private val currentState =
                stateForRunningQuest(scenario, previousQuest(scenario, laterQuestWithSharedQR))
                    .copy(questCompleted = timeProvider.now().minusMinutes(1))

            /**
             * Small twist, user does not get the coordinates of the later quest to the shared
             * location. They must identify it by the description given on the story.
             */
            @Test
            fun `Countdown page should not show coordinates but message`() {
                val outcome = startNextQuest(currentState)

                assertThat(
                    outcome,
                    equalTo(
                        WebAction(
                            // Then: Show countdown view
                            CountdownViewModel(
                                laterQuestWithSharedQR.countdownPage,
                                timeProvider.now().toEpochSecond(),
                                laterQuestWithSharedQR.countdown?.let {
                                    timeProvider.now().plusSeconds(it).toEpochSecond()
                                },
                                laterQuestWithSharedQR.fictionalCountdown,
                                null, null,
                                LocalizedMessage("Palaa puhelimelle")
                            ),
                            // And: State is updated for the quest to start
                            updatedStateForNewlyStartedQuest(currentState, laterQuestWithSharedQR)
                        )
                    )
                )
            }
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
            val action = engine.initScenario(null, scenario.name)
            // Then: State set to scenario start
            assertThat(
                action.state,
                equalTo(
                    State(
                        scenario = scenario.name,
                        currentQuest = 0,
                        questStarted = timeProvider.now(),
                        questCompleted = null,
                        questDeadline = timeProvider.now().plusYears(10),
                        userId = action.state!!.userId,
                        scenarioRestartCount = 0,
                        scenarioStarted = timeProvider.now()
                    )
                )
            )

            // And: Quest complete called next after location location got
            assertThat(
                action.modelAndView as LocationReadingViewModel,
                equalTo(
                    LocationReadingViewModel(
                        "/engine/complete/${scenario.name}/0/${scenario.quests[0].secret}",
                        null,
                        null
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

        @Test
        fun `Scanning second quest's QR code`() {
            // When: Completing second quest without state
            val action = scanQR(null, scenario, scenario.quests[1])

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

    @Nested
    inner class `All the hacky stuff`() {

        val scenario = loader.table.scenarios[0]

        @Test
        fun `trying to complete first quest of non existing scenario`() {
            // Given: User has state set for completing the intro quest
            // When: Completing intro for non existing scenario
            assertThrows<TechnicalError> {
                engine.complete(
                    stateForRunningQuest(scenario, scenario.quests[0]),
                    "other scenario",
                    0,
                    scenario.quests[0].secret,
                    locationSomewhere().asString()
                )
            }
        }

        /**
         * Use is only allowed to successfully scan the first quest's QR with state for a different
         * scenario. For any other quest, we should show the bad scenario error page that will have
         * a link to reset the scenario.
         */
        @Test
        fun `Scanning any on field QR of another scenario restart the scenario of the scanned QR`() {
            // Given: User has the wrong scenario
            val state = stateForRunningQuest(scenario, scenario.quests[3]).copy(
                scenario = "wrong scenario"
            )

            // When: Trying to scan any QR of another quest
            val result = scanQR(state, scenario, scenario.quests[3])

            // Then: Restart the scenario
            assertScenarioRestartAction(state, scenario, result)
        }

        @Test
        fun `Failure when starting quest of another scenario`() {
            // Given: State that has different scenario
            val state = stateForRunningQuest(scenario, scenario.quests[2]).copy(
                scenario = "this is not correct scenario"
            )

            // When: Starting quest
            // Then: Error on quest
            val error = assertThrows<TechnicalError> {
                val questToStart = scenario.quests[2]
                engine.startQuest(
                    state,
                    scenario.name,
                    2,
                    questToStart.secret,
                    freshLocation(questToStart)
                )
            }
            assertThat(error.message, equalTo("Not good: Bad cookie scenario"))
        }

        @Test
        fun `Calling complete URL with malformed location string`() {
            // When: Trying to scan QR with malformed location string
            // Then: Error given
            val error = assertThrows<TechnicalError> {
                val currentState = stateForRunningQuest(scenario, scenario.quests[3])
                engine.complete(
                    currentState,
                    scenario.name,
                    3,
                    scenario.quests[3].secret,
                    "badLocation"
                )
            }
            assertThat(error.message, equalTo("Alas, something went wrong"))
        }

        @Test
        fun `Calling complete URL with bad secret`() {
            // When: Trying to complete quest with bad secret
            // Then: Error
            val error = assertThrows<TechnicalError> {
                val currentState = stateForRunningQuest(scenario, scenario.quests[3])
                engine.complete(
                    currentState,
                    scenario.name,
                    3,
                    "bad secret",
                    freshLocation(scenario.quests[3])
                )
            }
            assertThat(error.message, equalTo("No such quest secret for you my friend"))
        }

        @Test
        fun `Start for the first quest should never be called`() {
            val state = stateForRunningQuest(scenario, loader.questFor(scenario.name, 0))
            assertThrows<IllegalStateException> {
                startQuest(state, scenario.quests[0])
            }
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
            val state = stateForRunningQuest(scenario, currentQuest)

            // When: Scanning the previous QR code, so basically trying to complete an earlier quest
            // Then: No state is returned, only view
            val viewModel = scanQR(state, scenario, previousQuest)

            // Then: Countdown view shown for the already started quest
            assertCountdownContinues(viewModel, state)
        }
    }

    @Nested
    inner class `User has state for unknown scenario` {

        // Given: State has unknown scenario (only possible if scenarios renamed or removed)
        val currentState =
            State(
                "unknown",
                0,
                null,
                timeProvider.now().minusMinutes(3),
                null,
                UUID.randomUUID(),
                2,
                timeProvider.now().minusMinutes(10)
            )

        @Test
        fun `trying to complete first quest of actual scenario`() {
            // When: Executing the intro's `complete` action
            val scenario = loader.table.scenarios[0]
            val action = engine.complete(
                currentState,
                scenario.name, 0, scenario.quests[0].secret, locationSomewhere().asString()
            )

            // Then: Scenario restarted
            assertScenarioRestartAction(currentState, scenario, action)
        }
    }

    @Nested
    inner class `User has state for different scenario` {
        val currentScenario = loader.table.scenarios[0]
        val currentState = stateForRunningQuest(currentScenario, currentScenario.quests[3])
        val anotherScenario = loader.table.scenarios[1]

        @Test
        fun `Scanning QR of another scenario`() {
            // When: Scanning  QR of a quest in another scenario
            val questOfAnotherScenario = anotherScenario.quests[3]
            val outcome = engine.complete(
                currentState,
                anotherScenario.name,
                questOfAnotherScenario.order,
                questOfAnotherScenario.secret,
                freshLocation(questOfAnotherScenario)
            )
            // Then: The other scenario is started
            assertScenarioRestartAction(currentState, anotherScenario, outcome)
        }

        @Test
        fun `trying to complete first quest of another scenario`() {
            // When: Completing first quest of another scenario
            val action = engine.complete(
                currentState,
                anotherScenario.name, 0, anotherScenario.quests[0].secret,
                locationSomewhere().asString()
            )

            // Then: Intro quest is successfully restarted
            assertScenarioRestartAction(currentState, anotherScenario, action)
        }

        @Test
        fun `trying to complete another scenario's intro with this scenario's secret`() {
            // Given: User has state set for completing the intro quest of current scenario
            val state =
                stateForRunningQuest(currentScenario, currentScenario.quests[0])

            // When: Trying to complete a different scenario with this scenario's secret
            val scenarioNameOfAnotherExistingScenario = anotherScenario.name
            val result = engine.complete(
                state, scenarioNameOfAnotherExistingScenario, 0,
                currentScenario.quests[0].secret, locationSomewhere().asString()
            )

            // Then: Restarting the other scenario scenario
            assertScenarioRestartAction(state, anotherScenario, result)
        }
    }

    /**
     * Loading (and reloading) the `/engine/init/$scenarioName` action
     */
    @Nested
    inner class `Scenario initialization` {

        @Test
        fun `init scenario will reset the existing state for scenario`() {
            val scenario = loader.table.scenarios[0]

            // Given: State with old dates and later quest order
            val existingState = stateForRunningQuest(scenario, scenario.quests[4])
                .copy(
                    questStarted = timeProvider.now().minusDays(39),
                    questDeadline = timeProvider.now().minusDays(20)
                )

            // When: scenario is initialized
            val (viewModel, newState) = engine.initScenario(
                existingState,
                scenario.name
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
                        scenarioRestartCount = existingState.scenarioRestartCount + 1,
                        questStarted = timeProvider.now(),
                        // And: Scenario start is reset
                        scenarioStarted = timeProvider.now()
                    )
                )
            )
        }

        @Test
        fun `init scenario will reset the state of another scenario`() {
            val scenario = loader.table.scenarios[0]

            // Given: State for another scenario
            val existingState = stateForRunningQuest(scenario, scenario.quests[0])
                .copy(scenario = "the other scenario")

            // When: scenario is initialized
            val (url, newState) = engine.initScenario(
                existingState,
                scenario.name
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
                        questStarted = timeProvider.now(),
                        scenarioStarted = timeProvider.now()
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
                scenario.name
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
    }

    @Nested
    inner class `Running last quest` {

        val scenario = loader.table.scenarios[1]
        val questToComplete = scenario.quests.last()
        val state = stateForRunningQuest(scenario, questToComplete)

        @Test
        fun `Scenario success when scanning this QR`() {
            // When: Scanning the last QR
            val action = scanQR(state, scenario, questToComplete)

            // Then: Success page is shown
            assertThat(
                action,
                equalTo(
                    WebAction(
                        ScenarioEndViewModel("quests/testing_7_success"),
                        state.copy(questCompleted = timeProvider.now())
                    )
                )
            )
        }

        @Test
        fun `Scenario success if scanning any other QR after last quest completed even if DL passed`() {
            // Given: Last quest completed
            val newState = state.copy(questCompleted = timeProvider.now())

            // And: Deadline for last quest passed
            now = timeProvider.now().plusDays(10)

            // When: Scanning some QR
            val action = scanQR(newState, scenario, loader.previousQuest(newState))

            // Then: Success page is shown
            assertThat(
                action,
                equalTo(
                    WebAction(
                        ScenarioEndViewModel("quests/testing_7_success"),
                        newState
                    )
                )
            )
        }
    }

    private fun freshLocation(questToStart: Quest) =
        if (questToStart.location != null)
            LocationReading(
                questToStart.location!!.lat, questToStart.location!!.lon,
                timeProvider.now()
            ).asString() else locationSomewhere().asString()

    fun assertScenarioRestartAction(existingState: State?, action: WebAction) {
        return assertScenarioRestartAction(existingState, loader.findScenario(existingState!!.scenario), action)
    }

    fun assertScenarioRestartAction(existingState: State?, scenario: Scenario, action: WebAction) {
        assertThat(
            action,
            equalTo(
                WebAction(
                    // Then: Go through location reading back to completing the first quest
                    LocationReadingViewModel(
                        "/engine/complete/${scenario.name}/0/${scenario.quests[0].secret}",
                        null,
                        null
                    ),
                    // Then: State is reset to the intro quest
                    State(
                        scenario = scenario.name,
                        currentQuest = 0,
                        questDeadline = timeProvider.now().plusYears(10),
                        questStarted = timeProvider.now(),
                        questCompleted = null,
                        userId = existingState?.userId ?: action.state!!.userId,
                        scenarioRestartCount =
                            if (existingState?.scenario == scenario.name) {
                                existingState.scenarioRestartCount + 1
                            } else {
                                0
                            },
                        scenarioStarted = timeProvider.now()
                    )
                )
            )
        )
    }

    fun assertQuestFailed(
        outcome: WebAction,
        currentState: State
    ) {
        assertQuestFailed(outcome, currentState, loader.currentQuest(currentState))
    }
    fun assertQuestFailed(
        outcome: WebAction,
        currentState: State,
        questToComplete: Quest
    ) {
        assertThat(
            outcome,
            equalTo(
                WebAction(
                    // Then: Show quest failure view
                    QuestFailedViewModel(questToComplete.failurePage, currentState),
                    // And: State is not changing
                    currentState
                )
            )
        )
    }

    /**
     * No change to state, just show the success
     */
    fun assertQuestSuccessViewShown(outcome: WebAction, currentState: State) {
        // Make sure quest is not already completed
        assertThat(currentState.questCompleted, notNullValue())
        assertThat(
            outcome,
            equalTo(
                WebAction(
                    // Then: Show quest success view
                    QuestEndViewModel(
                        loader.currentQuest(currentState).successPage,
                        loader.nextQuest(currentState),
                        loader.currentQuest(currentState)
                    ),
                    // And: State not changed
                    currentState
                )
            )
        )
    }
    fun assertQuestCompleted(outcome: WebAction, currentState: State) {
        // Make sure quest is not already completed
        assertThat(currentState.questCompleted, nullValue())
        assertThat(
            outcome,
            equalTo(
                WebAction(
                    // Then: Show quest success view
                    QuestEndViewModel(
                        loader.currentQuest(currentState).successPage,
                        loader.nextQuest(currentState),
                        loader.currentQuest(currentState)
                    ),
                    // And: Quest completion marked to state
                    currentState.copy(questCompleted = timeProvider.now())
                )
            )
        )
    }
    fun assertQuestStarted(outcome: WebAction, currentState: State) {
        assertQuestStarted(outcome, currentState, loader.nextQuest(currentState))
    }

    fun assertQuestStarted(outcome: WebAction, currentState: State, questToStart: Quest) {
        assertThat(
            outcome,
            equalTo(
                WebAction(
                    // Then: Show countdown view
                    CountdownViewModel(
                        questToStart.countdownPage,
                        timeProvider.now().toEpochSecond(),
                        questToStart.countdown?.let {
                            timeProvider.now().plusSeconds(it).toEpochSecond()
                        },
                        questToStart.fictionalCountdown,
                        questToStart.location?.lat, questToStart.location?.lon,
                        questToStart.message
                    ),
                    // And: State is updated for the quest to start
                    updatedStateForNewlyStartedQuest(currentState, questToStart)
                )
            )
        )
    }

    private fun updatedStateForNewlyStartedQuest(
        currentState: State,
        questToStart: Quest
    ) = currentState.copy(
        currentQuest = questToStart.order,
        questStarted = timeProvider.now(),
        questDeadline = questToStart.countdown?.let {
            timeProvider.now().plusSeconds(it)
        },
        questCompleted = null
    )
    private fun assertCountdownContinues(
        outcome: WebAction,
        currentState: State
    ) {
        val currentQuest = loader.currentQuest(currentState)
        assertThat(
            outcome,
            equalTo(
                WebAction(
                    // Then: Countdown continues
                    CountdownViewModel(
                        currentQuest.countdownPage,
                        currentState.questStarted.toEpochSecond(),
                        currentState.questDeadline?.let { it.toEpochSecond() },
                        currentQuest.fictionalCountdown,
                        currentQuest.location?.lat,
                        currentQuest.location?.lon,
                        currentQuest.message
                    ),
                    // And: State is not changed
                    currentState
                )
            )
        )
    }

    private fun assertMissingCookieErrorShown(action: WebAction) {
        assertThat(
            action,
            equalTo(
                // And: Missing cookie error shown
                WebAction(
                    OnlyView("missingCookie"),
                    // And: State not set
                    null
                )
            )
        )
    }

    private fun stateForRunningQuest(scenario: Scenario, currentQuest: Quest) = State(
        scenario.name,
        currentQuest.order,
        currentQuest.countdown?.let { timeProvider.now().plusSeconds(it) },
        timeProvider.now().minusMinutes(1),
        null,
        UUID.randomUUID(),
        5,
        timeProvider.now().minusHours(1)
    )

    private fun nextQuest(scenario: Scenario, currentQuest: Quest) =
        scenario.quests[currentQuest.order + 1]

    private fun previousQuest(scenario: Scenario, currentQuest: Quest) =
        scenario.quests[currentQuest.order - 1]

    // Scanning the QR of the current quest
    private fun scanTargetQR(state: State?) =
        scanQR(state, loader.findScenario(state!!.scenario), loader.currentQuest(state))

    private fun scanTargetQR(state: State?, location: LocationReading) =
        scanQR(state, loader.findScenario(state!!.scenario), loader.currentQuest(state), location)

    // Calling engine complete is what happens when you scan the QR
    private fun scanQR(state: State?, scenario: Scenario, quest: Quest) =
        engine.complete(state, scenario.name, quest.order, quest.secret, freshLocation(quest))

    private fun scanQR(state: State?, quest: Quest) =
        engine.complete(state, state!!.scenario, quest.order, quest.secret, freshLocation(quest))

    private fun scanQR(
        state: State?,
        quest: Quest,
        atLocation: LocationReading
    ) =
        engine.complete(state, state!!.scenario, quest.order, quest.secret, atLocation.asString())

    private fun scanQR(
        state: State?,
        scenario: Scenario,
        quest: Quest,
        atLocation: LocationReading
    ) =
        engine.complete(state, scenario.name, quest.order, quest.secret, atLocation.asString())

    private fun startQuest(
        state: State,
        questToStart: Quest,
        location: LocationReading
    ) =
        engine.startQuest(
            state,
            state.scenario,
            questToStart.order,
            questToStart.secret,
            location.asString()
        )

    private fun startQuest(state: State, questToStart: Quest): WebAction =
        engine.startQuest(
            state, state.scenario, questToStart.order, questToStart.secret,
            freshLocation(loader.currentQuest(state))
        )

    private fun restartCurrentQuest(state: State): WebAction =
        engine.startQuest(
            state, state.scenario, state.currentQuest, loader.currentQuest(state).secret,
            freshLocation(loader.currentQuest(state))
        )

    private fun startNextQuest(state: State): WebAction {
        assertThat(state.questCompleted, notNullValue())
        return engine.startQuest(
            state, state.scenario, state.currentQuest + 1,
            loader.nextQuest(state).secret,
            freshLocation(loader.currentQuest(state))
        )
    }

    fun locationFor(quest: Quest) =
        LocationReading(quest.location!!.lat, quest.location!!.lon, timeProvider.now())

    fun locationSomewhere() =
        LocationReading(2.0, 3.0, timeProvider.now())

    abstract inner class BaseTestClassForStartingAnotherQuest(
        val currentState: State,
        val verify: (outcome: WebAction) -> Unit
    ) {

        @Test
        fun `when starting earlier quest`() {
            // When: Starting earlier quest
            var action = startQuest(currentState, loader.previousQuest(currentState))

            // Then:
            verify(action)
        }

        @Test
        fun `when starting earlier quest without proper location`() {
            // When: Starting earlier quest
            var action = startQuest(currentState, loader.previousQuest(currentState), locationSomewhere())

            // Then:
            verify(action)
        }

        @Test
        fun `when starting later quest`() {
            // When: Starting later quest
            var action = startQuest(currentState, loader.questFor(currentState.scenario, currentState.currentQuest + 2))

            // Then:
            verify(action)
        }

        @Test
        fun `when starting later quest without proper location`() {
            // When: Starting later quest
            var action = startQuest(currentState, loader.questFor(currentState.scenario, currentState.currentQuest + 2), locationSomewhere())

            // Then:
            verify(action)
        }

        @Test
        fun `when starting next quest`() {
            // When: Starting next quest, without completing current one
            var action = startQuest(currentState, loader.nextQuest(currentState))

            // Then:
            verify(action)
        }

        @Test
        fun `when starting next quest without proper location`() {
            // When: Starting next quest, without completing current one
            var action = startQuest(currentState, loader.nextQuest(currentState), locationSomewhere())

            // Then:
            verify(action)
        }
    }

    /**
     * Common tests for completing current quest
     */
    abstract inner class BaseTestClassForCompletingCurrentQuest(val currentState: State, val verify: (outcome: WebAction) -> Unit) {

        @Test
        fun `when scanning QR`() {
            // When: Completing the quest
            val viewModel = scanTargetQR(currentState)

            // Then:
            verify(viewModel)
        }

        @Test
        fun `or fail if location is not close to quest location`() {
            // And: Location not close to target
            // When: Completing quest
            // Then: Error about not being close to target location
            val error = assertThrows<TechnicalError> {
                scanTargetQR(currentState, locationSomewhere())
            }
            assertThat(error.message, equalTo("Coordinates not matching QR location, try rescanning the QR"))
        }

        @Test
        fun `or fail if location is not fresh`() {
            // And: Old location reading
            val oldLocation = LocationReading(
                loader.currentQuest(currentState).location!!.lat,
                loader.currentQuest(currentState).location!!.lon,
                timeProvider.now().minusDays(200)
            )

            // When: Scanning the QR
            // Then: Error about expired location
            val error = assertThrows<TechnicalError> {
                scanTargetQR(currentState, oldLocation)
            }
            assertThat(error.message, equalTo("Could not get fresh location, try rescanning the QR"))
        }
    }

    abstract inner class BaseTestClassForCompletingOutOfOrderQuests(
        val currentState: State,
        val verify: (outcome: WebAction) -> Unit
    ) {

        @Test
        fun `when scanning earlier QR`() {
            // When: Scanning QR code of earlier quest
            val outcome = scanQR(
                currentState,
                loader.questFor(currentState.scenario, currentState.currentQuest - 2)
            )

            // Then:
            verify(outcome)
        }

        // Could easily happen by just going back in browser history
        @Test
        fun `when scanning earlier QR without proper location`() {
            // When: Scanning QR code of earlier quest
            val outcome = scanQR(
                currentState,
                loader.questFor(currentState.scenario, currentState.currentQuest - 2),
                locationSomewhere()
            )

            // Then:
            verify(outcome)
        }

        // Scanning the QR that you just used to complete the previous quest.
        // 1. Go to QR and scan it to complete quest
        // 2. Start next quest
        // 3. Scan again the same QR as in step 1
        @Test
        fun `when scanning previous QR`() {
            // When: Scanning QR code of previous quest
            val outcome = scanQR(currentState, loader.previousQuest(currentState))

            // Then:
            verify(outcome)
        }

        // Could easily happen by going back in browser history
        @Test
        fun `when scanning previous QR without proper location`() {
            // When: Scanning QR code of previous quest
            val outcome = scanQR(currentState, loader.previousQuest(currentState), locationSomewhere())

            // Then:
            verify(outcome)
        }

        @Test
        fun `when scanning later QR`() {
            // When: Scanning QR code of later quest
            val outcome = scanQR(currentState, loader.nextQuest(currentState))

            // Then:
            verify(outcome)
        }

        @Test
        fun `when scanning later QR without proper location`() {
            // When: Scanning QR code of later quest
            val outcome = scanQR(currentState, loader.nextQuest(currentState), locationSomewhere())

            // Then:
            verify(outcome)
        }

        /**
         * User cannot end up here by scanning QR code (because the intro quest QR code points to
         * init scenario action instead of complete) but can access this e.g. by using browser
         * history or such.
         *
         * TODO: Did we already change so that actually first qr points to complete also? We should.
         * then update the above description.
         *
         * Let's just keep running current quest.
         */
        @Test
        fun `Restart scenario when scanning first QR`() {
            // When: Executing the intro's `complete` action, anywhere
            val action =
                engine.complete(
                    currentState,
                    currentState.scenario,
                    0,
                    loader.findScenario(currentState.scenario).quests[0].secret,
                    locationSomewhere().asString()
                )

            // Then: Scenario is restarted
            assertScenarioRestartAction(currentState, action)
        }

        /**
         * Allows restarting the scenario no matter what.
         */
        @Test
        fun `Restart scenario when scanning second QR`() {
            // When: Scanning second (first on field) quest's QR
            val secondQuest = loader.findScenario(currentState.scenario).quests[1]
            val result = scanQR(currentState, secondQuest)

            // Then: Restart the scenario
            assertScenarioRestartAction(currentState, result)
        }
    }

    abstract inner class BaseTestClassForReCompletingCurrentQuest(val currentState: State) {

        @Test
        fun verifyPreconditions() {
            assertThat(currentState.questCompleted, notNullValue())
        }

        @Test
        fun `Recompleting should show quest success page`() {
            val outcome = scanTargetQR(currentState)
            assertQuestSuccessViewShown(outcome, currentState)
        }
    }

    /**
     * Tests for reloading the browser after user has started a quest. After clicking "go" the
     * countdown is shown on the url with `/start`. It is not unlikely that the browser window
     * could be reloaded while user is on this page.
     */
    abstract inner class BaseTestClassForRestartingCurrentQuest(
        val currentState: State,
        val verify: (outcome: WebAction) -> Unit
    ) {

        /**
         * With reloading browser window
         */
        @Test
        fun `when restarting current quest near start point`() {
            // When: Restarting this quest
            val action = startQuest(currentState, loader.currentQuest(currentState))

            // Then:
            verify(action)
        }

        /**
         * User reloads browser window while already proceeded on current quest
         * outside the accepted range of the QR. Important for the user not to
         * loose the target coordinates.
         */
        @Test
        fun `when restarting current quest far from start point`() {
            // When: Restarting this quest
            val action = startQuest(
                currentState,
                loader.currentQuest(currentState),
                locationSomewhere()
            )

            // Then:
            verify(action)
        }
    }
}
