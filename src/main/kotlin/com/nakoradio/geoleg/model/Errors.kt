package com.nakoradio.geoleg.model

import java.lang.RuntimeException

open class CustomError(message: String) : RuntimeException(message)

class TechnicalError(message: String) : CustomError(message)

class MissingCookieError(val lat: Double, val lon: Double) : CustomError("Cookie was missing")
