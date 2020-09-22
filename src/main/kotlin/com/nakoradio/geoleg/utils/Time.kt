package com.nakoradio.geoleg.utils

import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class Time {
    fun now(): OffsetDateTime = OffsetDateTime.now()
}
