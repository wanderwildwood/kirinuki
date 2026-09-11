package com.wanderwildwood.kirinuki.ui.compose

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.wanderwildwood.kirinuki.ui.MainActivity
import com.wanderwildwood.kirinuki.ui.MainActivityViewModel
import com.wanderwildwood.kirinuki.ui.compose.navigation.SyncScreenDestination
import com.wanderwildwood.kirinuki.ui.compose.theme.FeederTheme
import com.wanderwildwood.kirinuki.ui.robots.feedScreen
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.kodein.di.compose.withDI
import org.kodein.di.instance

@Ignore
class SyncSetupTest : BaseComposeTest {
    @get:Rule
    override val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent {
            composeTestRule.activity.apply {
                val mainActivityViewModel: MainActivityViewModel by di.instance(arg = this)
                FeederTheme {
                    withDI {
                        val navController = rememberNavController()
                        val navDrawerListState = rememberLazyListState()

                        NavHost(navController, startDestination = SyncScreenDestination.route) {
                            SyncScreenDestination.register(this, navController, navDrawerListState, mainActivityViewModel)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun addFeed() {
        feedScreen {
        } openOverflowMenu {
        } pressAddFeed {
            assertSearchButtonIsNotEnabled()
            enterText("cowboyprogrammer.org")
            assertSearchButtonIsEnabled()
            pressSearchButton()
        } pressFirstResult {
            scrollToBottom()
        } pressOKButton {
            assertAppBarTitleIs("Cowboy Programmer")
        }
    }
}
