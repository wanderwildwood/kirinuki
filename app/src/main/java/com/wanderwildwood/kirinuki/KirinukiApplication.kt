package com.wanderwildwood.kirinuki

import android.app.Application
import android.app.job.JobScheduler
import android.content.ContentResolver
import android.content.SharedPreferences
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.db.room.AppDatabase
import com.wanderwildwood.kirinuki.db.room.BlocklistDao
import com.wanderwildwood.kirinuki.db.room.FeedDao
import com.wanderwildwood.kirinuki.db.room.FeedItemDao
import com.wanderwildwood.kirinuki.db.room.ReadStatusSyncedDao
import com.wanderwildwood.kirinuki.db.room.RemoteFeedDao
import com.wanderwildwood.kirinuki.db.room.RemoteReadMarkDao
import com.wanderwildwood.kirinuki.db.room.SyncDeviceDao
import com.wanderwildwood.kirinuki.db.room.SyncRemoteDao
import com.wanderwildwood.kirinuki.di.androidModule
import com.wanderwildwood.kirinuki.di.archModelModule
import com.wanderwildwood.kirinuki.di.networkModule
import com.wanderwildwood.kirinuki.model.AlwaysUseCacheIfPossibleRequestsInterceptor
import com.wanderwildwood.kirinuki.model.ForceCacheOnSomeFailuresInterceptor
import com.wanderwildwood.kirinuki.model.OneImageRequestPerHostInterceptor
import com.wanderwildwood.kirinuki.model.RateLimitedInterceptor
import com.wanderwildwood.kirinuki.model.TooManyRequestsInterceptor
import com.wanderwildwood.kirinuki.model.UserAgentInterceptor
import com.wanderwildwood.kirinuki.notifications.NotificationsWorker
import com.wanderwildwood.kirinuki.util.FilePathProvider
import com.wanderwildwood.kirinuki.util.ToastMaker
import com.wanderwildwood.kirinuki.util.currentlyUnmetered
import com.wanderwildwood.kirinuki.util.filePathProvider
import com.wanderwildwood.kirinuki.util.logDebug
import com.nononsenseapps.jsonfeed.cachingHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.bind
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.di.singleton
import java.io.File
import java.util.concurrent.TimeUnit

class KirinukiApplication :
    Application(),
    DIAware {
    private val applicationCoroutineScope = ApplicationCoroutineScope()

    override val di by DI.lazy {
        bind<FilePathProvider>() with
            singleton {
                filePathProvider(cacheDir = cacheDir, filesDir = filesDir)
            }
        bind<Application>() with singleton { this@KirinukiApplication }
        bind<AppDatabase>() with singleton { AppDatabase.getInstance(this@KirinukiApplication) }
        bind<FeedDao>() with singleton { instance<AppDatabase>().feedDao() }
        bind<FeedItemDao>() with singleton { instance<AppDatabase>().feedItemDao() }
        bind<SyncRemoteDao>() with singleton { instance<AppDatabase>().syncRemoteDao() }
        bind<ReadStatusSyncedDao>() with singleton { instance<AppDatabase>().readStatusSyncedDao() }
        bind<RemoteReadMarkDao>() with singleton { instance<AppDatabase>().remoteReadMarkDao() }
        bind<RemoteFeedDao>() with singleton { instance<AppDatabase>().remoteFeedDao() }
        bind<SyncDeviceDao>() with singleton { instance<AppDatabase>().syncDeviceDao() }
        bind<BlocklistDao>() with singleton { instance<AppDatabase>().blocklistDao() }

        import(androidModule)

        import(archModelModule)

        bind<ContentResolver>() with singleton { contentResolver }
        bind<JobScheduler>() with singleton { getSystemService(JobScheduler::class.java) }
        bind<ToastMaker>() with
            singleton {
                object : ToastMaker {
                    override suspend fun makeToast(text: String) =
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@KirinukiApplication, text, Toast.LENGTH_SHORT).show()
                        }

                    override suspend fun makeToast(resId: Int) =
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@KirinukiApplication, resId, Toast.LENGTH_SHORT).show()
                        }
                }
            }
        bind<NotificationManagerCompat>() with singleton { NotificationManagerCompat.from(this@KirinukiApplication) }
        bind<SharedPreferences>() with
            singleton {
                PreferenceManager.getDefaultSharedPreferences(
                    this@KirinukiApplication,
                )
            }

        bind<OkHttpClient>() with
            singleton {
                val filePathProvider = instance<FilePathProvider>()
                cachingHttpClient(
                    cacheDirectory = filePathProvider.httpCacheDir,
                    cacheSize = 200L * 1024 * 1024,
                ) {
                    addNetworkInterceptor(UserAgentInterceptor)
                    addNetworkInterceptor(RateLimitedInterceptor)
                    addNetworkInterceptor(ForceCacheOnSomeFailuresInterceptor)
                    addNetworkInterceptor(TooManyRequestsInterceptor)
                    if (BuildConfig.DEBUG) {
                        addInterceptor { chain ->
                            val request = chain.request()
                            logDebug(
                                "FEEDER",
                                "Request ${request.url} headers [${request.headers}]",
                            )

                            chain.proceed(request).also {
                                logDebug(
                                    "FEEDER",
                                    "Response ${it.request.url} code ${it.networkResponse?.code} cached ${it.cacheResponse != null}",
                                )
                            }
                        }
                    }
                }
            }
        bind<ApplicationCoroutineScope>() with instance(applicationCoroutineScope)
        import(networkModule)
        bind<NotificationsWorker>() with singleton { NotificationsWorker(di) }
    }

    override fun onCreate() {
        super.onCreate()
        @Suppress("DEPRECATION")
        staticFilesDir = filesDir
    }

    override fun onTerminate() {
        applicationCoroutineScope.cancel("Application is being terminated")
        super.onTerminate()
    }

    companion object {
        @Deprecated("Only used by an old database migration")
        lateinit var staticFilesDir: File

        private const val LOG_TAG = "KIRINUKI_APP"
    }
}
