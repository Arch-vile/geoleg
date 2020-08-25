package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.model.StateCookie
import com.nakoradio.geoleg.services.CookieManager
import java.time.ZoneId
import java.time.ZonedDateTime
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin")
class AdminController(
    val cookieManager: CookieManager,
    val engine: Engine
) {

    @GetMapping("/yummy")
    fun checkCookie(@CookieValue(COOKIE_NAME) cookieData: String): CookieExposeResponse {
        var stateCookie = cookieManager.fromWebCookie(cookieData)

        return CookieExposeResponse(stateCookie, stateCookie.expiresAt.atZoneSameInstant(ZoneId.of("Europe/Helsinki")))
    }

    @GetMapping("/toggleLocationVerify")
    fun toggleLocationVerify(): Boolean {
        return engine.toggleLocationVerification()
    }
}

class CookieExposeResponse(val cookie: StateCookie, val _expiresAt: ZonedDateTime)