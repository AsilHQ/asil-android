package org.halalz.fenix.ui

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.halalz.fenix.customannotations.SmokeTest
import org.halalz.fenix.helpers.AndroidAssetDispatcher
import org.halalz.fenix.helpers.HomeActivityTestRule
import org.halalz.fenix.helpers.TestAssetHelper
import org.halalz.fenix.helpers.TestHelper.verifyDarkThemeApplied
import org.halalz.fenix.helpers.TestHelper.verifyLightThemeApplied
import org.halalz.fenix.ui.robots.homeScreen
import org.halalz.fenix.ui.robots.navigationToolbar

class OnboardingTest {
    private lateinit var mDevice: UiDevice
    private lateinit var mockWebServer: MockWebServer
    private val privacyNoticeLink = "mozilla.org/en-US/privacy/firefox"

    @get:Rule
    val activityTestRule = HomeActivityTestRule.withDefaultSettingsOverrides()

    @Before
    fun setUp() {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        mockWebServer = MockWebServer().apply {
            dispatcher = AndroidAssetDispatcher()
            start()
        }
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun getUITheme(): Boolean {
        val mode =
            activityTestRule.activity.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)

        return when (mode) {
            Configuration.UI_MODE_NIGHT_YES -> true // dark theme is set
            Configuration.UI_MODE_NIGHT_NO -> false // dark theme is not set, using light theme
            else -> false // default option is light theme
        }
    }

    // Verifies the first run onboarding screen
    @SmokeTest
    @Test
    fun firstRunScreenTest() {
        homeScreen {
            verifyHomeScreenAppBarItems()
            verifyNavigationToolbarItems("0")
        }
    }

    // Verifies the functionality of the onboarding Start Browsing button
    @SmokeTest
    @Test
    fun startBrowsingButtonTest() {

    }

    @Test
    fun dismissOnboardingUsingSettingsTest() {
        homeScreen {

        }.openThreeDotMenu {
        }.openSettings {
            verifyGeneralHeading()
        }.goBack {
            verifyExistingTopSitesList()
        }
    }

    @Test
    fun dismissOnboardingUsingBookmarksTest() {
        homeScreen {

        }.openThreeDotMenu {
        }.openBookmarks {
            verifyBookmarksMenuView()
            navigateUp()
        }
        homeScreen {
            verifyExistingTopSitesList()
        }
    }

    @Ignore("Failing, see: https://bugzilla.mozilla.org/show_bug.cgi?id=1807268")
    @Test
    fun dismissOnboardingUsingHelpTest() {
        homeScreen {

        }.openThreeDotMenu {
        }.openHelp {
            verifyHelpUrl()
        }.goBack {
            verifyExistingTopSitesList()
        }
    }

    @Test
    fun toolbarTapDoesntDismissOnboardingTest() {
        homeScreen {

        }.openSearch {

        }.dismissSearchBar {

        }
    }

    @Test
    fun dismissOnboardingWithPageLoadTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        homeScreen {

        }
        navigationToolbar {
        }.enterURLAndEnterToBrowser(defaultWebPage.url) {
        }.goToHomescreen {
            verifyHomeScreen()
        }
    }

    @Test
    fun chooseYourThemeCardTest() {
        homeScreen {
            verifyLightThemeApplied(getUITheme())
            verifyDarkThemeApplied(getUITheme())
            verifyLightThemeApplied(getUITheme())
        }
    }


    @Test
    fun privacyProtectionByDefaultCardTest() {
        homeScreen {

        }
    }

    @Test
    fun pickUpWhereYouLeftOffCardTest() {

    }

    @Test
    fun youControlYourDataCardTest() {

    }
}
