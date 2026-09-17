package com.wanderwildwood.kirinuki.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import androidx.core.net.toUri

private const val LOG_TAG = "KIRINUKI_BROWSER"

/**
 * The AOSP WebView test harness. It is not a browser -- it is a developer tool that ships
 * on the Kompakt and registers for http, so the system hands it every web address and it
 * crashes on the first real page. Mudita found that the hard way. Anything that resolves
 * to this is treated as nothing resolving at all.
 */
private const val WEBVIEW_SHELL = "org.chromium.webview_shell"

/**
 * Whether this phone has something that can actually show a web page.
 *
 * Needs the http `<queries>` entry in the manifest; without it the package manager answers
 * nothing on API 30 and up whatever is installed, which reads as "no browser" -- wrong, but
 * wrong in the safe direction.
 */
fun Context.hasWebBrowser(): Boolean = webBrowsers().isNotEmpty()

private fun Context.webBrowsers(): List<ResolveInfo> =
    try {
        val probe = Intent(Intent.ACTION_VIEW, "https://example.com".toUri())
        packageManager
            .queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo?.packageName != WEBVIEW_SHELL }
    } catch (e: Exception) {
        Log.e(LOG_TAG, "Could not ask what opens a web page", e)
        emptyList()
    }

/**
 * Open [url] in a browser, if one is there. Returns false when nothing took it, so a
 * caller can say so rather than leave a tap that did nothing.
 *
 * Every layer here can fail on a phone that is missing the thing being asked for, so every
 * layer is guarded: an unopened page is a disappointment, and taking the reader down with
 * an [android.content.ActivityNotFoundException] is the bug this app already shipped once.
 */
fun Context.openInBrowser(url: String): Boolean {
    if (webBrowsers().isEmpty()) return false

    return try {
        startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: Exception) {
        Log.e(LOG_TAG, "Could not open $url", e)
        false
    }
}
