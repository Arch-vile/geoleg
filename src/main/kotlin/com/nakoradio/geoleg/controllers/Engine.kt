package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.model.StateCookie
import com.nakoradio.geoleg.model.TechnicalError
import com.nakoradio.geoleg.services.CookieManager
import com.nakoradio.geoleg.utils.now
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import javax.servlet.http.HttpServletResponse

@Controller
class Engine(val cookieManager: CookieManager) {

    val SCENARIO_ANCIENT_BLOOD = "ancient-blood"

    @GetMapping("/engine/{scenario}/{leg}/{action}")
    @ResponseBody
    fun process(
            @CookieValue(COOKIE_NAME) cookieData: String?,
            @PathVariable("scenario") scenario: String,
            @PathVariable("leg") leg: Int,
            @PathVariable("action") action: String,
            @RequestParam("secret") secret: String,
            response: HttpServletResponse
    ): String {

        if (scenario == SCENARIO_ANCIENT_BLOOD && leg == 1 && action == "init")
            return startAncientBlood(cookieData, response)

        throw TechnicalError("Unknown scenario")
    }

    // Create new start scenario token, with unlimited time to complete the first leg (which is
    // actually where the user already is)
    // Preserving the userId if present
    private fun startAncientBlood(cookieData: String?, response: HttpServletResponse): String {
        val cookie =
                cookieManager.create(
                SCENARIO_ANCIENT_BLOOD,
                1,
                now().plusYears(10),
                cookieData?.let {
                    cookieManager.fromWebCookie(it).userId
                } ?: UUID.randomUUID()
        )

        response.addCookie(cookieManager.toWebCookie(cookie))
        return "Scnario $SCENARIO_ANCIENT_BLOOD started"
    }
}
