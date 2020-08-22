package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.model.StateCookie
import com.nakoradio.geoleg.services.CookieManager
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneId
import java.time.ZonedDateTime

@RestController
class CookieChecker(val cookieManager: CookieManager) {

    @GetMapping("/yummy")
    fun checkCookie(@CookieValue(COOKIE_NAME) cookieData: String): CookieExposeResponse {
        var stateCookie = cookieManager.fromWebCookie(cookieData)

        return CookieExposeResponse(stateCookie, stateCookie.expiresAt.atZoneSameInstant(ZoneId.of("Europe/Helsinki")))
    }

}

class CookieExposeResponse(val cookie: StateCookie, val _expiresAt: ZonedDateTime)