package com.wanderwildwood.kirinuki.sync.greader

/**
 * What the sync needs from a feed server, whichever kind it is: the Google Reader API
 * ([GReaderApi]: FreshRSS, Miniflux) or Nextcloud News' own API
 * ([com.wanderwildwood.kirinuki.sync.nextcloud.NextcloudNewsApi]). [GReaderSync] runs the same
 * steps against either, so both kinds keep the same guarantees (docs/SYNC.md).
 *
 * Every call blocks and throws [GReaderException], whose cases are not specific to the Google
 * Reader API: unreachable, wrong password, not this kind of server, insecure, server error.
 */
interface FeedServerApi {
    /** Logs in if not already in. */
    fun login()

    /** Every subscription, with its server id ("stream"), address, name and folder. */
    fun subscriptions(): List<RemoteFeed>

    fun unsubscribe(streamId: String)

    /** Files [streamId] under [label] (blank for none), taking it out of [oldLabel]. */
    fun setLabel(
        streamId: String,
        label: String,
        oldLabel: String,
    )

    /** Subscribes to [url]; the server's stream id for it, or null if it would not. */
    fun quickAdd(url: String): String?

    /** Sets [tag] ([GReaderIds.READ] or [GReaderIds.STARRED]) on, or with [add] false off, every one of [ids]. */
    fun editTag(
        ids: List<String>,
        tag: String,
        add: Boolean,
    )

    /**
     * The server's side of read and starred, after the phone's changes have been sent.
     * [known] is every article the phone holds from this server, with the state both sides
     * now agree on; [since] is when to look back to for new and changed articles;
     * [newStreams] are the feeds linked in this sync, whose older articles the phone has not
     * seen. A server whose API lists ids cheaply can ignore the last two and list everything.
     */
    fun serverItems(
        known: List<GReaderMerge.ItemState>,
        since: Long,
        newStreams: Set<String>,
    ): ServerItems

    /** The articles for [ids]; ids it cannot give are left out. */
    fun contents(ids: List<String>): List<RemoteItem>
}

/**
 * The server's read and starred state, as the merge wants it: every unread id (newest
 * first), every starred id, and the ids new or changed since the last sync.
 */
data class ServerItems(
    val unread: List<String>,
    val starred: List<String>,
    val recent: List<String>,
)
