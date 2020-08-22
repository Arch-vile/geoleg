package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.model.TechnicalError
import com.nakoradio.geoleg.services.CookieManager
import com.nakoradio.geoleg.services.ScenarioLoader
import com.nakoradio.geoleg.utils.now
import javax.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class Engine(val cookieManager: CookieManager, val loader: ScenarioLoader) {

    val SCENARIO_ANCIENT_BLOOD = "ancient-blood"

    @GetMapping("/engine/{scenario}/{quest}/{action}")
    @ResponseBody
    fun process(
        @CookieValue(COOKIE_NAME) cookieData: String?,
        @PathVariable("scenario") scenario: String,
        @PathVariable("quest") questOrder: Int,
        @PathVariable("action") action: String,
        @RequestParam("secret") secret: String,
        response: HttpServletResponse
    ): String {
        val quest = loader
            .load()
            .scenarios.find { it.name == scenario }
            ?.quests
            ?.find { it.order == questOrder }
            ?.takeIf { it.secret == secret }
            ?: throw TechnicalError("No such quest for you my friend")

        if (scenario == SCENARIO_ANCIENT_BLOOD && questOrder == 1 && action == "init") {
            return startAncientBlood(cookieData, response)
        }

        throw TechnicalError("Unknown scenario")
    }

    // Create new start scenario token, with unlimited time to complete the first quest (which is
    // actually where the user already is)
    // Preserving the userId if present
    private fun startAncientBlood(cookieData: String?, response: HttpServletResponse): String {
        val existingCookie = cookieData?.let { cookieManager.fromWebCookie(cookieData) }

        val cookie =
            cookieManager.updateOrCreate(
                existingCookie,
                SCENARIO_ANCIENT_BLOOD,
                1,
                now().plusYears(10)
            )

        response.addCookie(cookieManager.toWebCookie(cookie))
        return "Scnario $SCENARIO_ANCIENT_BLOOD started"
    }
}
