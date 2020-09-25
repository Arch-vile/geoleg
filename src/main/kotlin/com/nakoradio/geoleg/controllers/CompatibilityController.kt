package com.nakoradio.geoleg.controllers

import java.util.UUID
import javax.servlet.http.Cookie
import javax.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

@Controller
class CompatibilityController {

    @GetMapping("/compatibility")
    fun setCookie(
        response: HttpServletResponse
    ): ModelAndView {
        return ModelAndView(
            "compatibility", "compatibility",
            CompatibilityModel(setCookieTestValue(response), "TBD")
        )
    }

    @GetMapping("/compatibility/qr")
    fun setQRCode(
        @RequestParam("qr") qr: String,
        response: HttpServletResponse
    ): ModelAndView {
        return ModelAndView(
            "compatibility", "compatibility",
            CompatibilityModel(setCookieTestValue(response), qr)
        )
    }

    private fun setCookieTestValue(response: HttpServletResponse): UUID {
        var random = UUID.randomUUID()
        response.addCookie(Cookie("testCookie", "$random"))
        return random
    }
}

data class CompatibilityModel(val expectedCookieValue: UUID, val qrCode: String)
