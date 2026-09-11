package com.wanderwildwood.kirinuki.db.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wanderwildwood.kirinuki.db.COL_GLOB_PATTERN
import com.wanderwildwood.kirinuki.db.COL_ID

@Entity(
    tableName = "blocklist",
    indices = [
        Index(value = [COL_GLOB_PATTERN], unique = true),
    ],
)
data class BlocklistEntry
    @Ignore
    constructor(
        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = COL_ID)
        var id: Long = ID_UNSET,
        @ColumnInfo(name = COL_GLOB_PATTERN) var globPattern: String = "",
    ) {
        constructor() : this(id = ID_UNSET)
    }
