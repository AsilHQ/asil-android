/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.ui

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.core.net.toUri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.halalz.fenix.ext.settings
import org.halalz.fenix.helpers.AndroidAssetDispatcher
import org.halalz.fenix.helpers.AppAndSystemHelper.assertExternalAppOpens
import org.halalz.fenix.helpers.Constants
import org.halalz.fenix.helpers.HomeActivityIntentTestRule
import org.halalz.fenix.helpers.MatcherHelper.itemContainingText
import org.halalz.fenix.helpers.MatcherHelper.itemWithText
import org.halalz.fenix.helpers.RetryTestRule
import org.halalz.fenix.helpers.TestAssetHelper
import org.halalz.fenix.helpers.TestHelper.clickSnackbarButton
import org.halalz.fenix.ui.robots.clickContextMenuItem
import org.halalz.fenix.ui.robots.clickPageObject
import org.halalz.fenix.ui.robots.downloadRobot
import org.halalz.fenix.ui.robots.longClickPageObject
import org.halalz.fenix.ui.robots.navigationToolbar
import org.halalz.fenix.ui.robots.shareOverlay

/**
 *  Tests for verifying basic functionality of content context menus
 *
 *  - Verifies long click "Open link in new tab" UI and functionality
 *  - Verifies long click "Open link in new Private tab" UI and functionality
 *  - Verifies long click "Copy Link" UI and functionality
 *  - Verifies long click "Share Link" UI and functionality
 *  - Verifies long click "Open image in new tab" UI and functionality
 *  - Verifies long click "Save Image" UI and functionality
 *  - Verifies long click "Copy image location" UI and functionality
 *  - Verifies long click items of mixed hypertext items
 *
 */

class ComposeContextMenusTest {
    private lateinit var mDevice: UiDevice
    private lateinit var mockWebServer: MockWebServer

    @get:Rule(order = 0)
    val composeTestRule =
        AndroidComposeTestRule(
            HomeActivityIntentTestRule.withDefaultSettingsOverrides(
                tabsTrayRewriteEnabled = true,
            ),
        ) { it.activity }

    @Rule(order = 1)
    @JvmField
    val retryTestRule = RetryTestRule(3)

