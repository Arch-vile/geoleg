package com.nakoradio.geoleg.controllers

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import javax.servlet.http.HttpServletResponse

const val COOKIE_NAME = "yummy"

/*
Handles the scanned QR codes
 */
@Controller
class QRController() {

    private val QR_CODE_MAPPING = mapOf(
            "2e910ca65a107421" to "/engine/ancient-blood/1/init?secret=fd32c86119df9ea7",
            "a77e677275f1d5bf" to "/engine/ancient-blood/",
            "5f47fb7bd175f3fa" to "/engine/ancient-blood/",
            "55a20ef6c20eb34d" to "/engine/ancient-blood/",
            "1767c3c0e11b500c" to "/engine/ancient-blood/",
            "d6ae79f3091b4586" to "/engine/ancient-blood/",
            "7f865c66f0881510" to "/engine/ancient-blood/",
            "9fc219ddea992ea2" to "/engine/ancient-blood/",
            "a1cc85ec13238523" to "/engine/ancient-blood/",
            "7c5a22bebf288eaf" to "/engine/ancient-blood/"
    )

    // TODO: Handle code mismatch
    @GetMapping("/qr/{qrCode}")
    fun processCode(
            @PathVariable("qrCode") qrCode: String,
            response: HttpServletResponse) =
            response.sendRedirect(QR_CODE_MAPPING[qrCode])

}