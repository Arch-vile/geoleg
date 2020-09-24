package com.nakoradio.geoleg.model

import com.nakoradio.geoleg.utils.Time
import java.time.OffsetDateTime
import java.util.UUID

data class State(
    val scenario: String,
    val currentQuest: Int,
    val questDeadline: OffsetDateTime,
    val questStarted: OffsetDateTime,
    val userId: UUID,
    val scenarioRestartCount: Int
) {
    companion object Factory {
        fun empty(time: Time) = State("", 0, time.now().minusDays(100), time.now(), UUID.randomUUID(), 0)
    }
}
