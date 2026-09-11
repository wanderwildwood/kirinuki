package com.wanderwildwood.kirinuki.db.room

import androidx.room.ColumnInfo
import androidx.room.Ignore
import com.wanderwildwood.kirinuki.db.COL_ID
import com.wanderwildwood.kirinuki.db.COL_LINK

data class FeedItemIdWithLink
    @Ignore
    constructor(
        @ColumnInfo(name = COL_ID) override var id: Long = ID_UNSET,
        @ColumnInfo(name = COL_LINK) override var link: String? = null,
    ) : FeedItemForFetching {
        constructor() : this(id = ID_UNSET)
    }
