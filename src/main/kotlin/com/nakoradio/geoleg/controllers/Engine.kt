package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.model.Coordinates
import com.nakoradio.geoleg.model.LocationReading
import com.nakoradio.geoleg.model.MissingCookieError
import com.nakoradio.geoleg.model.Quest
import com.nakoradio.geoleg.model.StateCookie
import com.nakoradio.geoleg.model.TechnicalError
import com.nakoradio.geoleg.model.WebAction
import com.nakoradio.geoleg.services.CookieManager
import com.nakoradio.geoleg.services.ScenarioLoader
import com.nakoradio.geoleg.utils.distance
import com.nakoradio.geoleg.utils.now
import java.time.Duration
import javax.servlet.http.HttpServletResponse
import kotlin.math.absoluteValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.ModelAndView

@Controller
class Engine(
    @Value("\${location.verification.enabled:true}") var verifyLocation: Boolean,
    val cookieManager: CookieManager,
    val loader: ScenarioLoader
) {

    var logger: Logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Initialize certain scenario. The QR code scanned from the web page (and link) points here.
     *
     * We will set cookie for the first quest and then redirect to quest complete url.
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
        val quest = loader.questFor(scenario, 0, secret)
        val cookie =
            cookieManager.updateOrCreate(
                cookieData,
                scenario,
                0,
                now().plusYears(10)
            )

        return processAction(response, WebAction(askForLocation(questCompleteUrl(scenario, quest)), cookie))
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
        val quest = loader.questFor(scenario, questToStart, secret)
        val cookie = assertCookieIsPresent(cookieData, scenario)

        val locationReading = LocationReading.fromString(locationString)
        checkIsFresh(locationReading)
        assertProximity(quest, locationReading.toCoordinates())

        assertEqual(cookie.scenario, scenario, "Bad cookie scenario")
        assertEqual(cookie.quest, questToStart - 1, "Bad cookie quest")

        var updatedCookie = cookie.copy(
            createdAt = now(),
            quest = questToStart,
            expiresAt = now().plusSeconds(quest.countdown)
        )

        var expiresAt = now().plusSeconds(quest.countdown).toEpochSecond()
        var now = now().toEpochSecond()
        var countdownPageUrl = countdownPage(expiresAt, now, quest.fictionalCountdown, quest.location)

        processAction(response, WebAction(countdownPageUrl, updatedCookie))
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
        val quest = loader.questFor(scenario, questToComplete, secret)
        response.sendRedirect(
            askForLocation(
                questCompleteUrl(scenario, quest)
            )
        )
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
        val quest = loader.questFor(scenario, questOrder, secret)
        val cookie = assertCookieIsPresent(cookieData, scenario)

        val locationReading = LocationReading.fromString(locationString)
        checkIsFresh(locationReading)

        val nextPage = checkQuestCompletion(scenario, quest, locationReading.toCoordinates(), cookie)
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

    private fun assertCookieIsPresent(cookieData: String?, scenario: String): StateCookie {
        if (cookieData == null) {
            // TODO: We need to have special page for this to explain. Imagine if someone,
            // scans the qr code found by accident.
            val firstQuest = loader.firstQuestFor(scenario)
            throw MissingCookieError(firstQuest.location.lat, firstQuest.location.lon)
        } else {
            return cookieManager.fromWebCookie(cookieData)
        }
    }

    private fun checkQuestCompletion(scenario: String, quest: Quest, location: Coordinates, state: StateCookie): String {
        assertEqual(scenario, state.scenario, "scenario completion")
        assertEqual(quest.order, state.quest, "quest matching")

        if (quest.shouldVerifyLocation) {
            assertProximity(quest, location)
        }

        return if (quest.shouldVerifyCountdown && now().isAfter(state.expiresAt)) {
            logger.info("Quest failed due to time running out")
            quest.failurePage
        } else {
            logger.info("Quest success!")
            quest.successPage
        }
    }

    private fun assertProximity(quest: Quest, location: Coordinates) {
        if (!verifyLocation) {
            return
        }

        var distance = distance(quest.location, location)
        if (distance > 100) {
            logger.error("quest location [${quest.location}] location [$location] distance [$distance]")
            throw TechnicalError("Bad gps accuracy")
        }
    }

    private fun assertEqual(val1: Any, val2: Any, context: String) {
        if (val1 != val2) {
            throw TechnicalError("Not good $context")
        }
    }

    private fun checkIsFresh(location: LocationReading) {
        // We should receive the location right after granted, if it takes longer, suspect something funny
        if (Duration.between(now(), location.createdAt).seconds.absoluteValue > 10) {
            throw TechnicalError("Something funny with the location")
        }
    }

    private fun questCompleteUrl(scenario: String, quest: Quest): String {
        return "/engine/complete/$scenario/${quest.order}/${quest.secret}"
    }

    private fun askForLocation(questUrl: String) =
        "/checkLocation.html?target=$questUrl"

    private fun countdownPage(expiresAt: Long, now: Long, fictionalCountdown: Long, location: Coordinates) =
        "/countdown.html?expiresAt=$expiresAt&now=$now&countdown=$fictionalCountdown&lat=${location.lat}&lon=${location.lon}"

    fun toggleLocationVerification(): Boolean {
        verifyLocation = !verifyLocation
        return verifyLocation
    }
}
