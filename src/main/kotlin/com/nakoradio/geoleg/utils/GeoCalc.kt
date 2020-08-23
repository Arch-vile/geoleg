package com.nakoradio.geoleg.utils

import com.grum.geocalc.Coordinate
import com.grum.geocalc.EarthCalc
import com.grum.geocalc.Point
import com.nakoradio.geoleg.model.Coordinates

typealias Meter = Double

fun distance(point1: Coordinates, point2: Coordinates): Meter {
    var currentLocation = Point.at(
        Coordinate.fromDegrees(point1.lat),
        Coordinate.fromDegrees(
            point1.lon
        )
    )
    var targetLocation = Point.at(
        Coordinate.fromDegrees(point2.lat),
        Coordinate.fromDegrees(point2.lon)
    )
    return EarthCalc.vincentyDistance(currentLocation, targetLocation)
}
