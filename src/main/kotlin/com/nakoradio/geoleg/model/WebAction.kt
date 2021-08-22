package com.nakoradio.geoleg.model

import com.nakoradio.geoleg.services.ViewModel

// FIXME: Let's make state not optional here
// Afterwards remove all `state!!` things not needed anymore, atlest in EngineTest
data class WebAction(val modelAndView: ViewModel, val state: State?)
