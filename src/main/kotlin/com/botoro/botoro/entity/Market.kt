package com.botoro.botoro.entity

import java.time.Instant

data class Market(
    var id: String,
    var buy: Double,
    var sell: Double,
    var time: Instant,
)
