package com.wanderwildwood.kirinuki.sync.greader

/*
 * The JSON shapes of the Google Reader API as FreshRSS and Miniflux answer it
 * (`output=json`). Every field is optional: servers leave out what they do not have.
 * Shapes follow Read You's GoogleReaderDTO (https://github.com/Ashinch/ReadYou, GPL-3.0)
 * and the FreshRSS and Miniflux documentation (docs/SYNC.md).
 */

data class GrLoginJson(
    val SID: String? = null,
    val LSID: String? = null,
    val Auth: String? = null,
)

data class GrSubscriptionList(
    val subscriptions: List<GrSubscription>? = null,
)

data class GrSubscription(
    val id: String? = null,
    val title: String? = null,
    val categories: List<GrCategory>? = null,
    val url: String? = null,
    val htmlUrl: String? = null,
    val iconUrl: String? = null,
)

data class GrCategory(
    val id: String? = null,
    val label: String? = null,
)

data class GrTagList(
    val tags: List<GrTag>? = null,
)

data class GrTag(
    val id: String? = null,
    val type: String? = null,
)

data class GrItemRef(
    val id: String? = null,
)

data class GrItemIds(
    val itemRefs: List<GrItemRef>? = null,
    val continuation: String? = null,
)

data class GrContents(
    val id: String? = null,
    val updated: Long? = null,
    val items: List<GrItem>? = null,
    val continuation: String? = null,
)

data class GrItem(
    val id: String? = null,
    val crawlTimeMsec: String? = null,
    val timestampUsec: String? = null,
    val published: Long? = null,
    val updated: Long? = null,
    val title: String? = null,
    val summary: GrText? = null,
    val content: GrText? = null,
    val categories: List<String>? = null,
    val origin: GrOrigin? = null,
    val author: String? = null,
    val canonical: List<GrLink>? = null,
    val alternate: List<GrLink>? = null,
    val enclosure: List<GrLink>? = null,
)

data class GrText(
    val content: String? = null,
)

data class GrOrigin(
    val streamId: String? = null,
    val htmlUrl: String? = null,
    val title: String? = null,
)

data class GrLink(
    val href: String? = null,
    val type: String? = null,
)

data class GrQuickAdd(
    val numResults: Long? = null,
    val query: String? = null,
    val streamId: String? = null,
    val streamName: String? = null,
)

/** One subscription as the app uses it: the server's id for it, its address, name and folder. */
data class RemoteFeed(
    val streamId: String,
    val url: String,
    val title: String,
    val label: String,
)

/** One article from the server, ready to become a row. */
data class RemoteItem(
    val id: String,
    val feedStreamId: String,
    val title: String,
    val link: String?,
    val html: String,
    val author: String?,
    val publishedEpochSeconds: Long?,
    val enclosureUrl: String?,
    val enclosureType: String?,
)

fun GrSubscription.toRemoteFeed(): RemoteFeed? {
    val streamId = id ?: return null
    val address = url ?: htmlUrl ?: return null
    return RemoteFeed(
        streamId = streamId,
        url = address,
        title = title.orEmpty(),
        label = categories?.firstNotNullOfOrNull { it.label ?: GReaderIds.labelName(it.id) }.orEmpty(),
    )
}

fun GrItem.toRemoteItem(): RemoteItem? {
    val longId = id?.let { GReaderIds.toLong(it) } ?: return null
    val stream = origin?.streamId ?: return null
    val audio = enclosure?.firstOrNull { it.type?.startsWith("audio/") == true } ?: enclosure?.firstOrNull()
    return RemoteItem(
        id = longId,
        feedStreamId = stream,
        title = title.orEmpty(),
        link = canonical?.firstOrNull()?.href ?: alternate?.firstOrNull()?.href,
        html = content?.content ?: summary?.content ?: "",
        author = author?.takeIf { it.isNotBlank() },
        publishedEpochSeconds = published ?: crawlTimeMsec?.toLongOrNull()?.div(1000),
        enclosureUrl = audio?.href,
        enclosureType = audio?.type,
    )
}
