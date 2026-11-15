package com.wanderwildwood.kirinuki.base

import android.view.MenuInflater
import androidx.activity.ComponentActivity
import com.wanderwildwood.kirinuki.util.ActivityLauncher
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.bind
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.di.provider
import org.kodein.di.singleton

abstract class DIAwareComponentActivity :
    ComponentActivity(),
    DIAware {
    private val parentDI: DI by closestDI()
    override val di: DI by DI.lazy {
        extend(parentDI)
        bind<MenuInflater>() with provider { menuInflater }
        bind<DIAwareComponentActivity>() with instance(this@DIAwareComponentActivity)
        bind<ActivityLauncher>() with
            singleton {
                ActivityLauncher(
                    this@DIAwareComponentActivity,
                    di.direct.instance(),
                )
            }
    }

    // A line that stays until the next press hears of it here; see NextPress.
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        com.wanderwildwood.kirinuki.ui.NextPress.seen(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        com.wanderwildwood.kirinuki.ui.NextPress.seen(event)
        return super.dispatchKeyEvent(event)
    }

    // A notice from somewhere with no screen of its own (an import, an export) is said on this one.
    override fun onResume() {
        super.onResume()
        com.wanderwildwood.kirinuki.ui.FootNotice.resumed(this)
    }

    override fun onPause() {
        com.wanderwildwood.kirinuki.ui.FootNotice.paused(this)
        super.onPause()
    }
}
