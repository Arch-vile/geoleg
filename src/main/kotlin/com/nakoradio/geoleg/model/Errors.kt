package com.nakoradio.geoleg.model

import java.lang.RuntimeException

open class CustomError(message: String) : RuntimeException(message)

class TechnicalError(message: String) : CustomError(message)

class StoryError(message: String) : CustomError(message)