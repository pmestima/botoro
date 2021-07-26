package com.botoro.botoro

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BotoroApplication

fun main(args: Array<String>) {
    runApplication<BotoroApplication>(*args)
}

inline fun <reified T> logger(@Suppress("UNUSED_PARAMETER") from: T): Logger {
    return LoggerFactory.getLogger(T::class.java)
}
