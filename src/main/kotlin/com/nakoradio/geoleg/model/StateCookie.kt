package com.nakoradio.geoleg.model

import java.time.OffsetDateTime
import java.util.UUID

data class StateCookie(
        val scenario: String,
        val quest: Int,
        val expiresAt: OffsetDateTime,
        val createdAt: OffsetDateTime,
        val userId: UUID,
        val restartCount: Int
)
