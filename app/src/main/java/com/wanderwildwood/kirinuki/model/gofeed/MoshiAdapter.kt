package com.wanderwildwood.kirinuki.model.gofeed

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

/**
 * Kept from the sync package that went with device sync: the Go feed parser still
 * speaks JSON, and this is the one line of it that was doing any work.
 */
inline fun <reified T> Moshi.adapter(): JsonAdapter<T> = adapter(T::class.java)
