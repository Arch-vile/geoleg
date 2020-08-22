package com.nakoradio.geoleg.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.nakoradio.geoleg.controllers.COOKIE_NAME
import com.nakoradio.geoleg.model.StateCookie
import java.time.OffsetDateTime
import java.util.UUID
import javax.servlet.http.Cookie
import org.springframework.stereotype.Service

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
        var webCookie = Cookie(COOKIE_NAME, encrypted)
        webCookie.path = "/"
        return webCookie
    }

    fun fromWebCookie(cookieData: String) =
        jsonMapper.readValue(cryptor.aesDecrypt(cookieData), StateCookie::class.java);

}
