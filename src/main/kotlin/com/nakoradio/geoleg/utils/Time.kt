package com.nakoradio.geoleg.utils

import java.time.OffsetDateTime
import org.springframework.stereotype.Service

@Service
class Time {
    fun now(): OffsetDateTime = OffsetDateTime.now()
}
