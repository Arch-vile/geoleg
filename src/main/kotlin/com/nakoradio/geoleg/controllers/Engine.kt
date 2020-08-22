package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.model.StateCookie
import com.nakoradio.geoleg.services.CookieManager
import java.time.OffsetDateTime
import java.util.UUID
import javax.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

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

        return "Nothing here yet"
    }

    private fun startAncientBlood(cookieData: String?, response: HttpServletResponse): String {
        val userId = null // cookieData?.let {  cookieFrom(cookieData).userId}

        val cookie = StateCookie(
                SCENARIO_ANCIENT_BLOOD,
                1,
                OffsetDateTime.now().plusDays(2),
                OffsetDateTime.now(),
                userId ?: UUID.randomUUID()
        )

        response.addCookie(cookieManager.toWebCookie(cookie))
        return "Scnario $SCENARIO_ANCIENT_BLOOD started"
    }
}
