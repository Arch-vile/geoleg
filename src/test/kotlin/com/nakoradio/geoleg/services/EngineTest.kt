package com.nakoradio.geoleg.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.nakoradio.geoleg.model.State
import com.nakoradio.geoleg.model.TechnicalError
import com.nakoradio.geoleg.utils.now
import java.util.UUID
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class EngineTest {

    private val jsonmappper = ObjectMapper().registerModule(KotlinModule())
    private val loader = ScenarioLoader(jsonmappper)
    private val engine = Engine(
        true,
        CookieManager(Cryptor("bbc02fc2febb11d73ec4215cd8014ad2"), jsonmappper),
        loader
    )

    @Test
    fun `init scenario will reset the existing state for scenario`() {
        val scenario = loader.table.scenarios[0]

        // Given: State with old dates and later quest order
        val existingState = State(scenario.name, 10, now().minusDays(20), now().minusDays(39), UUID.randomUUID(), 10)

        // When: scenario is initialized
        val (url, newState) = engine.initScenario(
            existingState,
            scenario.name,
            scenario.quests[0].secret
        )

        // Then: State is reset to first quest
        assertThat(newState.currentQuest, equalTo(0))

        // And: Deadline is set far in future, to "never" expire
        assertThat(newState.deadline.isAfter(now().plusYears(10).minusMinutes(1)), equalTo(true))

        // And: Restart count is increased by one
        assertThat(newState.scenarioRestartCount, equalTo(11))

        // And: User id is kept
        assertThat(newState.userId, equalTo(existingState.userId))
    }

    @Test
    fun `init scenario will reset the state of another scenario`() {
        val scenario = loader.table.scenarios[0]

        // Given: State for another scenario
        val existingState = State("the other scenario", 1, now().plusDays(1), now(), UUID.randomUUID(), 2)

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
            State.empty(),
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
                State.empty(),
                scenario.name,
                "bad secret"
            )
        }
    }
}
