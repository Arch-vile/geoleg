package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.model.Quest
import com.nakoradio.geoleg.model.StoryError
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

    @GetMapping("/engine/init/{scenario}/{secret}")
    @ResponseBody
    fun initScenario(
            @CookieValue(COOKIE_NAME) cookieData: String?,
            @PathVariable("scenario") scenario: String,
            @PathVariable("secret") secret: String,
            response: HttpServletResponse
    ) {
        val quest = loader
                .load()
                .scenarios.find { it.name == scenario }
                ?.quests
                ?.find { it.order == 1 }
                ?.takeIf { it.secret == secret }
                ?: throw TechnicalError("No such quest for you my friend")


        if (scenario == SCENARIO_ANCIENT_BLOOD) {
            startAncientBlood(scenario, quest, cookieData, response)
            return
        }

        throw TechnicalError("Unknown scenario")
    }

    @GetMapping("/engine/complete/{scenario}/{quest}/{secret}/{location}")
    @ResponseBody
    fun process(
            @CookieValue(COOKIE_NAME) cookieData: String?,
            @PathVariable("scenario") scenario: String,
            @PathVariable("quest") questOrder: Int,
            @PathVariable("secret") secret: String,
            @PathVariable("location") location: String,
            response: HttpServletResponse
    ) {
        val quest = loader
            .load()
            .scenarios.find { it.name == scenario }
            ?.quests
            ?.find { it.order == questOrder }
            ?.takeIf { it.secret == secret }
            ?: throw TechnicalError("No such quest for you my friend")

        if(cookieData == null) {
            // TODO: We need to have special page for this to explain. Imagine if someone,
            // scans the qr code found by accident.
            throw StoryError("You need to start from the first quest! Go at coordinates: ${quest.location.lat}, ${quest.location.lon}");
        }

        throw TechnicalError("Check not yet implemented")
    }

    // Create new start scenario token, with unlimited time to complete the first quest (which is
    // actually where the user already is)
    // Preserving the userId if present
    private fun startAncientBlood(scenario: String, quest: Quest, cookieData: String?, response: HttpServletResponse){
        val existingCookie = cookieData?.let { cookieManager.fromWebCookie(cookieData) }

        val cookie =
            cookieManager.updateOrCreate(
                existingCookie,
                scenario,
                1,
                now().plusYears(10)
            )

        response.addCookie(cookieManager.toWebCookie(cookie))
        response.sendRedirect(askForLocation(questVerifyUrl(scenario, quest)))
    }

    private fun questVerifyUrl(scenario: String, quest: Quest): String {
        return "/engine/complete/$scenario/${quest.order}/${quest.secret}"
    }

    private fun askForLocation(questUrl: String): String {
        return "/checkLocation.html?target=${questUrl}"
    }
}
