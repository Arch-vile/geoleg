package com.nakoradio.geoleg.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.nakoradio.geoleg.model.LocationReading
import com.nakoradio.geoleg.model.Quest
import com.nakoradio.geoleg.model.State
import com.nakoradio.geoleg.model.TechnicalError
import com.nakoradio.geoleg.utils.Time
import java.time.OffsetDateTime
import java.util.UUID
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class EngineTest {

    private val jsonmappper = ObjectMapper().registerModule(KotlinModule())
    private val loader = ScenarioLoader(jsonmappper)
    private val timeProvider = object : Time() {
        val now = super.now()
        override fun now(): OffsetDateTime {
            return now
        }
    }

    // Engine has location verification turned on (there is no location check on init)
    private val engine = Engine(
        true,
        timeProvider,
        loader
    )

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

        @Test
        fun `complete the intro quest`() {
            // When: Intro quest is completed
            val (url, newState) = engine.complete(state, scenario.name, 0, scenario.quests[0].secret, locationString)

            // Then: Redirected to success page
            assertThat(url, equalTo(scenario.quests[0].successPage))
        }

        @Test
        fun `trying to complete intro with bad quest order in state`() {
            // Given: State with non zero quest order
            val badState = state.copy(currentQuest = 1)

            // When: Intro quest is completed, error is thrown due to mismatch in quest number
            assertThrows<TechnicalError> {
                val (url, newState) = engine.complete(badState, scenario.name, 0, scenario.quests[0].secret, locationString)
            }
        }

        @Test
        fun `trying to complete intro with bad quest order in params`() {
            // When: Intro quest is completed, error is thrown due to mismatch in quest number
            assertThrows<TechnicalError> {
                engine.complete(state, scenario.name, 1, scenario.quests[0].secret, locationString)
            }
        }

        @Test
        fun `trying to complete intro with unknown scenario in params`() {
            // When: Intro quest is completed, error is thrown due scenario not found
            assertThrows<TechnicalError> {
                engine.complete(state, "other scenario", 0, scenario.quests[0].secret, locationString)
            }
        }

        @Test
        fun `trying to complete intro with unknown scenario in state`() {
            // Given: State has unknown scenario (should not technically be possible)
            val badState = state.copy(scenario = "other scenario")

            // When: Intro quest is completed, error is thrown due to mismatch in scenario
            assertThrows<TechnicalError> {
                val (url, newState) = engine.complete(badState, scenario.name, 0, scenario.quests[0].secret, locationString)
            }
        }

        @Test
        fun `trying to complete intro with different scenario in state`() {
            // Given: State is for different scenario
            val badState = state.copy(loader.table.scenarios[1].name)

            // When: Intro quest is completed, error is thrown due to mismatch in scenario
            assertThrows<TechnicalError> {
                val (url, newState) = engine.complete(badState, scenario.name, 0, scenario.quests[0].secret, locationString)
            }
        }

        @Test
        fun `trying to complete intro with different scenario in params`() {
            // When: Intro quest is completed, error is thrown due to mismatch in scenario
            assertThrows<TechnicalError> {
                val (url, newState) = engine.complete(state, loader.table.scenarios[1].name, 0, scenario.quests[0].secret, locationString)
            }
        }

        @Test
        fun `trying to complete intro with malformed location`() {
            // When: Intro quest is completed, error is thrown due to malformed location
            assertThrows<TechnicalError> {
                val (url, newState) = engine.complete(state, scenario.name, 0, scenario.quests[0].secret, "badLocation")
            }
        }

        @Test
        fun `trying to complete intro with bad secret`() {
            // When: Intro quest is completed, error is thrown due to mismatch in secret
            assertThrows<TechnicalError> {
                val (url, newState) = engine.complete(state, scenario.name, 0, "bad secret", locationString)
            }
        }
    }

    @Nested
    inner class `Scenario initialization` {

        @Test
        fun `init scenario will reset the existing state for scenario`() {
            val scenario = loader.table.scenarios[0]

            // Given: State with old dates and later quest order
            val existingState = State(scenario.name, 10, timeProvider.now().minusDays(20), timeProvider.now().minusDays(39), UUID.randomUUID(), 10)

            // When: scenario is initialized
            val (url, newState) = engine.initScenario(
                existingState,
                scenario.name,
                scenario.quests[0].secret
            )

            // Then: State is reset to first quest
            assertThat(newState.currentQuest, equalTo(0))

            // And: questDeadline is set far in future, to "never" expire
            assertThat(newState.questDeadline.isAfter(timeProvider.now().plusYears(10).minusMinutes(1)), equalTo(true))

            // And: Restart count is increased by one
            assertThat(newState.scenarioRestartCount, equalTo(11))

            // And: User id is kept
            assertThat(newState.userId, equalTo(existingState.userId))
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

            // Then: State is intialized for this scenario
            assertThat(newState.scenario, equalTo(scenario.name))

            // And: State is reset to first quest
            assertThat(newState.currentQuest, equalTo(0))

            // And: Restart count is set to 0
            assertThat(newState.scenarioRestartCount, equalTo(0))

            // And: User id is kept
            assertThat(newState.userId, equalTo(existingState.userId))
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
            val (url, newState) = engine.initScenario(
                State.empty(timeProvider),
                scenario.name,
                scenario.quests[0].secret
            )

            // Then: Redirected to quest complete
            // We want to read the location also although not needed, as this could allow user to
            // catch any technical errors on location reading already at home.
            assertThat(url, equalTo("/checkLocation.html?target=/engine/complete/ancient-blood/0/6a5fc6c0f8ec"))
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

    /**
     * There is some speciality to the second quest also. Because the first quest is completed
     * at home, the second quest is also started from home (thus we cannot specify the location)
     * for it. Usually quest need to be started at the same location as the previous one was
     * completed, but this does not apply for the second quest.
     *
     * First quest is the introduction quest that is automatically instantly completed. Second
     * quest is thus also started right away at home. This has the following implications
     * for the second quest processing logic:
     * - Second quest can be started anywhere (not in the end point of previous one, as others)
     * - Second quest has unlimited time to complete
     */
    @Nested
    inner class `Starting the second quest` {

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

        @Test
        fun `quest successfully started`() {
            // Given: Location that is far away from the first quest's end location and very old
            val locationString = LocationReading(
                48.854546, 2.347316, // This is Paris
                timeProvider.now().minusDays(100)
            ).asString()

            // And: State that
            val state = State(
                scenario.name,
                0,
                // Has a questDeadline that is already passed
                timeProvider.now().minusDays(1),
                timeProvider.now(),
                UUID.randomUUID(),
                5
            )

            // When: Starting the second quest
            val (url, newState) = engine.startQuest(state, scenario.name, 1, questToStart.secret, locationString)

            // Then: Redirected to countdown page
            // Regardless of questDeadline already passed
            // Regardless of location not matching
            assertThat(
                url,
                equalTo(
                    """
                        |/countdown.html?
                            |expiresAt=${newState.questDeadline.toEpochSecond()}&
                            |now=${newState.questStarted.toEpochSecond()}&
                            |countdown=0&
                            |lat=${questToStart.location.lat}&
                            |lon=${questToStart.location.lon}
                    """.trimMargin().replace("\\n".toRegex(), "")
                )
            )

            // And: New state is updated with deadline accordingly to quest spec
            assertThat(newState.questDeadline, equalTo(timeProvider.now().plusSeconds(questToStart.countdown)))

            // Ans: questStarted timestamp update
            assertThat(newState.questStarted, equalTo(timeProvider.now()))

            // And: current quest set to the one to start
            assertThat(newState.currentQuest, equalTo(1))
        }
    }

    @Nested
    inner class `Starting Nth quest` {

        val scenario = loader.table.scenarios[1]
        val questToStart = scenario.quests[2]

        @Test
        fun `fail if location reading is not fresh enough`() {
            // Given: Location that is old
            val locationString = LocationReading(
                questToStart.location.lat, questToStart.location.lon,
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
                questToStart.location.lat + 0.002, questToStart.location.lon,
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
            val (url, newState) =
                engine.startQuest(
                    state,
                    scenario.name,
                    questToStart.order,
                    questToStart.secret,
                    freshLocation(questToStart)
                )

            // Then: Redirected to countdown page
            assertThat(
                url,
                equalTo(
                    """
                        |/countdown.html?
                            |expiresAt=${newState.questDeadline.toEpochSecond()}&
                            |now=${newState.questStarted.toEpochSecond()}&
                            |countdown=${questToStart.fictionalCountdown}&
                            |lat=${questToStart.location.lat}&
                            |lon=${questToStart.location.lon}
                    """.trimMargin().replace("\\n".toRegex(), "")
                )
            )

            // And: New state is updated with deadline accordingly to quest spec
            assertThat(newState.questDeadline, equalTo(timeProvider.now().plusSeconds(questToStart.countdown)))

            // Ans: questStarted timestamp update
            assertThat(newState.questStarted, equalTo(timeProvider.now()))

            // And: current quest set to the one to start
            assertThat(newState.currentQuest, equalTo(questToStart.order))
        }
    }

    private fun freshLocation(questToStart: Quest) =
        LocationReading(
            questToStart.location.lat, questToStart.location.lon,
            timeProvider.now()
        ).asString()
}
