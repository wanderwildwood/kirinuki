package com.wanderwildwood.kirinuki.archmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.paging.PagingData
import com.wanderwildwood.kirinuki.ApplicationCoroutineScope
import com.wanderwildwood.kirinuki.background.runOnceBlocklistUpdate
import com.wanderwildwood.kirinuki.background.runOnceRssSync
import com.wanderwildwood.kirinuki.background.schedulePeriodicRssSync
import com.wanderwildwood.kirinuki.db.room.Feed
import com.wanderwildwood.kirinuki.db.room.FeedForSettings
import com.wanderwildwood.kirinuki.db.room.FeedItem
import com.wanderwildwood.kirinuki.db.room.FeedItemCursor
import com.wanderwildwood.kirinuki.db.room.FeedItemForReadMark
import com.wanderwildwood.kirinuki.db.room.FeedItemIdWithLink
import com.wanderwildwood.kirinuki.db.room.FeedItemWithFeed
import com.wanderwildwood.kirinuki.db.room.FeedTitle
import com.wanderwildwood.kirinuki.db.room.ID_ALL_FEEDS
import com.wanderwildwood.kirinuki.db.room.ID_SAVED_ARTICLES
import com.wanderwildwood.kirinuki.db.room.ID_UNSET
import com.wanderwildwood.kirinuki.db.room.RemoteFeed
import com.wanderwildwood.kirinuki.db.room.SyncDevice
import com.wanderwildwood.kirinuki.db.room.SyncRemote
import com.wanderwildwood.kirinuki.model.FeedUnreadCount
import com.wanderwildwood.kirinuki.model.ThumbnailImage
import com.wanderwildwood.kirinuki.model.FeedListItem
import com.wanderwildwood.kirinuki.model.FeedListFilter
import com.wanderwildwood.kirinuki.model.emptyFeedListFilter
import com.wanderwildwood.kirinuki.util.Either
import com.wanderwildwood.kirinuki.util.addDynamicShortcutToFeed
import com.wanderwildwood.kirinuki.util.reportShortcutToFeedUsed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class Repository(
    override val di: DI,
) : DIAware {
    private val settingsStore: SettingsStore by instance()
    private val sessionStore: SessionStore by instance()
    private val feedItemStore: FeedItemStore by instance()
    private val feedStore: FeedStore by instance()
    private val androidSystemStore: AndroidSystemStore by instance()
    private val applicationCoroutineScope: ApplicationCoroutineScope by instance()
    private val application: Application by instance()
    private val syncRemoteStore: SyncRemoteStore by instance()

    // Upstream subscribed every new install to its own news feed on first start.
    // That is inherited process rather than inherited code: a reader should open empty
    // and wait to be told what to read.

    val minReadTime: StateFlow<Instant> = settingsStore.minReadTime

    fun setMinReadTime(value: Instant) = settingsStore.setMinReadTime(value)

    val currentFeedAndTag: StateFlow<Pair<Long, String>> = settingsStore.currentFeedAndTag

    fun getUnreadCount(
        feedId: Long,
        tag: String = "",
    ) = feedItemStore.getFeedItemCountRaw(
        feedId = feedId,
        tag = tag,
        minReadTime = Instant.now(),
        filter = emptyFeedListFilter,
        search = "",
    )

    fun setCurrentFeedAndTag(
        feedId: Long,
        tag: String,
    ) {
        if (feedId > ID_UNSET) {
            applicationCoroutineScope.launch {
                application.apply {
                    addDynamicShortcutToFeed(
                        feedStore.getDisplayTitle(feedId) ?: "",
                        feedId,
                        null,
                    )
                    // Report shortcut usage
                    reportShortcutToFeedUsed(feedId)
                }
            }
        }
        if (settingsStore.setCurrentFeedAndTag(feedId, tag)) {
            setMinReadTime(Instant.now())
        }
    }

    suspend fun renameTag(
        oldTag: String,
        newTag: String,
    ) {
        feedStore.renameTag(oldTag, newTag)
        val (currentFeedId, currentTag) = currentFeedAndTag.value
        if (currentFeedId == ID_UNSET && currentTag == oldTag) {
            setCurrentFeedAndTag(ID_UNSET, newTag)
        }
    }

    val isArticleOpen: StateFlow<Boolean> = settingsStore.isArticleOpen

    fun setIsArticleOpen(open: Boolean) {
        settingsStore.setIsArticleOpen(open)
    }

    val isMarkAsReadOnScroll: StateFlow<Boolean> = settingsStore.isMarkAsReadOnScroll

    fun setIsMarkAsReadOnScroll(value: Boolean) {
        settingsStore.setIsMarkAsReadOnScroll(value)
    }

    val maxLines: StateFlow<Int> = settingsStore.maxLines

    fun setMaxLines(value: Int) {
        settingsStore.setMaxLines(value.coerceAtLeast(1))
    }

    val showOnlyTitle: StateFlow<Boolean> = settingsStore.showOnlyTitle

    fun setShowOnlyTitles(value: Boolean) {
        settingsStore.setShowOnlyTitles(value)
    }

    val feedListFilter: StateFlow<FeedListFilter> = settingsStore.feedListFilter

    fun setFeedListFilterSaved(value: Boolean) {
        settingsStore.setFeedListFilterSaved(value)
    }

    fun setFeedListFilterRecentlyRead(value: Boolean) {
        settingsStore.setFeedListFilterRecentlyRead(value)
        // Implies read too
        if (!value) {
            settingsStore.setFeedListFilterRead(false)
        }
    }

    fun setFeedListFilterRead(value: Boolean) {
        settingsStore.setFeedListFilterRead(value)
        // Implies recently read too
        if (value) {
            settingsStore.setFeedListFilterRecentlyRead(true)
        }
    }

    val search: MutableStateFlow<String> = MutableStateFlow("")

    fun searchFor(value: String) = search.update { value }

    val currentArticleId: StateFlow<Long> = settingsStore.currentArticleId

    fun setCurrentArticle(articleId: Long) = settingsStore.setCurrentArticle(articleId)

    suspend fun getCurrentArticle() = feedItemStore.getArticle(currentArticleId.value)

    fun getArticleFlow(itemId: Long): Flow<Article?> =
        feedItemStore
            .getFeedItem(itemId)
            .map { Article(it) }

    val currentTheme: StateFlow<ThemeOptions> = settingsStore.currentTheme

    fun setCurrentTheme(value: ThemeOptions) = settingsStore.setCurrentTheme(value)

    val preferredDarkTheme: StateFlow<DarkThemePreferences> = settingsStore.darkThemePreference

    fun setPreferredDarkTheme(value: DarkThemePreferences) = settingsStore.setDarkThemePreference(value)

    val blockList: Flow<List<String>> = settingsStore.blockListPreference

    suspend fun addBlocklistPattern(pattern: String) {
        settingsStore.addBlocklistPattern(pattern)
        runOnceBlocklistUpdate(di)
    }

    suspend fun removeBlocklistPattern(pattern: String) {
        settingsStore.removeBlocklistPattern(pattern)
        runOnceBlocklistUpdate(di)
    }

    val applyBlocklistToSummaries: StateFlow<Boolean> = settingsStore.applyBlocklistToSummaries

    fun setApplyBlocklistToSummaries(value: Boolean) {
        settingsStore.setApplyBlocklistToSummaries(value)
        runOnceBlocklistUpdate(di)
    }

    val applyBlocklistToLinks: StateFlow<Boolean> = settingsStore.applyBlocklistToLinks

    fun setApplyBlocklistToLinks(value: Boolean) {
        settingsStore.setApplyBlocklistToLinks(value)
        runOnceBlocklistUpdate(di)
    }

    suspend fun setBlockStatusForNewInFeed(
        feedId: Long,
        blockTime: Instant,
    ) {
        feedItemStore.setBlockStatusForNewInFeed(feedId, blockTime)
    }

    val currentSorting: StateFlow<SortingOptions> = settingsStore.currentSorting

    fun setCurrentSorting(value: SortingOptions) = settingsStore.setCurrentSorting(value)

    val showFab: StateFlow<Boolean> = settingsStore.showFab

    fun setShowFab(value: Boolean) = settingsStore.setShowFab(value)

    val feedItemStyle: StateFlow<FeedItemStyle> = settingsStore.feedItemStyle

    fun setFeedItemStyle(value: FeedItemStyle) = settingsStore.setFeedItemStyle(value)

    val swipeAsRead: StateFlow<SwipeAsRead> = settingsStore.swipeAsRead

    fun setSwipeAsRead(value: SwipeAsRead) = settingsStore.setSwipeAsRead(value)

    val syncOnResume: StateFlow<Boolean> = settingsStore.syncOnResume

    fun setSyncOnResume(value: Boolean) = settingsStore.setSyncOnResume(value)

    val syncOnlyOnWifi: StateFlow<Boolean> = settingsStore.syncOnlyOnWifi

    fun setSyncOnlyOnWifi(value: Boolean) = settingsStore.setSyncOnlyOnWifi(value)

    val syncOnlyWhenCharging: StateFlow<Boolean> = settingsStore.syncOnlyWhenCharging

    fun setSyncOnlyWhenCharging(value: Boolean) = settingsStore.setSyncOnlyWhenCharging(value)

    val useDetectLanguage = settingsStore.useDetectLanguage

    fun setUseDetectLanguage(value: Boolean) = settingsStore.setUseDetectLanguage(value)

    val textScale = settingsStore.textScale

    val openTitleInBrowser = settingsStore.openTitleInBrowser

    fun setTextScale(value: Float) = settingsStore.setTextScale(value)

    fun setOpenTitleInBrowser(value: Boolean) = settingsStore.setOpenTitleInBrowser(value)

    val maximumCountPerFeed = settingsStore.maximumCountPerFeed

    fun setMaxCountPerFeed(value: Int) = settingsStore.setMaxCountPerFeed(value)

    val itemOpener
        get() = settingsStore.itemOpener

    fun setItemOpener(value: ItemOpener) = settingsStore.setItemOpener(value)

    val linkOpener = settingsStore.linkOpener

    fun setLinkOpener(value: LinkOpener) = settingsStore.setLinkOpener(value)

    val syncFrequency = settingsStore.syncFrequency

    fun setSyncFrequency(value: SyncFrequency) = settingsStore.setSyncFrequency(value)

    val resumeTime: StateFlow<Instant> = sessionStore.resumeTime

    fun setResumeTime(value: Instant) {
        sessionStore.setResumeTime(value)
    }

    val showTitleUnreadCount = settingsStore.showTitleUnreadCount

    fun setShowTitleUnreadCount(value: Boolean) = settingsStore.setShowTitleUnreadCount(value)

    val isOpenDrawerOnFab = settingsStore.openDrawerOnFab

    fun setOpenDrawerOnFab(value: Boolean) = settingsStore.setOpenDrawerOnFab(value)

    val forceSingleColumn = settingsStore.forceSingleColumn

    fun setForceSingleColumn(value: Boolean) = settingsStore.setForceSingleColumn(value)

    /**
     * True while a sync is recent enough to still be worth saying so.
     *
     * ⚠ The obvious version of this -- map the timestamp to "is it within ten seconds"
     * -- never becomes false. The query returns MAX(last_sync), which changes when a
     * sync *starts* and never again, so the comparison against now() is made exactly
     * once, while it is still true, and nothing emits afterwards to re-make it. The
     * title said "Syncing" for the rest of the session.
     *
     * So the flow says false itself, after the time it is claiming has actually passed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentlySyncing: Flow<Boolean>
        get() =
            feedStore
                .getCurrentlySyncingLatestTimestamp()
                .flatMapLatest { value ->
                    flow {
                        val startedAt = value ?: Instant.EPOCH
                        val remaining =
                            SYNC_SETTLE_SECONDS - Duration.between(startedAt, Instant.now()).seconds
                        if (remaining <= 0) {
                            emit(false)
                        } else {
                            emit(true)
                            delay(remaining * 1000L)
                            emit(false)
                        }
                    }
                }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCurrentFeedListItems(): Flow<PagingData<FeedListItem>> =
        combine(
            currentFeedAndTag,
            minReadTime,
            currentSorting,
            feedListFilter,
            search,
        ) { feedAndTag, minReadTime, currentSorting, feedListFilter, search ->
            val (feedId, tag) = feedAndTag
            FeedListArgs(
                feedId = feedId,
                tag = tag,
                minReadTime =
                    when (feedId) {
                        ID_SAVED_ARTICLES -> Instant.EPOCH
                        else -> minReadTime
                    },
                newestFirst = currentSorting == SortingOptions.NEWEST_FIRST,
                filter = feedListFilter,
                search = search,
            )
        }.flatMapLatest {
            feedItemStore.getPagedFeedItemsRaw(
                feedId = it.feedId,
                tag = it.tag,
                minReadTime = it.minReadTime,
                newestFirst = it.newestFirst,
                filter = it.filter,
                search = it.search,
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCurrentFeedListVisibleItemCount(): Flow<Int> =
        combine(
            currentFeedAndTag,
            minReadTime,
            feedListFilter,
            search,
        ) { feedAndTag, minReadTime, feedListFilter, search ->
            val (feedId, tag) = feedAndTag
            FeedListArgs(
                feedId = feedId,
                tag = tag,
                minReadTime =
                    when (feedId) {
                        ID_SAVED_ARTICLES -> Instant.EPOCH
                        else -> minReadTime
                    },
                newestFirst = false,
                filter = feedListFilter,
                search = search,
            )
        }.flatMapLatest {
            feedItemStore.getFeedItemCountRaw(
                feedId = it.feedId,
                tag = it.tag,
                minReadTime = it.minReadTime,
                filter = it.filter,
                search = it.search,
            )
        }

    val currentArticle: Flow<Article> =
        currentArticleId
            .flatMapLatest { itemId ->
                feedItemStore.getFeedItem(itemId)
            }.mapLatest { item ->
                Article(item = item)
            }

    suspend fun getFeed(feedId: Long): Feed? = feedStore.getFeed(feedId)

    suspend fun getFeed(url: URL): Feed? = feedStore.getFeed(url)

    suspend fun saveFeed(feed: Feed): Long = feedStore.saveFeed(feed)

    suspend fun setBookmarked(
        itemId: Long,
        bookmarked: Boolean,
    ) = feedItemStore.setBookmarked(itemId = itemId, bookmarked = bookmarked)

    suspend fun markAsNotified(itemIds: List<Long>) = feedItemStore.markAsNotified(itemIds)

    suspend fun toggleNotifications(
        feedId: Long,
        value: Boolean,
    ) = feedStore.toggleNotifications(feedId, value)

    val feedNotificationSettings: Flow<List<FeedForSettings>> = feedStore.feedForSettings

    suspend fun markAsReadAndNotified(
        itemId: Long,
        readTimeBeforeMinReadTime: Boolean = false,
    ) {
        minReadTime.value.let { minReadTimeValue ->
            if (readTimeBeforeMinReadTime && minReadTimeValue.isAfter(Instant.EPOCH)) {
                // If read time is not EPOCH, one second before so swipe can get rid of it
                feedItemStore.markAsReadAndNotifiedAndOverwriteReadTime(
                    itemId,
                    minReadTimeValue.minusSeconds(1),
                )
            } else {
                feedItemStore.markAsReadAndNotified(itemId)
            }
        }
    }

    suspend fun markAsUnread(itemId: Long) {
        feedItemStore.markAsUnread(itemId)
        syncRemoteStore.setNotSynced(itemId)
    }

    suspend fun shouldDisplayFullTextForItemByDefault(itemId: Long): Boolean = feedItemStore.getFullTextByDefault(itemId)

    suspend fun getLink(itemId: Long): String? = feedItemStore.getLink(itemId)

    suspend fun getArticleOpener(itemId: Long): ItemOpener =
        when (feedItemStore.getArticleOpener(itemId)) {
            PREF_VAL_OPEN_WITH_BROWSER -> ItemOpener.DEFAULT_BROWSER
            PREF_VAL_OPEN_WITH_CUSTOM_TAB -> ItemOpener.CUSTOM_TAB
            PREF_VAL_OPEN_WITH_READER -> ItemOpener.READER
            else -> itemOpener.value // Global default
        }

    fun getScreenTitleForFeedOrTag(
        feedId: Long,
        tag: String,
    ) = getUnreadCount(feedId, tag).mapLatest { unreadCount ->
        ScreenTitle(
            title =
                when {
                    feedId > ID_UNSET -> feedStore.getDisplayTitle(feedId)
                    tag.isNotBlank() -> tag
                    else -> null
                },
            type =
                when (feedId) {
                    ID_UNSET -> FeedType.TAG
                    ID_ALL_FEEDS -> FeedType.ALL_FEEDS
                    ID_SAVED_ARTICLES -> FeedType.SAVED_ARTICLES
                    else -> FeedType.FEED
                },
            unreadCount = unreadCount,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getScreenTitleForCurrentFeedOrTag(): Flow<ScreenTitle> =
        currentFeedAndTag.flatMapLatest { (feedId, tag) ->
            getUnreadCount(feedId, tag).mapLatest { unreadCount ->
                ScreenTitle(
                    title =
                        when {
                            feedId > ID_UNSET -> feedStore.getDisplayTitle(feedId)
                            tag.isNotBlank() -> tag
                            else -> null
                        },
                    type =
                        when {
                            tag.isNotBlank() -> FeedType.TAG
                            feedId == ID_UNSET || feedId == ID_ALL_FEEDS -> FeedType.ALL_FEEDS
                            feedId == ID_SAVED_ARTICLES -> FeedType.SAVED_ARTICLES
                            else -> FeedType.FEED
                        },
                    unreadCount = unreadCount,
                )
            }
        }

    suspend fun deleteFeeds(feedIds: List<Long>) {
        feedStore.deleteFeeds(feedIds)
        androidSystemStore.removeDynamicShortcuts(feedIds)
        if (currentFeedAndTag.value.first in feedIds) {
            setCurrentFeedAndTag(ID_ALL_FEEDS, "")
        }
    }

    suspend fun markAllAsReadInFeedOrTag(
        feedId: Long,
        tag: String,
    ) {
        when {
            feedId > ID_UNSET -> feedItemStore.markAllAsReadInFeed(feedId)
            tag.isNotBlank() -> feedItemStore.markAllAsReadInTag(tag)
            else -> feedItemStore.markAllAsRead()
        }
        setMinReadTime(Instant.now())
    }

    suspend fun markBeforeAsRead(
        cursor: FeedItemCursor,
        feedId: Long,
        tag: String,
    ) {
        feedItemStore.markAsReadRaw(
            feedId = feedId,
            tag = tag,
            filter = feedListFilter.value,
            search = search.value,
            minReadTime = minReadTime.value,
            descending = SortingOptions.NEWEST_FIRST != currentSorting.value,
            cursor = cursor,
        )
    }

    suspend fun markAfterAsRead(
        cursor: FeedItemCursor,
        feedId: Long,
        tag: String,
    ) {
        feedItemStore.markAsReadRaw(
            feedId = feedId,
            tag = tag,
            filter = feedListFilter.value,
            search = search.value,
            minReadTime = minReadTime.value,
            descending = SortingOptions.NEWEST_FIRST == currentSorting.value,
            cursor = cursor,
        )
    }

    val allTags: Flow<List<String>> = feedStore.allTags

    fun getPagedNavDrawerItems(): Flow<PagingData<FeedUnreadCount>> =
        expandedTags.flatMapLatest {
            feedStore.getPagedNavDrawerItems(it)
        }

    val getUnreadBookmarksCount
        get() =
            feedItemStore.getFeedItemCountRaw(
                feedId = ID_SAVED_ARTICLES,
                tag = "",
                minReadTime = Instant.EPOCH,
                filter = emptyFeedListFilter,
                search = "",
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCurrentlyVisibleFeedTitles(): Flow<List<FeedTitle>> =
        currentFeedAndTag.flatMapLatest { (feedId, tag) ->
            feedStore.getFeedTitles(feedId, tag)
        }

    val expandedTags: StateFlow<Set<String>> = sessionStore.expandedTags

    fun toggleTagExpansion(tag: String) = sessionStore.toggleTagExpansion(tag)

    fun ensurePeriodicSyncConfigured() {
        schedulePeriodicRssSync(di = di, replace = false)
    }

    fun getFeedsItemsWithDefaultFullTextNeedingDownload(): Flow<List<FeedItemIdWithLink>> = feedItemStore.getFeedsItemsWithDefaultFullTextNeedingDownload()

    suspend fun markAsFullTextDownloaded(feedItemId: Long) = feedItemStore.markAsFullTextDownloaded(feedItemId)

    fun getFeedItemsNeedingNotifying(): Flow<List<Long>> = feedItemStore.getFeedItemsNeedingNotifying()

    fun getSyncRemoteFlow(): Flow<SyncRemote?> = syncRemoteStore.getSyncRemoteFlow()

    suspend fun getSyncRemote(): SyncRemote = syncRemoteStore.getSyncRemote()

    suspend fun updateSyncRemote(syncRemote: SyncRemote) {
        syncRemoteStore.updateSyncRemote(syncRemote)
    }

    suspend fun updateSyncRemoteMessageTimestamp(timestamp: Instant) {
        syncRemoteStore.updateSyncRemoteMessageTimestamp(timestamp)
    }

    suspend fun getFeedItemsWithoutSyncedReadMark(): List<FeedItemForReadMark> = syncRemoteStore.getFeedItemsWithoutSyncedReadMark()

    suspend fun setSynced(feedItemId: Long) {
        syncRemoteStore.setSynced(feedItemId)
    }

    suspend fun upsertFeed(feedSql: Feed) = feedStore.upsertFeed(feedSql)

    suspend fun loadFeedItem(
        guid: String,
        feedId: Long,
    ): FeedItem? = feedItemStore.loadFeedItem(guid = guid, feedId = feedId)

    suspend fun upsertFeedItems(
        itemsWithText: List<Pair<FeedItem, String>>,
        block: suspend (FeedItem, String) -> Unit,
    ) {
        feedItemStore.upsertFeedItems(itemsWithText, block)
    }

    suspend fun getItemsToBeCleanedFromFeed(
        feedId: Long,
        keepCount: Int,
    ) = feedItemStore.getItemsToBeCleanedFromFeed(feedId = feedId, keepCount = keepCount)

    suspend fun deleteFeedItems(ids: List<Long>) {
        feedItemStore.deleteFeedItems(ids)
    }

    suspend fun deleteStaleRemoteReadMarks() {
        syncRemoteStore.deleteStaleRemoteReadMarks(Instant.now())
    }

    suspend fun getGuidsWhichAreSyncedAsReadInFeed(feed: Feed) = syncRemoteStore.getGuidsWhichAreSyncedAsReadInFeed(feed.url)

    suspend fun applyRemoteReadMarks() {
        val toBeApplied = syncRemoteStore.getRemoteReadMarksReadyToBeApplied()
        val itemIds = toBeApplied.map { it.feedItemId }
        feedItemStore.markAsRead(itemIds)
        for (itemId in itemIds) {
            syncRemoteStore.setSynced(itemId)
        }
        syncRemoteStore.deleteAppliedRemoteReadMarks(toBeApplied.map { it.id })
        // Remove stale marks for items that are already read so they don't override a
        // deliberate "mark as unread" on the next sync.
        syncRemoteStore.deleteRemoteReadMarksForReadItems()
    }

    suspend fun replaceWithDefaultSyncRemote() {
        syncRemoteStore.replaceWithDefaultSyncRemote()
    }

    fun getDevices(): Flow<List<SyncDevice>> = syncRemoteStore.getDevices()

    suspend fun replaceDevices(devices: List<SyncDevice>) {
        syncRemoteStore.replaceDevices(devices)
    }

    suspend fun getFeedsOrderedByUrl(): List<Feed> = feedStore.getFeedsOrderedByUrl()

    suspend fun getRemotelySeenFeeds(): List<URL> = syncRemoteStore.getRemotelySeenFeeds()

    suspend fun deleteFeed(url: URL) {
        feedStore.deleteFeed(url)
    }

    suspend fun replaceRemoteFeedsWith(remoteFeeds: List<RemoteFeed>) {
        syncRemoteStore.replaceRemoteFeedsWith(remoteFeeds)
    }

    suspend fun syncLoadFeedIfStale(
        feedId: Long,
        staleTime: Long,
        retryAfter: Instant,
    ) = feedStore.syncLoadFeedIfStale(feedId = feedId, staleTime = staleTime, retryAfter = retryAfter)

    suspend fun syncLoadFeed(
        feedId: Long,
        retryAfter: Instant,
    ): Feed? = feedStore.syncLoadFeed(feedId = feedId, retryAfter = retryAfter)

    suspend fun syncLoadFeedsIfStale(
        tag: String,
        staleTime: Long,
        retryAfter: Instant,
    ) = feedStore.syncLoadFeedsIfStale(tag = tag, staleTime = staleTime, retryAfter = retryAfter)

    suspend fun syncLoadFeedsIfStale(
        staleTime: Long,
        retryAfter: Instant,
    ) = feedStore.syncLoadFeedsIfStale(staleTime = staleTime, retryAfter = retryAfter)

    suspend fun syncLoadFeeds(
        tag: String,
        retryAfter: Instant,
    ): List<Feed> = feedStore.syncLoadFeeds(tag = tag, retryAfter = retryAfter)

    suspend fun syncLoadFeeds(retryAfter: Instant): List<Feed> = feedStore.syncLoadFeeds(retryAfter = retryAfter)

    suspend fun setCurrentlySyncingOn(
        feedId: Long,
        syncing: Boolean,
        lastSync: Instant? = null,
    ) = feedStore.setCurrentlySyncingOn(feedId = feedId, syncing = syncing, lastSync = lastSync)

    val isOpenAdjacent: StateFlow<Boolean> = settingsStore.openAdjacent

    fun setOpenAdjacent(value: Boolean) {
        settingsStore.setOpenAdjacent(value)
    }

    val isPagingMode: StateFlow<Boolean> = settingsStore.isPagingMode

    fun setIsPagingMode(value: Boolean) {
        settingsStore.setIsPagingMode(value)
    }

    val isAnimatedPaging: StateFlow<Boolean> = settingsStore.isAnimatedPaging

    fun setIsAnimatedPaging(value: Boolean) {
        settingsStore.setIsAnimatedPaging(value)
    }

    val showReadingTime: StateFlow<Boolean> = settingsStore.showReadingTime

    fun setShowReadingTime(value: Boolean) {
        settingsStore.setShowReadingTime(value)
    }

    suspend fun updateWordCountFull(
        id: Long,
        wordCount: Int,
    ) {
        feedItemStore.updateWordCountFull(id, wordCount)
    }

    suspend fun duplicateStoryExists(
        id: Long,
        title: String,
        link: String?,
    ): Boolean = feedItemStore.duplicateStoryExists(id = id, title = title, link = link)

    val syncWorkerRunning: StateFlow<Boolean> = sessionStore.syncWorkerRunning

    fun setSyncWorkerRunning(running: Boolean) {
        sessionStore.setSyncWorkerRunning(running)
    }

    /**
     * Set the retry after time for feeds with the given base URL.
     *
     * If a retry after time is already set, the new time will only be set if it is later than the
     * current value.
     */
    suspend fun setRetryAfterForFeedsWithBaseUrl(
        host: String,
        retryAfter: Instant,
    ) {
        feedStore.setRetryAfterForFeedsWithBaseUrl(host = host, retryAfter = retryAfter)
    }

    companion object {
        private const val LOG_TAG = "KIRINUKI_REPO"

        /** How long after a sync starts the app still says it is syncing. */
        private const val SYNC_SETTLE_SECONDS = 10L
    }
}

