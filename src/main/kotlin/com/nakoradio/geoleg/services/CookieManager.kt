package com.nakoradio.geoleg.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.nakoradio.geoleg.controllers.COOKIE_NAME
import com.nakoradio.geoleg.model.State
import javax.servlet.http.Cookie
import org.springframework.stereotype.Service

@Service
class CookieManager(val cryptor: Cryptor, val jsonMapper: ObjectMapper) {

    fun toWebCookie(cookie: State): Cookie {
        val json = jsonMapper.writeValueAsString(cookie)
        val encrypted = cryptor.aesEncrypt(json)
        var webCookie = Cookie(COOKIE_NAME, encrypted)
        webCookie.path = "/"
        // One day
        webCookie.maxAge = 60 * 60 * 24 * 365 * 10;
        return webCookie
    }

    fun fromWebCookieMaybe(cookieData: String?): State? =
            if(cookieData == null) null else fromWebCookie(cookieData)

    fun fromWebCookie(cookieData: String): State =
        jsonMapper.readValue(cryptor.aesDecrypt(cookieData), State::class.java)
}
