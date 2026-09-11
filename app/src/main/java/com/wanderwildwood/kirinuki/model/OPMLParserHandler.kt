package com.wanderwildwood.kirinuki.model

import com.wanderwildwood.kirinuki.db.room.Feed

interface OPMLParserHandler {
    suspend fun saveFeed(feed: Feed)

    suspend fun saveSetting(
        key: String,
        value: String,
    )

    suspend fun saveBlocklistPatterns(patterns: Iterable<String>)
}