    @Before
    fun setUp() {
        composeTestRule.activity.applicationContext.settings().shouldShowJumpBackInCFR = false
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

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/243837
    @Test
    fun verifyOpenLinkNewTabContextMenuOptionTest() {
        val pageLinks =
            TestAssetHelper.getGenericAsset(mockWebServer, 4)
        val genericURL =
            TestAssetHelper.getGenericAsset(mockWebServer, 1)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(pageLinks.url) {
            mDevice.waitForIdle()
            longClickPageObject(itemWithText("Link 1"))
            verifyContextMenuForLocalHostLinks(genericURL.url)
            clickContextMenuItem("Open link in new tab")
            verifySnackBarText("New tab opened")
            clickSnackbarButton("SWITCH")
            verifyUrl(genericURL.url.toString())
        }.openComposeTabDrawer(composeTestRule) {
            verifyNormalBrowsingButtonIsSelected()
            verifyExistingOpenTabs("Test_Page_1")
            verifyExistingOpenTabs("Test_Page_4")
        }
    }

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/244655
    @Test
    fun verifyOpenLinkInNewPrivateTabContextMenuOptionTest() {
        val pageLinks =
            TestAssetHelper.getGenericAsset(mockWebServer, 4)
        val genericURL =
            TestAssetHelper.getGenericAsset(mockWebServer, 2)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(pageLinks.url) {
            mDevice.waitForIdle()
            longClickPageObject(itemWithText("Link 2"))
            verifyContextMenuForLocalHostLinks(genericURL.url)
            clickContextMenuItem("Open link in private tab")
            verifySnackBarText("New private tab opened")
            clickSnackbarButton("SWITCH")
            verifyUrl(genericURL.url.toString())
        }.openComposeTabDrawer(composeTestRule) {
            verifyPrivateBrowsingButtonIsSelected()
            verifyExistingOpenTabs("Test_Page_2")
        }
    }

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/243832
    @Test
    fun verifyCopyLinkContextMenuOptionTest() {
        val pageLinks =
            TestAssetHelper.getGenericAsset(mockWebServer, 4)
        val genericURL =
            TestAssetHelper.getGenericAsset(mockWebServer, 3)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(pageLinks.url) {
            mDevice.waitForIdle()
            longClickPageObject(itemWithText("Link 3"))
            verifyContextMenuForLocalHostLinks(genericURL.url)
            clickContextMenuItem("Copy link")
            verifySnackBarText("Link copied to clipboard")
        }.openNavigationToolbar {
        }.visitLinkFromClipboard {
            verifyUrl(genericURL.url.toString())
        }
    }

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/243838
    @Test
    fun verifyShareLinkContextMenuOptionTest() {
        val pageLinks =
            TestAssetHelper.getGenericAsset(mockWebServer, 4)
        val genericURL =
            TestAssetHelper.getGenericAsset(mockWebServer, 1)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(pageLinks.url) {
            mDevice.waitForIdle()
            longClickPageObject(itemWithText("Link 1"))
            verifyContextMenuForLocalHostLinks(genericURL.url)
            clickContextMenuItem("Share link")
            shareOverlay {
                verifyShareLinkIntent(genericURL.url)
            }
        }
    }

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/243833
    @Test
    fun verifyOpenImageNewTabContextMenuOptionTest() {
        val pageLinks =
            TestAssetHelper.getGenericAsset(mockWebServer, 4)
        val imageResource =
            TestAssetHelper.getImageAsset(mockWebServer)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(pageLinks.url) {
            mDevice.waitForIdle()
            longClickPageObject(itemWithText("test_link_image"))
            verifyLinkImageContextMenuItems(imageResource.url)
            clickContextMenuItem("Open image in new tab")
            verifySnackBarText("New tab opened")
            clickSnackbarButton("SWITCH")
            verifyUrl(imageResource.url.toString())
        }
    }

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/243834
    @Test
    fun verifyCopyImageLocationContextMenuOptionTest() {
        val pageLinks =
            TestAssetHelper.getGenericAsset(mockWebServer, 4)
        val imageResource =
            TestAssetHelper.getImageAsset(mockWebServer)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(pageLinks.url) {
            mDevice.waitForIdle()
            longClickPageObject(itemWithText("test_link_image"))
            verifyLinkImageContextMenuItems(imageResource.url)
            clickContextMenuItem("Copy image location")
            verifySnackBarText("Link copied to clipboard")
        }.openNavigationToolbar {
        }.visitLinkFromClipboard {
            verifyUrl(imageResource.url.toString())
        }
    }

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/243835
    @Test
    fun verifySaveImageContextMenuOptionTest() {
        val pageLinks =
            TestAssetHelper.getGenericAsset(mockWebServer, 4)
        val imageResource =
            TestAssetHelper.getImageAsset(mockWebServer)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(pageLinks.url) {
            mDevice.waitForIdle()
            longClickPageObject(itemWithText("test_link_image"))
            verifyLinkImageContextMenuItems(imageResource.url)
            clickContextMenuItem("Save image")
        }

        downloadRobot {
            verifyDownloadCompleteNotificationPopup()
        }.clickOpen("image/jpeg") {} // verify open intent is matched with associated data type
        downloadRobot {
            verifyPhotosAppOpens()
        }
    }

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/352050
    @Test
    fun verifyContextMenuLinkVariationsTest() {
        val pageLinks =
            TestAssetHelper.getGenericAsset(mockWebServer, 4)
        val genericURL =
            TestAssetHelper.getGenericAsset(mockWebServer, 1)
        val imageResource =
            TestAssetHelper.getImageAsset(mockWebServer)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(pageLinks.url) {
            mDevice.waitForIdle()
            longClickPageObject(itemWithText("Link 1"))
            verifyContextMenuForLocalHostLinks(genericURL.url)
            dismissContentContextMenu()
            longClickPageObject(itemWithText("test_link_image"))
            verifyLinkImageContextMenuItems(imageResource.url)
            dismissContentContextMenu()
            longClickPageObject(itemWithText("test_no_link_image"))
            verifyNoLinkImageContextMenuItems(imageResource.url)
        }
    }

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/2333840
    @Test
    fun verifyPDFContextMenuLinkVariationsTest() {
        val genericURL =
            TestAssetHelper.getGenericAsset(mockWebServer, 3)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(genericURL.url) {
            clickPageObject(itemWithText("PDF form file"))
            waitForPageToLoad()
            longClickPageObject(itemWithText("Wikipedia link"))
            verifyContextMenuForLinksToOtherHosts("wikipedia.org".toUri())
            dismissContentContextMenu()
            // Some options are missing from the linked and non liked images context menus in PDF files
            // See https://bugzilla.mozilla.org/show_bug.cgi?id=1012805 for more details
            longClickPDFImage()
            verifyContextMenuForLinksToOtherHosts("wikipedia.org".toUri())
            dismissContentContextMenu()
        }
    }

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/832094
    @Test
    fun verifyOpenLinkInAppContextMenuOptionTest() {
        val defaultWebPage = TestAssetHelper.getExternalLinksAsset(mockWebServer)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(defaultWebPage.url) {
            longClickPageObject(itemContainingText("Youtube full link"))
            verifyContextMenuForLinksToOtherApps("youtube.com")
            clickContextMenuItem("Open link in external app")
            assertExternalAppOpens(Constants.PackageName.YOUTUBE_APP)
        }
    }
}