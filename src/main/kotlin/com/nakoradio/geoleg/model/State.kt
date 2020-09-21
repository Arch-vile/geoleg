package com.nakoradio.geoleg.model

import com.nakoradio.geoleg.utils.now
import java.time.OffsetDateTime
import java.util.UUID

data class State(
    val scenario: String,
    val currentQuest: Int,
    val deadline: OffsetDateTime,
    val started: OffsetDateTime,
    val userId: UUID,
    val scenarioRestartCount: Int
) {
    companion object Factory {
        fun empty() = State("", 0, now(), now(), UUID.randomUUID(), 0)
    }
}
