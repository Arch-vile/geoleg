package com.nakoradio.geoleg.servlet

import com.nakoradio.geoleg.controllers.COOKIE_NAME
import com.nakoradio.geoleg.model.State
import com.nakoradio.geoleg.services.CookieManager
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import org.slf4j.MDC
import org.springframework.stereotype.Component

@Component
class MDCFilter(val cookieManager: CookieManager) : Filter {

    override fun doFilter(request: ServletRequest?, response: ServletResponse?, next: FilterChain?) {
        val httpRequest = request as HttpServletRequest

        val cookieData = httpRequest.cookies?.find { it.name == COOKIE_NAME }?.value
        val cookie = cookieData?.let {
            cookieManager.fromWebCookie(cookieData)
        }

        val context = LogContext(httpRequest.requestURI, cookie)

        MDC.put("logContext", context.toString())
        next!!.doFilter(request, response)
    }
}

data class LogContext(val URI: String, val cookie: State?)
