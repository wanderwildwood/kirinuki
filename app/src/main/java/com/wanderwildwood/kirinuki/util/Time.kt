package com.wanderwildwood.kirinuki.util

import java.time.Instant
import java.time.temporal.ChronoUnit

fun Instant.minusMinutes(minutes: Int): Instant = minus(minutes.toLong(), ChronoUnit.MINUTES)
