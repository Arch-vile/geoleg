package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.model.TechnicalError
import com.nakoradio.geoleg.services.ScenarioLoader
import javax.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

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

    // Codes are random strings to avoid guessing and for flexible replacing
    private val QR_CODE_MAPPING = mapOf(
        "6ecp98eu" to "/engine/init/${scenarioName}/$quest0Secret",
        "snwxfqgj" to "/engine/complete/$scenarioName/1/$quest1Secret",
        "4c8czet6" to "/engine/complete/$scenarioName/2/$quest2Secret",
        "6frvz9m6" to "/engine/complete/$scenarioName/3/$quest3Secret",
        "1767c3c0e11b500c" to "/engine/x/",
        "d6ae79f3091b4586" to "/engine/x/",
        "7f865c66f0881510" to "/engine/x/",
        "9fc219ddea992ea2" to "/engine/x/",
        "a1cc85ec13238523" to "/engine/x/",
        "hj7hujue" to "/compatibility/qr?qr=ok"
    )

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
