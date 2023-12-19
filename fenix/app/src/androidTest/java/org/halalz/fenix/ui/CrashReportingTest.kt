/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.ui

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.halalz.fenix.R
import org.halalz.fenix.customannotations.SmokeTest
import org.halalz.fenix.helpers.AndroidAssetDispatcher
import org.halalz.fenix.helpers.DataGenerationHelper.getStringResource
import org.halalz.fenix.helpers.HomeActivityIntentTestRule
import org.halalz.fenix.helpers.MatcherHelper.itemWithResId
import org.halalz.fenix.helpers.TestAssetHelper
import org.halalz.fenix.helpers.TestHelper.packageName
import org.halalz.fenix.ui.robots.clickPageObject
import org.halalz.fenix.ui.robots.homeScreen
import org.halalz.fenix.ui.robots.navigationToolbar

class CrashReportingTest {
    private lateinit var mDevice: UiDevice
    private lateinit var mockWebServer: MockWebServer
    private val tabCrashMessage = getStringResource(R.string.tab_crash_title_2)

    @get:Rule
    val activityTestRule = AndroidComposeTestRule(
        HomeActivityIntentTestRule(
            isPocketEnabled = false,
            isJumpBackInCFREnabled = false,
            isWallpaperOnboardingEnabled = false,
            isTCPCFREnabled = false,
        ),
    ) { it.activity }

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

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/308906
    @Test
    fun closeTabFromCrashedTabReporterTest() {
        homeScreen {
        }.openNavigationToolbar {
        }.openTabCrashReporter {
        }.clickTabCrashedCloseButton {
        }.openTabDrawer {
            verifyNoOpenTabsInNormalBrowsing()
        }
    }

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/2336134
    @Ignore("Test failure caused by: https://github.com/mozilla-mobile/fenix/issues/19964")
    @Test
    fun restoreTabFromTabCrashedReporterTest() {
        val website = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        homeScreen {
        }.openNavigationToolbar {
        }.enterURLAndEnterToBrowser(website.url) {}

        navigationToolbar {
        }.openTabCrashReporter {
            clickPageObject(itemWithResId("$packageName:id/restoreTabButton"))
            verifyPageContent(website.content)
        }
    }

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/1681928
    @SmokeTest
    @Test
    fun useAppWhileTabIsCrashedTest() {
        val firstWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)
        val secondWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 2)

        homeScreen {
        }.openNavigationToolbar {
        }.enterURLAndEnterToBrowser(firstWebPage.url) {
            mDevice.waitForIdle()
        }.openTabDrawer {
        }.openNewTab {
        }.submitQuery(secondWebPage.url.toString()) {
            waitForPageToLoad()
        }

        navigationToolbar {
        }.openTabCrashReporter {
            verifyPageContent(tabCrashMessage)
        }.openTabDrawer {
            verifyExistingOpenTabs(firstWebPage.title)
            verifyExistingOpenTabs(secondWebPage.title)
        }.closeTabDrawer {
        }.goToHomescreen {
            verifyExistingTopSitesList()
        }.openThreeDotMenu {
            verifySettingsButton()
        }
    }
}