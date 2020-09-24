package com.nakoradio.geoleg.model

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import org.slf4j.Logger
import org.slf4j.LoggerFactory

data class LocationReading(val lat: Double, val lon: Double, val createdAt: OffsetDateTime) {

    fun toCoordinates() = Coordinates(lat, lon)
    fun asString(): String {
        val locationString = "" + lat + ';' + lon + ';' + createdAt.toEpochSecond() * 1000
        var output = ""
        for (i in locationString) {
            output += ceaserSource[ceaserTarget.indexOf(i)]
        }
        return output
    }

    companion object {

        var logger: Logger = LoggerFactory.getLogger(this::class.java)
        const val ceaserSource = "cdef01234567890-ab"
        const val ceaserTarget = "0123456789abcdef.;"

        @JvmStatic
        fun fromString(locationString: String): LocationReading {
            try {
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
            } catch (e: Exception) {
                logger.error("Error parsing location", e)
                throw TechnicalError("Alas, something went wrong")
            }
        }
    }
}
