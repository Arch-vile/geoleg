package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.model.State
import com.nakoradio.geoleg.services.CookieManager
import com.nakoradio.geoleg.services.Engine
import com.nakoradio.geoleg.services.ScenarioLoader
import java.time.ZoneId
import java.time.ZonedDateTime
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.ModelAndView

@RestController
@RequestMapping("/admin")
class AdminController(
    val qrController: QRController,
    val cookieManager: CookieManager,
    val engine: Engine,
    val loader: ScenarioLoader
) {

    @GetMapping("/yummy")
    fun checkCookie(@CookieValue(COOKIE_NAME) cookieData: String): CookieExposeResponse {
        var stateCookie = cookieManager.fromWebCookie(cookieData)

        return CookieExposeResponse(
            stateCookie,
            stateCookie.questDeadline?.atZoneSameInstant(ZoneId.of("Europe/Helsinki"))
        )
    }

    @GetMapping("/toggleLocationVerify")
    fun toggleLocationVerify(): Boolean {
        return engine.toggleLocationVerification()
    }

    @GetMapping("/qrLinks")
    fun qrLinks(): ModelAndView {
        return ModelAndView("admin/qrLinks", "model",
            qrController.QR_CODE_MAPPING.entries
        )
    }
}

class CookieExposeResponse(val cookie: State, val _expiresAt: ZonedDateTime?)
