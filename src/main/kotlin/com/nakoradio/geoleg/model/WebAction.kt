package com.nakoradio.geoleg.model

data class WebAction(val modelAndView: ModelView, val state: State)

data class ModelView(val view: String, val model: Map<String,Any>)
