package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.model.MissingCookieError
import com.nakoradio.geoleg.model.State
import com.nakoradio.geoleg.model.WebAction
import com.nakoradio.geoleg.services.CookieManager
import com.nakoradio.geoleg.services.Engine
import javax.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.ModelAndView

@Controller
class EngineController(
    val cookieManager: CookieManager,
    val engine: Engine
) {

    var logger: Logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Initialize certain scenario. The QR code scanned from the web page (and link) points here.
     *
     * We will set cookie for the first quest and then redirect to quest complete url. If state
     * (cookie) already exists, we will keep the userId and update the restart count.
     *
     * In the quest config, the first quest has location and countdown verification turned off,
     * so arriving to the complete url will be accepted as success and the success page will
     * be shown (which is the introduction text to the scenario). We will get location anyway
     * to be confident user's device is compatible on upcoming quests also.
     *
     * Proceeding to second quest will give the location of the first on field QR code. You should
     * note that the second quest has `"shouldVerifyCountdown": false` so there is no time limit
     * to reach it (also countdown set to zero, so timer not shown).
     *
     */
    @GetMapping("/engine/init/{scenario}/{secret}")
    @ResponseBody
    fun initScenario(
        @CookieValue(COOKIE_NAME) cookieData: String?,
        @PathVariable("scenario") scenario: String,
        @PathVariable("secret") secret: String,
        response: HttpServletResponse
    ) {
        val state =
                cookieData?.let { cookieManager.fromWebCookie(it) }
                        ?: State.empty()

        return processAction(response, engine.initScenario(state, scenario, secret))
    }

    /**
     * Start next quest. This endpoint is called when clicking "GO" to start the next quest.
     */
    @GetMapping("/engine/start/{scenario}/{quest}/{secret}/{location}")
    @ResponseBody
    fun startQuest(
        @CookieValue(COOKIE_NAME) cookieData: String?,
        @PathVariable("scenario") scenario: String,
        @PathVariable("quest") questToStart: Int,
        @PathVariable("secret") secret: String,
        @PathVariable("location") locationString: String,
        response: HttpServletResponse
    ) {
        return processAction(response, engine.startQuest(cookieData, scenario, questToStart, secret, locationString))
    }

    // This just does the redirection to location granting, which redirects back
    // to the other complete endpoint with location.
    @GetMapping("/engine/complete/{scenario}/{quest}/{secret}")
    @ResponseBody
    fun initComplete(
        @PathVariable("scenario") scenario: String,
        @PathVariable("quest") questToComplete: Int,
        @PathVariable("secret") secret: String,
        response: HttpServletResponse
    ) {
        response.sendRedirect(engine.initComplete(scenario, questToComplete, secret))
    }

    @GetMapping("/engine/complete/{scenario}/{quest}/{secret}/{location}")
    @ResponseBody
    fun complete(
        @CookieValue(COOKIE_NAME) cookieData: String?,
        @PathVariable("scenario") scenario: String,
        @PathVariable("quest") questOrder: Int,
        @PathVariable("secret") secret: String,
        @PathVariable("location") locationString: String,
        response: HttpServletResponse
    ) {
        val nextPage = engine.complete(cookieData, scenario, questOrder, secret, locationString)
        logger.info("Redirecting to $nextPage")
        response.sendRedirect(nextPage)
    }

    @ExceptionHandler(value = [MissingCookieError::class])
    fun missinCoookieHandler(ex: MissingCookieError): ModelAndView {
        return ModelAndView("missingCookie", "msg", "doo")
    }

    private fun processAction(response: HttpServletResponse, action: WebAction) {
        logger.info("Setting cookie [${action.cookie}] and redirecting to ${action.url}")
        response.addCookie(cookieManager.toWebCookie(action.cookie))
        response.sendRedirect(action.url)
    }
}
