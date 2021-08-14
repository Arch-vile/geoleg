package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.model.TechnicalError
import com.nakoradio.geoleg.services.ScenarioLoader
import javax.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.ModelAndView

const val COOKIE_NAME = "yummy"

/*
Handles the scanned QR codes
 */
@Controller
class QRController(
    val loader: ScenarioLoader
) {

    var logger: Logger = LoggerFactory.getLogger(this::class.java)

    val scenario = loader.table.scenarios[0]
    val scenarioName = scenario.name
    val quest0Secret = scenario.quests[0].secret
    val quest1Secret = scenario.quests[1].secret
    val quest2Secret = scenario.quests[2].secret
    val quest3Secret = scenario.quests[3].secret
    val quest4Secret = scenario.quests[4].secret
    val quest5Secret = scenario.quests[5].secret
    val quest6Secret = scenario.quests[6].secret
    val quest7Secret = scenario.quests[7].secret
    val quest8Secret = scenario.quests[8].secret
    val quest9Secret = scenario.quests[9].secret

    // Codes are random strings to avoid guessing and for flexible replacing
    private val QR_CODE_MAPPING = mapOf(
        // Scenario start/reset
        "gtf92jgf" to "/engine/init/$scenarioName/$quest0Secret",
        // Kyltti
        "9xs4tu3v" to "/engine/complete/$scenarioName/1/$quest1Secret",
        // Silta
        "4c8czet6xxxxxxxxxx" to "/engine/complete/$scenarioName/2/$quest2Secret",
        // Kuusi
        "6frvz9m6xxxxxxx" to "/engine/complete/$scenarioName/3/$quest3Secret",
        // Kallio
        "kv96gnwe" to "/engine/complete/$scenarioName/4/$quest4Secret",
        // Kelo
        "6ecp98eu" to "/engine/complete/$scenarioName/5/$quest5Secret",
        // Sähkötolppa
        "vyrusmvm" to "/engine/complete/$scenarioName/6/$quest6Secret",
        // Helikopteri
        "2djzavs8" to "/engine/complete/$scenarioName/7/$quest7Secret",
        // Kallio
        "kv96gnwe" to "/engine/complete/$scenarioName/8/$quest8Secret",
        // Siilo
        "hj7hujue" to "/engine/complete/$scenarioName/9/$quest9Secret",
        "xxxxx" to "/compatibility/qr?qr=ok"
    )

    @GetMapping("/manualqr")
    fun manual(): ModelAndView {
        return ModelAndView("manualQR")
    }

    @GetMapping("/qr/{qrCode}")
    fun processCode(
        @PathVariable qrCode: String,
        response: HttpServletResponse
    ) {
        QR_CODE_MAPPING[qrCode]?.run {
            logger.info("QR code $qrCode redirecting to $this")
            response.sendRedirect(this)
        } ?: run {
            throw TechnicalError("Unknown QR code $qrCode")
        }
    }
}
