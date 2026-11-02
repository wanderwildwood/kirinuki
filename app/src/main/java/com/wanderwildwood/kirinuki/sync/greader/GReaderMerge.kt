package com.wanderwildwood.kirinuki.sync.greader

/**
 * Read and starred, both ways, decided against the last state both sides agreed on.
 *
 * Every article that came from the server keeps a base: read and starred as they stood the
 * last time phone and server matched. A side whose value differs from the base has changed
 * it since. That base is the offline queue: a change made with no network stays a
 * difference until a sync gets it to the server, however many syncs fail first.
 *
 * Read and starred are each one yes or no, so the two sides can never have changed the same
 * thing in opposite directions: from one base there is only one other value. If both
 * changed, they agree. The phone's changes go first and the server's are applied after, so
 * whichever changed last since the base is what both end up holding.
 */
object GReaderMerge {
    data class ItemState(
        val itemId: Long,
        val remoteId: String,
        val baseRead: Boolean,
        val baseStarred: Boolean,
        val localRead: Boolean,
        val localStarred: Boolean,
    )

    /** What the phone has changed since the base, as four lists of server ids. */
    data class Push(
        val markRead: List<String>,
        val markUnread: List<String>,
        val star: List<String>,
        val unstar: List<String>,
    ) {
        val isEmpty: Boolean get() = markRead.isEmpty() && markUnread.isEmpty() && star.isEmpty() && unstar.isEmpty()
    }

    /** One article whose phone state should change to the server's; null leaves that half alone. */
    data class Apply(
        val itemId: Long,
        val read: Boolean?,
        val starred: Boolean?,
    )

    fun push(items: List<ItemState>): Push =
        Push(
            markRead = items.filter { it.localRead && !it.baseRead }.map { it.remoteId },
            markUnread = items.filter { !it.localRead && it.baseRead }.map { it.remoteId },
            star = items.filter { it.localStarred && !it.baseStarred }.map { it.remoteId },
            unstar = items.filter { !it.localStarred && it.baseStarred }.map { it.remoteId },
        )

    /** The items once [push] has reached the server: phone and base agree again. */
    fun afterPush(items: List<ItemState>): List<ItemState> =
        items.map { it.copy(baseRead = it.localRead, baseStarred = it.localStarred) }

    /**
     * What the server has changed since the base, for items the phone has not changed
     * (after a push, none have). An item missing from [unreadIds] is read on the server.
     */
    fun pull(
        items: List<ItemState>,
        unreadIds: Set<String>,
        starredIds: Set<String>,
    ): List<Apply> =
        items.mapNotNull { item ->
            val serverRead = item.remoteId !in unreadIds
            val serverStarred = item.remoteId in starredIds
            val read = serverRead.takeIf { item.localRead == item.baseRead && it != item.baseRead }
            val starred = serverStarred.takeIf { item.localStarred == item.baseStarred && it != item.baseStarred }
            if (read == null && starred == null) null else Apply(item.itemId, read, starred)
        }
}
