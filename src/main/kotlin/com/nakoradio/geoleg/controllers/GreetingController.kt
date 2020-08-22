package com.nakoradio.geoleg.controllers

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class GreetingController {

    @GetMapping("/greeting")
    fun greeting(model: Model): String {
        model.addAttribute("name", "mikko")
        return "greeting"
    }
}