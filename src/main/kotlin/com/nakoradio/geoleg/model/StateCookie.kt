package com.nakoradio.geoleg.model

import com.nakoradio.geoleg.controllers.COOKIE_NAME
import java.time.OffsetDateTime
import javax.servlet.http.Cookie

data class StateCookie(val scenario: String, val currentLeg: Int, val expiresAt: OffsetDateTime, val userId: String) {
    fun asCookie(): Cookie {
        return Cookie(COOKIE_NAME, "thiswillbeencrypted")
    }
}

fun cookieFrom(data: String): StateCookie {
    return StateCookie(
            "some scenario",
            222,
            OffsetDateTime.now(),
            "foooobar"
    )
}