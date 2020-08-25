package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.model.Coordinates
import com.nakoradio.geoleg.model.LocationReading
import com.nakoradio.geoleg.model.Quest
import com.nakoradio.geoleg.model.StateCookie
import com.nakoradio.geoleg.model.StoryError
import com.nakoradio.geoleg.model.TechnicalError
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class Engine(
        @Value("\${location.verification.enabled:true}") var verifyLocation: Boolean,
        val cookieManager: CookieManager,
        val loader: ScenarioLoader) {

    var logger: Logger = LoggerFactory.getLogger(this::class.java)

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
        val quest = loader
            .load()
            .scenarios.find { it.name == scenario }
            ?.quests
            ?.find { it.order == questToStart }
            ?.takeIf { it.secret == secret }
            ?: throw TechnicalError("No such quest for you my friend")

        if (cookieData == null) {
            // TODO: We need to have special page for this to explain. Imagine if someone,
            // scans the qr code found by accident.
            throw StoryError("You need to start from the first quest! Go at coordinates: ${quest.location.lat}, ${quest.location.lon}")
        }

        val locationReading = LocationReading.fromString(locationString)
        checkIsFresh(locationReading)
        assertProximity(quest, locationReading.toCoordinates())

        val cookie = cookieManager.fromWebCookie(cookieData)
        assertEqual(cookie.scenario, scenario, "Bad cookie scenario")
        assertEqual(cookie.quest, questToStart - 1, "Bad cookie quest")

        var updatedCookie = cookie.copy(
            createdAt = now(),
            quest = questToStart,
            expiresAt = now().plusSeconds(quest.countdown)
        )
        response.addCookie(cookieManager.toWebCookie(updatedCookie))
        response.sendRedirect(countdownPage(now().plusSeconds(quest.countdown).toEpochSecond(), quest.location))
    }

    @GetMapping("/engine/complete/{scenario}/{quest}/{secret}/{location}")
    @ResponseBody
    fun process(
        @CookieValue(COOKIE_NAME) cookieData: String?,
        @PathVariable("scenario") scenario: String,
        @PathVariable("quest") questOrder: Int,
        @PathVariable("secret") secret: String,
        @PathVariable("location") locationString: String,
        response: HttpServletResponse
    ) {
        val quest = loader
            .load()
            .scenarios.find { it.name == scenario }
            ?.quests
            ?.find { it.order == questOrder }
            ?.takeIf { it.secret == secret }
            ?: throw TechnicalError("No such quest for you my friend")

        if (cookieData == null) {
            // TODO: We need to have special page for this to explain. Imagine if someone,
            // scans the qr code found by accident.
            throw StoryError("You need to start from the first quest! Go at coordinates: ${quest.location.lat}, ${quest.location.lon}")
        }

        val locationReading = LocationReading.fromString(locationString)
        checkIsFresh(locationReading)

        val nextPage = checkQuestCompletion(scenario, quest, locationReading.toCoordinates(), cookieManager.fromWebCookie(cookieData))

        response.sendRedirect(nextPage)
    }

    private fun checkQuestCompletion(scenario: String, quest: Quest, location: Coordinates, state: StateCookie): String {
        assertEqual(scenario, state.scenario, "scenario completion")
        assertEqual(quest.order, state.quest, "quest matching")
        assertProximity(quest, location)

        return if (now().isAfter(state.expiresAt)) {
            quest.failurePage
        } else {
            quest.successPage
        }
    }

    private fun assertProximity(quest: Quest, location: Coordinates) {
        if(!verifyLocation)
            return;

        var distance = distance(quest.location, location)
        if (distance > 500) {
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

    // Create new start scenario token, with unlimited time to complete the first quest (which is
    // actually where the user already is)
    // Preserving the userId if present
    private fun startAncientBlood(scenario: String, quest: Quest, cookieData: String?, response: HttpServletResponse) {
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
        return "/checkLocation.html?target=$questUrl"
    }

    private fun countdownPage(expiresAt: Long, location: Coordinates) =
        "/countdown.html?expiresAt=$expiresAt&lat=${location.lat}&lon=${location.lon}"
}
