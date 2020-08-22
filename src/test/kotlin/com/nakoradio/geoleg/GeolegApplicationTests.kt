package com.nakoradio.geoleg

import java.time.OffsetDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Test

class GeolegApplicationTests {

    @Test
    fun contextLoads() {
        println("sdfsdf")

        val now = OffsetDateTime.now(ZoneId.of("UTC"))
//        val zone = ZoneId.of("Europe/Helsinki")
//        var fff = ZoneOffset.of(zone.id)
//        var withOffsetSameInstant = now.withOffsetSameInstant(ZoneOffset.of("Europe/Helsinki"))
//        print(withOffsetSameInstant)
        println(now)
        println(now.atZoneSameInstant(ZoneId.of("Europe/Helsinki")))
    }
}
