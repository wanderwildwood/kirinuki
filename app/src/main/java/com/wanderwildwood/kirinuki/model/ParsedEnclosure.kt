package com.wanderwildwood.kirinuki.model

data class ParsedEnclosure(
    val url: String?,
    val mime_type: String? = null,
    val title: String? = null,
    val size_in_bytes: Long? = null,
    val duration_in_seconds: Long? = null,
)
