package com.wanderwildwood.kirinuki.model

import androidx.compose.runtime.Immutable

/**
 * Which cuttings the list shows. Unread by default: the rest is still there,
 * it is simply not what you opened the app to look at.
 */
@Immutable
interface FeedListFilter {
    val unread: Boolean
    val saved: Boolean
    val recentlyRead: Boolean
    val read: Boolean
}

val emptyFeedListFilter =
    object : FeedListFilter {
        override val unread: Boolean = true
        override val saved: Boolean = false
        override val recentlyRead: Boolean = false
        override val read: Boolean = false
    }
