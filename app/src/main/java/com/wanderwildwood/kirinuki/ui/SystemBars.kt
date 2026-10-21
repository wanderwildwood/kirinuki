package com.wanderwildwood.kirinuki.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Edge to edge with dark status and navigation icons whatever the phone's dark-theme setting.
 * The app is always white; the default picks white icons on a phone set to dark, and on
 * Android builds that draw the app's own status bar (LineageOS and others) the clock and
 * battery vanished into the white. MuditaOS paints its own bar over the app, so it never showed.
 */
fun ComponentActivity.enableEdgeToEdgeDarkIcons() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
    )
}
