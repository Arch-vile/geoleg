package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.model.TechnicalError
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

const val COOKIE_NAME = "yummy"

/*
Handles the scanned QR codes
 */
@Controller
class QRController() {

    var logger: Logger = LoggerFactory.getLogger(this::class.java)

    // Codes are random strings to avoid guessing and for flexible replacing
    private val QR_CODE_MAPPING = mapOf(
        "2e910ca65a107421" to "/engine/init/ancient-blood/656a0b0924da",
        "a77e677275f1d5bf" to "/engine/complete/ancient-blood/2/138d0d22b893",
        "5f47fb7bd175f3fa" to "/engine/complete/ancient-blood/3/a41e1e8068c3",
        "55a20ef6c20eb34d" to "/engine/ancient-blood/",
        "1767c3c0e11b500c" to "/engine/ancient-blood/",
        "d6ae79f3091b4586" to "/engine/ancient-blood/",
        "7f865c66f0881510" to "/engine/ancient-blood/",
        "9fc219ddea992ea2" to "/engine/ancient-blood/",
        "a1cc85ec13238523" to "/engine/ancient-blood/",
        "f2pvf2np" to "/compatibility.html?qr=ok"
    )

    @GetMapping("/qr/{qrCode}")
    fun processCode(
        @PathVariable("qrCode") qrCode: String,
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