private data class FeedListArgs(
    val feedId: Long,
    val tag: String,
    val newestFirst: Boolean,
    val minReadTime: Instant,
    val filter: FeedListFilter,
    val search: String,
)

// Wrapper class because flow combine doesn't like nulls
@Immutable
data class ScreenTitle(
    val title: String?,
    val type: FeedType,
    val unreadCount: Int,
)

enum class FeedType {
    FEED,
    TAG,
    SAVED_ARTICLES,
    ALL_FEEDS,
}

@Immutable
data class Enclosure(
    val present: Boolean = false,
    val link: String = "",
    val name: String = "",
    val type: String = "",
)

val Enclosure.isImage: Boolean
    get() = type.startsWith("image/")

@Immutable
data class Article(
    val item: FeedItemWithFeed?,
) {
    val id: Long = item?.id ?: ID_UNSET
    val link: String? = item?.link
    val feedDisplayTitle: String = item?.feedDisplayTitle ?: ""
    val title: String = item?.plainTitle ?: ""
    val snippet: String = item?.plainSnippet ?: ""
    val enclosure: Enclosure =
        item?.enclosureLink?.let { link ->
            Enclosure(
                present = true,
                link = link,
                name = item.enclosureFilename ?: "",
                type = item.enclosureType ?: "",
            )
        } ?: Enclosure(
            present = false,
        )
    val author: String? = item?.author
    val pubDate: ZonedDateTime? = item?.pubDate
    val feedId: Long = item?.feedId ?: ID_UNSET
    val feedUrl: String? = item?.feedUrl?.toString()
    val bookmarked: Boolean = item?.bookmarked ?: false
    val fullTextByDefault: Boolean = item?.fullTextByDefault ?: false
    val wordCount: Int = item?.wordCount ?: 0
    val wordCountFull: Int = item?.wordCountFull ?: 0
    val image: ThumbnailImage? = item?.thumbnailImage
}

enum class TextToDisplay {
    CONTENT,
    LOADING_FULLTEXT,
    FAILED_TO_LOAD_FULLTEXT,
    FAILED_MISSING_BODY,
    FAILED_MISSING_LINK,
    FAILED_NOT_HTML,
    FAILED_FULLTEXT_TOO_LARGE,
}
