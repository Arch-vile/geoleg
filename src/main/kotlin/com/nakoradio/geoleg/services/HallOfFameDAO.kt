package com.nakoradio.geoleg.services

import com.nakoradio.geoleg.model.Result
import com.nakoradio.geoleg.model.State
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class HallOfFameDAO(val redis: StringRedisTemplate) {

    val redisKey = "/hallOfFame/results"

    fun create(state: State, result: Result) {
        redis.opsForHash<String, String>()
            .put(redisKey, asResultKey(state, result), serialize(result))
    }

    // What is the simplest thing that could possibly work? Custom serialization of course.
    private fun serialize(result: Result): String {
        // FIXME: Nick should be html escaped to avoid XSS
        return "${result.timeInSeconds}&${result.scenario}&${result.nickName}"
    }

    private fun deserialize(it: List<String>) =
        Result(it[0].toLong(), it[1], it[2])

    // Using all these terms in the key will allow us to avoid not to worry about double submitting
    // as the key will be the same based on the state for the same run.
    private fun asResultKey(state: State, result: Result) =
        "${state.userId}/${state.scenario}/${state.scenarioRestartCount}/${result.timeInSeconds}"

    fun list(): List<Result> {
     return   redis.opsForHash<String,String>()
            .values(redisKey)
            .map { it.split("&") }
            .map { deserialize(it) }
    }

}