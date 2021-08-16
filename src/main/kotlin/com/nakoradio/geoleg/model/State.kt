package com.nakoradio.geoleg.model

import com.nakoradio.geoleg.utils.Time
import java.time.OffsetDateTime
import java.util.UUID

data class State(
    val scenario: String,
    val currentQuest: Int,

    // Can be omitted to indicate no deadline
    val questDeadline: OffsetDateTime?,

    val questStarted: OffsetDateTime,
    val questCompleted: OffsetDateTime?,
    val userId: UUID,
    val scenarioRestartCount: Int,

    // For tracking whole scenario completion time
    val scenarioStarted: OffsetDateTime
) {
    companion object Factory {
        fun empty(time: Time) = State(
            "",
            0,
            time.now().minusDays(100),
            time.now(),
            null,
            UUID.randomUUID(),
            0,
            time.now()
        )
    }
}
