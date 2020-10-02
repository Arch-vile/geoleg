package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.model.TechnicalError
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
class QRController {

    var logger: Logger = LoggerFactory.getLogger(this::class.java)

    // Codes are random strings to avoid guessing and for flexible replacing
    private val QR_CODE_MAPPING = mapOf(
        "6ecp98eu" to "/engine/init/ancient-blood/6a5fc6c0f8ec",
        "snwxfqgj" to "/engine/complete/ancient-blood/1/656a0b0924da",
        "4c8czet6" to "/engine/complete/ancient-blood/2/138d0d22b893",
        "6frvz9m6" to "/engine/complete/ancient-blood/3/a41e1e8068c3",
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
