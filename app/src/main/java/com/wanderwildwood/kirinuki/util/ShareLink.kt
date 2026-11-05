package com.wanderwildwood.kirinuki.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

private const val LOG_TAG = "KIRINUKI_SHARE"

/**
 * Feeds takes shared text itself, to add a feed. Sending a story's address back to it would
 * only start a search for a feed, so it is left out of the list and out of the count.
 */
private const val OWN_SHARE_TARGET = "com.wanderwildwood.kirinuki.ui.AddFeedFromShareActivity"

private fun textShare(): Intent = Intent(Intent.ACTION_SEND).setType("text/plain")

/**
 * Whether anything other than Feeds takes shared text. Needs the SEND `<queries>` entry in the
 * manifest; without it the answer on API 30 and up is nothing, and the press stays hidden.
 */
fun Context.canShareText(): Boolean =
    try {
        packageManager
            .queryIntentActivities(textShare(), PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo?.packageName != packageName }
    } catch (e: Exception) {
        Log.e(LOG_TAG, "Could not ask what takes shared text", e)
        false
    }

/**
 * Hand a story's address to another app. The title goes as the subject, so a notes app can
 * use it as the note's title; the text is the address alone.
 */
fun Context.shareLink(
    title: String,
    link: String,
    chooserTitle: String,
): Boolean {
    val send =
        textShare()
            .putExtra(Intent.EXTRA_TEXT, link)
            .apply { if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title) }
    return try {
        startActivity(
            Intent.createChooser(send, chooserTitle)
                .putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS,
                    arrayOf(ComponentName(packageName, OWN_SHARE_TARGET)),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: Exception) {
        Log.e(LOG_TAG, "Could not share the link", e)
        false
    }
}
