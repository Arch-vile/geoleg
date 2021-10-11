package com.nakoradio.geoleg.controllers

import com.nakoradio.geoleg.controllers.EngineController.Companion.processWebView
import com.nakoradio.geoleg.services.CountdownViewModel
import com.nakoradio.geoleg.services.LocationReadingViewModel
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * MVC model requires us to route each request through controller. This controller is used
 * to provide those dummy endpoints that just show the simple page
 */
@Controller
class ThymeleafController {

    // Stupid proxy for rendering the view
    @GetMapping("/checkLocation")
    fun checkLocation(viewModel: LocationReadingViewModel) = processWebView(viewModel)

    // Stupid proxy for rendering the view
    @GetMapping("/countdown")
    fun countdown(model: CountdownViewModel) = processWebView(model)

    @GetMapping("/geolocationInstructions")
    fun geolocationInstructions() = "geolocationInstructions"
}
