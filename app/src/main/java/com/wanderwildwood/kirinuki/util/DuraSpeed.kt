package com.wanderwildwood.kirinuki.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * DuraSpeed, MediaTek's background manager on the Kompakt, closes installed apps a few minutes
 * after the screen goes dark, so feeds are not refreshed in the background until Feeds is
 * switched on in its list. Settings on a Kompakt has no way in to it, and its own screen will
 * not open for another app, but its App info page will, and that page has an Open button.
 * The same way in that Messaging and Whereabouts offer.
 */
object DuraSpeed {
    const val PACKAGE = "com.mediatek.duraspeed"

    /**
     * Whether DuraSpeed is on this phone at all. Not the maker's name: KompaktOS also says
     * "Mudita" and has none. Listed in the manifest's <queries> so the lookup can see it.
     */
    fun isPresent(context: Context): Boolean =
        runCatching {
            context.packageManager.getApplicationInfo(PACKAGE, 0)
            true
        }.getOrDefault(false)

    /** DuraSpeed's App info page; false when nothing opened. */
    fun open(context: Context): Boolean =
        runCatching {
            context.startActivity(appInfoIntent())
            true
        }.getOrDefault(false)

    fun appInfoIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
