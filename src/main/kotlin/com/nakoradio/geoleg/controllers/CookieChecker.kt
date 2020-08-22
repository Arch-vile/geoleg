package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.services.CookieManager
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class CookieChecker(val cookieManager: CookieManager) {

    @GetMapping("/yummy")
    fun checkCookie(@CookieValue(COOKIE_NAME) cookieData: String) =
            cookieManager.fromWebCookie(cookieData)

}