package com.wanderwildwood.kirinuki.util

import android.util.Log
import com.wanderwildwood.kirinuki.BuildConfig

fun logDebug(
    tag: String,
    msg: String,
    exception: Throwable? = null,
) {
    if (BuildConfig.DEBUG) {
        Log.d(tag, msg, exception)
    }
}
