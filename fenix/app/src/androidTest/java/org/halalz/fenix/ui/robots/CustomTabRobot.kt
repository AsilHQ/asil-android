/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.halalz.fenix.ui.robots

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiSelector
import org.halalz.fenix.R
import org.halalz.fenix.helpers.Constants.LONG_CLICK_DURATION
import org.halalz.fenix.helpers.DataGenerationHelper.getStringResource
import org.halalz.fenix.helpers.MatcherHelper.assertUIObjectExists
import org.halalz.fenix.helpers.MatcherHelper.itemContainingText
import org.halalz.fenix.helpers.MatcherHelper.itemWithDescription
import org.halalz.fenix.helpers.MatcherHelper.itemWithResId
import org.halalz.fenix.helpers.MatcherHelper.itemWithResIdAndText
import org.halalz.fenix.helpers.MatcherHelper.itemWithResIdContainingText
import org.halalz.fenix.helpers.TestAssetHelper.waitingTime
import org.halalz.fenix.helpers.TestHelper.appName
import org.halalz.fenix.helpers.TestHelper.mDevice
import org.halalz.fenix.helpers.TestHelper.packageName
import org.halalz.fenix.helpers.TestHelper.waitForObjects
import org.halalz.fenix.helpers.click

/**
 *  Implementation of the robot pattern for Custom tabs
 */
class CustomTabRobot {

    fun verifyCustomTabsSiteInfoButton() =
        assertUIObjectExists(
            itemWithResId("$packageName:id/mozac_browser_toolbar_security_indicator"),
        )

    fun verifyCustomTabsShareButton() =
        assertUIObjectExists(
            itemWithDescription(getStringResource(R.string.mozac_feature_customtabs_share_link)),
        )

    fun verifyMainMenuButton() = assertUIObjectExists(mainMenuButton)

    fun verifyDesktopSiteButtonExists() {
        desktopSiteButton().check(matches(isDisplayed()))
    }

    fun verifyFindInPageButtonExists() {
        findInPageButton().check(matches(isDisplayed()))
    }

    fun verifyPoweredByTextIsDisplayed() =
        assertUIObjectExists(itemContainingText("POWERED BY $appName"))

    fun verifyOpenInBrowserButtonExists() {
        openInBrowserButton().check(matches(isDisplayed()))
    }

    fun verifyBackButtonExists() = assertUIObjectExists(itemWithDescription("Back"))

    fun verifyForwardButtonExists() = assertUIObjectExists(itemWithDescription("Forward"))

    fun verifyRefreshButtonExists() = assertUIObjectExists(itemWithDescription("Refresh"))

    fun verifyCustomMenuItem(label: String) = assertUIObjectExists(itemContainingText(label))

    fun verifyCustomTabCloseButton() {
        closeButton().check(matches(isDisplayed()))
    }

    fun verifyCustomTabToolbarTitle(title: String) {
        waitForPageToLoad()

        mDevice.waitForObjects(
            mDevice.findObject(
                UiSelector()
                    .resourceId("$packageName:id/mozac_browser_toolbar_title_view")
                    .textContains(title),
            )
                .getFromParent(
                    UiSelector().resourceId("$packageName:id/mozac_browser_toolbar_origin_view"),
                ),
            waitingTime,
        )

        assertUIObjectExists(
            itemWithResIdContainingText("$packageName:id/mozac_browser_toolbar_title_view", title),
        )
    }

    fun verifyCustomTabUrl(Url: String) {
        assertUIObjectExists(
            itemWithResIdContainingText("$packageName:id/mozac_browser_toolbar_url_view", Url.drop(7)),
        )
    }

    fun longCLickAndCopyToolbarUrl() {
        mDevice.waitForObjects(
            mDevice.findObject(UiSelector().resourceId("$packageName:id/toolbar")),
            waitingTime,
        )
        customTabToolbar().click(LONG_CLICK_DURATION)
        clickContextMenuItem("Copy")
    }

    fun fillAndSubmitLoginCredentials(userName: String, password: String) {
        mDevice.waitForIdle(waitingTime)
        setPageObjectText(itemWithResId("username"), userName)
        setPageObjectText(itemWithResId("password"), password)
        clickPageObject(itemWithResId("submit"))
        mDevice.waitForObjects(
            mDevice.findObject(UiSelector().resourceId("$packageName:id/save_confirm")),
            waitingTime,
        )
    }

    fun waitForPageToLoad() = progressBar.waitUntilGone(waitingTime)

    fun clickCustomTabCloseButton() = closeButton().click()

    fun verifyCustomTabActionButton(customTabActionButtonDescription: String) =
        assertUIObjectExists(itemWithDescription(customTabActionButtonDescription))

    fun verifyPDFReaderToolbarItems() =
        assertUIObjectExists(
            itemWithResIdAndText("download", "Download"),
            itemWithResIdAndText("openInApp", "Open in app"),
        )

    class Transition {
        fun openMainMenu(interact: CustomTabRobot.() -> Unit): Transition {
            mainMenuButton.also {
                it.waitForExists(waitingTime)
                it.click()
            }

            CustomTabRobot().interact()
            return Transition()
        }

        fun clickOpenInBrowserButton(interact: BrowserRobot.() -> Unit): BrowserRobot.Transition {
            openInBrowserButton().perform(click())

            BrowserRobot().interact()
            return BrowserRobot.Transition()
        }

        fun clickShareButton(interact: ShareOverlayRobot.() -> Unit): ShareOverlayRobot.Transition {
            itemWithDescription(getStringResource(R.string.mozac_feature_customtabs_share_link)).click()

            ShareOverlayRobot().interact()
            return ShareOverlayRobot.Transition()
        }
    }
}

fun customTabScreen(interact: CustomTabRobot.() -> Unit): CustomTabRobot.Transition {
    CustomTabRobot().interact()
    return CustomTabRobot.Transition()
}

private val mainMenuButton = itemWithResId("$packageName:id/mozac_browser_toolbar_menu")

private fun desktopSiteButton() = onView(withId(R.id.switch_widget))

private fun findInPageButton() = onView(withText("Find in page"))

private fun openInBrowserButton() = onView(withText("Open in $appName"))

private fun closeButton() = onView(withContentDescription("Return to previous app"))

private fun customTabToolbar() = mDevice.findObject(By.res("$packageName:id/toolbar"))

private val progressBar =
    mDevice.findObject(
        UiSelector().resourceId("$packageName:id/mozac_browser_toolbar_progress"),
    )
