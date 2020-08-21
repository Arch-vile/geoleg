package com.nakoradio.geoleg.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.nakoradio.geoleg.controllers.COOKIE_NAME
import com.nakoradio.geoleg.model.StateCookie
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID
import javax.servlet.http.Cookie

@Service
class CookieManager(val cryptor: Cryptor, val jsonMapper: ObjectMapper) {

    fun cookieFrom(data: String): StateCookie {
        return StateCookie(
                "some scenario",
                222,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                UUID.randomUUID()
        )
    }

    fun toWebCookie(cookie: StateCookie): Cookie {
        val json = jsonMapper.writeValueAsString(cookie)
        val encrypted = cryptor.aesEncrypt(json)
        return Cookie(COOKIE_NAME, encrypted)
    }

}