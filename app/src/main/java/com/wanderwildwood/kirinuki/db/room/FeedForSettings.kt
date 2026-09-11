package com.wanderwildwood.kirinuki.db.room

import androidx.room.ColumnInfo
import androidx.room.Ignore
import com.wanderwildwood.kirinuki.db.COL_ID
import com.wanderwildwood.kirinuki.db.COL_NOTIFY
import com.wanderwildwood.kirinuki.db.COL_TITLE

data class FeedForSettings
    @Ignore
    constructor(
        @ColumnInfo(name = COL_ID) var id: Long = ID_UNSET,
        @ColumnInfo(name = COL_TITLE) var title: String = "",
        @ColumnInfo(name = COL_NOTIFY) var notify: Boolean = false,
    ) {
        constructor() : this(id = ID_UNSET)
    }
