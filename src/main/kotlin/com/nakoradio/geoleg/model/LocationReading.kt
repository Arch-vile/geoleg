package com.nakoradio.geoleg.model

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

data class LocationReading(val lat: Double, val lon: Double, val createdAt: OffsetDateTime) {
    fun toCoordinates() = Coordinates(lat, lon)

    companion object {

        const val ceaserSource = "cdef01234567890-ab"
        const val ceaserTarget = "0123456789abcdef.;"

        @JvmStatic
        fun fromString(locationString: String): LocationReading {
            var encypted = ""
            for (element in locationString) {
                encypted += ceaserTarget[ceaserSource.indexOf(element)]
            }

            var splitted = encypted.split(';')
            var instant = Instant.ofEpochMilli(splitted[2].toLong())
            return LocationReading(
                splitted[0].toDouble(),
                splitted[1].toDouble(),
                OffsetDateTime.ofInstant(instant, ZoneId.of("UTC"))
            )
        }
    }
}
