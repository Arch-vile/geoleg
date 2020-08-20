package com.nakoradio.geoleg.controllers

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody

/*
Handles the scanned QR codes
 */
@Controller
class QRController {


    @GetMapping("/qr/{qrCode}")
    @ResponseBody
    fun processCode(@PathVariable("qrCode") qrCode: String) = "hello there $qrCode"

}