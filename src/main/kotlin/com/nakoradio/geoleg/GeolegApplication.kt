package com.nakoradio.geoleg

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
class GeolegApplication

fun main(args: Array<String>) {
    runApplication<GeolegApplication>(*args)
}

