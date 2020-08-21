package com.nakoradio.geoleg.model

import com.nakoradio.geoleg.controllers.COOKIE_NAME
import com.nakoradio.geoleg.services.Cryptor
import java.time.OffsetDateTime
import java.util.UUID
import javax.servlet.http.Cookie

data class StateCookie(
        val scenario: String,
        val currentLeg: Int,
        val expiresAt: OffsetDateTime,
        val createdAt: OffsetDateTime,
        val userId: UUID) {
    fun asCookie(): Cookie {
        return Cookie(COOKIE_NAME, "thiswillbeencrypted")
    }
}

