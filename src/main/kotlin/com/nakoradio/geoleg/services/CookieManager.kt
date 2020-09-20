package com.nakoradio.geoleg.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.nakoradio.geoleg.controllers.COOKIE_NAME
import com.nakoradio.geoleg.model.State
import com.nakoradio.geoleg.utils.now
import java.time.OffsetDateTime
import java.util.UUID
import javax.servlet.http.Cookie
import org.springframework.stereotype.Service

@Service
class CookieManager(val cryptor: Cryptor, val jsonMapper: ObjectMapper) {

    fun toWebCookie(cookie: State): Cookie {
        val json = jsonMapper.writeValueAsString(cookie)
        val encrypted = cryptor.aesEncrypt(json)
        var webCookie = Cookie(COOKIE_NAME, encrypted)
        webCookie.path = "/"
        return webCookie
    }

    fun fromWebCookie(cookieData: String): State =
        jsonMapper.readValue(cryptor.aesDecrypt(cookieData), State::class.java)

    fun updateOrCreate(cookieData: String?, scenario: String, leg: Int, expiresAt: OffsetDateTime): State {
        val existingCookie = cookieData?.run { fromWebCookie(cookieData) }
        return State(
            scenario,
            leg,
            expiresAt,
            now(),
            existingCookie?.userId ?: UUID.randomUUID(),
            (existingCookie?.scenarioRestartCount ?: 0) + 1
        )
    }
}
