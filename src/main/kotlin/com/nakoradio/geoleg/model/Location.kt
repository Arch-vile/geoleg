package com.nakoradio.geoleg.model

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

data class Location(val lat: Double, val lon: Double, val createdAt: OffsetDateTime) {

    companion object {

        const val ceaserSource = "cdef01234567890-ab";
        const val ceaserTarget = "0123456789abcdef.;";

        @JvmStatic
        fun fromString(locationString: String): Location {

            var encypted = ""
            for(element in locationString) {
               encypted += ceaserTarget[ceaserSource.indexOf(element)]
            }

            print(encypted);

            var splitted = encypted.split(';')
            var instant = Instant.ofEpochMilli(splitted[2].toLong())
            return Location(splitted[0].toDouble(),
                    splitted[1].toDouble(),
                    OffsetDateTime.ofInstant(instant, ZoneId.of("UTC")))


        }
    }


}


