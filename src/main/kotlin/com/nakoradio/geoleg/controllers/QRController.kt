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
    val quest4Secret = scenario.quests[4].secret
    val quest5Secret = scenario.quests[5].secret
    val quest6Secret = scenario.quests[6].secret
    val quest7Secret = scenario.quests[7].secret
    val quest8Secret = scenario.quests[8].secret
    val quest9Secret = scenario.quests[9].secret

    // Codes are random strings to avoid guessing and for flexible replacing
    private val QR_CODE_MAPPING = mapOf(
        "6ecp98eu" to "/engine/init/$scenarioName/$quest0Secret",
        "snwxfqgj" to "/engine/complete/$scenarioName/1/$quest1Secret",
        "4c8czet6" to "/engine/complete/$scenarioName/2/$quest2Secret",
        "6frvz9m6" to "/engine/complete/$scenarioName/3/$quest3Secret",
        "2djzavs8" to "/engine/complete/$scenarioName/4/$quest4Secret",
        "9xs4tu3v" to "/engine/complete/$scenarioName/5/$quest5Secret",
        "b94gz2hy" to "/engine/complete/$scenarioName/6/$quest6Secret",
        "kv96gnwe" to "/engine/complete/$scenarioName/7/$quest7Secret",
        "48nemd5w" to "/engine/complete/$scenarioName/8/$quest8Secret",
        "u6xuv5rv" to "/engine/complete/$scenarioName/9/$quest9Secret",
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
