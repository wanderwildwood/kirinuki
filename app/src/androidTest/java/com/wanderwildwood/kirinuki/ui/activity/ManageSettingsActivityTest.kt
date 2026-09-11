package com.wanderwildwood.kirinuki.ui.activity

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wanderwildwood.kirinuki.ui.ManageSettingsActivity
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ManageSettingsActivityTest {
    @Test
    fun activityShouldStart() {
        val intent =
            Intent(
                ApplicationProvider.getApplicationContext(),
                ManageSettingsActivity::class.java,
            )
        launchActivity<ManageSettingsActivity>(intent).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
