package com.wanderwildwood.kirinuki.model

import androidx.room.ColumnInfo
import androidx.room.Ignore
import com.wanderwildwood.kirinuki.db.room.ID_UNSET
import com.wanderwildwood.kirinuki.model.FeedIdTag
import java.net.URL

data class FeedUnreadCount
    @Ignore
    constructor(
        override var id: Long = ID_UNSET,
        @ColumnInfo(name = "display_title")
        var displayTitle: String = "",
        override var tag: String = "",
        @ColumnInfo(name = "image_url") var imageUrl: URL? = null,
        @ColumnInfo(name = "unread_count") var unreadCount: Int = 0,
        var expanded: Boolean = false,
    ) : FeedIdTag {
        constructor() : this(id = ID_UNSET)
    }
