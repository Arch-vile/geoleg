package com.nakoradio.geoleg.model

data class HallOfFame(val results: Map<String, Result>)

data class Result(val timeInSeconds: Long, val scenario: String, val nickName: String)
