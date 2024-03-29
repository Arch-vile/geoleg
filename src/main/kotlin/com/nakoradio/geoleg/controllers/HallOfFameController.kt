package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.model.Result
import com.nakoradio.geoleg.model.State
import com.nakoradio.geoleg.services.CookieManager
import com.nakoradio.geoleg.services.HallOfFameDAO
import com.nakoradio.geoleg.services.HallOfFameFormViewModel
import com.nakoradio.geoleg.services.HallOfFameListViewModel
import com.nakoradio.geoleg.services.ScenarioLoader
import java.time.Duration
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.ModelAndView

@Controller
class HallOfFameController(
    val cookieManager: CookieManager,
    val loader: ScenarioLoader,
    val hallOfFameDAO: HallOfFameDAO
) {

    @GetMapping("/hallOfFame/{scenario}/list")
    @ResponseBody
    fun getHallOfFameList(
        @PathVariable scenario: String
    ) =
        EngineController.asModelAndView(HallOfFameListViewModel(resultsList(scenario)))

    @GetMapping("/hallOfFame/submit")
    @ResponseBody
    fun getHallOfFame(@CookieValue(COOKIE_NAME) cookieData: String): ModelAndView {
        val state = cookieManager.fromWebCookie(cookieData)
        return EngineController.asModelAndView(
            HallOfFameFormViewModel(
                timeToString(scenarioCompleteTime(state)),
                resultsList(state)
            )
        )
    }

    data class HallOfFameSubmitForm(val nickName: String)

    @PostMapping(path = ["/hallOfFame/submit"], consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    @ResponseBody
    fun recordHallOfFame(
        @CookieValue(COOKIE_NAME) cookieData: String,
        @ModelAttribute payload: HallOfFameSubmitForm
    ): ModelAndView {
        val state = cookieManager.fromWebCookie(cookieData)
        if (state.currentQuest != loader.findScenario(state.scenario).quests.last().order) {
            throw IllegalStateException("Current quest is not the last of the scenario")
        }

        if (state.questCompleted == null) {
            throw IllegalStateException("Scenario not completed")
        }

        val timeInSeconds = scenarioCompleteTime(state)
        hallOfFameDAO.create(state, Result(timeInSeconds, state.scenario, payload.nickName))
        return EngineController.asModelAndView(HallOfFameListViewModel(resultsList(state)))
    }

    private fun resultsList(state: State) = resultsList(state.scenario)

    private fun resultsList(scenario: String) =
        hallOfFameDAO.list()
            .filter { it.scenario == scenario }
            .sortedBy { it.timeInSeconds }
            .map { ResultForView(timeToString(it.timeInSeconds), it.nickName) }
            .toList()

    private fun scenarioCompleteTime(state: State) =
        state.questCompleted!!.toEpochSecond() - state.scenarioStarted.toEpochSecond()

    private fun timeToString(timeInSeconds: Long): String {
        val duration = Duration.ofSeconds(timeInSeconds)
        return String.format(
            "%02d:%02d:%02d",
            duration.toHoursPart(),
            duration.toMinutesPart(),
            duration.toSecondsPart()
        )
    }

    data class ResultForView(val time: String, val nick: String)
}
