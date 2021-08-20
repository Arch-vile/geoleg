package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.model.WebAction
import com.nakoradio.geoleg.services.CookieManager
import com.nakoradio.geoleg.services.CountdownViewModel
import com.nakoradio.geoleg.services.Engine
import com.nakoradio.geoleg.services.LocationReadingViewModel
import com.nakoradio.geoleg.services.ViewModel
import com.nakoradio.geoleg.utils.Time
import javax.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.web.bind.MissingRequestCookieException
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.ModelAndView

@Controller
class EngineController(
    val cookieManager: CookieManager,
    val engine: Engine,
    val time: Time
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
        @PathVariable scenario: String,
        @PathVariable secret: String,
        response: HttpServletResponse
    ): ModelAndView {
        // TODO: old type cookie handling should be applied to all endpoints. some way to reset state.
        // Changes on state fields can cause deserialization errors, lets reset cookie then
        val state = try { cookieManager.fromWebCookieMaybe(cookieData) } catch (e: Throwable) {
            null
        }
        return processAction(response, engine.initScenario(state, scenario, secret))
    }

    /**
     * Start next quest. This endpoint is called when clicking "GO" to start the next quest.
     */
    @GetMapping("/engine/start/{scenario}/{questToStart}/{secret}/{locationString}")
    @ResponseBody
    fun startQuest(
        @CookieValue(COOKIE_NAME) cookieData: String,
        @PathVariable scenario: String,
        @PathVariable questToStart: Int,
        @PathVariable secret: String,
        @PathVariable locationString: String,
        response: HttpServletResponse
    ): ModelAndView {
        val state = cookieManager.fromWebCookie(cookieData)
        return processAction(response, engine.startQuest(state, scenario, questToStart, secret, locationString))
    }

    // This just does the redirection to location granting, which redirects back
    // to the other complete endpoint with the gain location.
    @GetMapping("/engine/complete/{scenario}/{questToComplete}/{secret}")
    @ResponseBody
    fun initComplete(
        @PathVariable scenario: String,
        @PathVariable questToComplete: Int,
        @PathVariable secret: String,
        response: HttpServletResponse
    ) =
        processWebView(engine.initComplete(scenario, questToComplete, secret))

    @GetMapping("/engine/complete/{scenario}/{questOrder}/{secret}/{locationString}")
    @ResponseBody
    fun complete(
        @CookieValue(COOKIE_NAME) cookieData: String?,
        @PathVariable scenario: String,
        @PathVariable questOrder: Int,
        @PathVariable secret: String,
        @PathVariable locationString: String,
        response: HttpServletResponse
    ): ModelAndView {
        val state = cookieManager.fromWebCookieMaybe(cookieData)
        return processAction(
            response,
            engine.complete(state, scenario, questOrder, secret, locationString)
        )
    }

    // Stupid proxy for rendering the view
    @GetMapping("/checkLocation")
    fun checkLocation(viewModel: LocationReadingViewModel) =
        processWebView(viewModel)

    // Stupid proxy for rendering the view
    @GetMapping("/countdown")
    fun countdown(model: CountdownViewModel) =
        processWebView(model)

    @ExceptionHandler(value = [MissingRequestCookieException::class])
    fun missinCoookieHandler(ex: MissingRequestCookieException) =
        ModelAndView("missingCookie", "msg", "doo")

    private fun processAction(response: HttpServletResponse, action: WebAction): ModelAndView {
        if (action.state == null) {
            logger.info("Do not set cookies. Rendering view ${action.modelAndView.view} with model ${action.modelAndView}")
        } else {
            logger.info("Setting cookie [${action.state}] and rendering view ${action.modelAndView.view} with model ${action.modelAndView}")
            response.addCookie(cookieManager.toWebCookie(action.state))
        }

        return asModelAndView(action.modelAndView)
    }

    private fun processWebView(webView: ViewModel): ModelAndView {
        logger.info("Rendering view [${webView.view}] with model [$webView]")
        return asModelAndView(webView)
    }

    private fun asModelAndView(modelView: ViewModel) =
        ModelAndView(modelView.view, "model", modelView)
}
