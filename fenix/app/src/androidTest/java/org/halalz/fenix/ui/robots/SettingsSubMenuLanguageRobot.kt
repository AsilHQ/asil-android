/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.ui.robots

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import org.hamcrest.CoreMatchers
import org.halalz.fenix.R
import org.halalz.fenix.helpers.MatcherHelper.assertUIObjectExists
import org.halalz.fenix.helpers.MatcherHelper.itemWithText
import org.halalz.fenix.helpers.TestAssetHelper.waitingTime
import org.halalz.fenix.helpers.TestHelper.mDevice
import org.halalz.fenix.helpers.TestHelper.packageName
import org.halalz.fenix.helpers.click

class SettingsSubMenuLanguageRobot {
    fun selectLanguage(language: String) {
        languagesList.waitForExists(waitingTime)
        languagesList
            .getChildByText(UiSelector().text(language), language)
            .click()
    }

    fun selectLanguageSearchResult(languageName: String) {
        language(languageName).waitForExists(waitingTime)
        language(languageName).click()
    }

    fun verifyLanguageHeaderIsTranslated(translation: String) = assertUIObjectExists(itemWithText(translation))

    fun verifySelectedLanguage(language: String) {
        languagesList.waitForExists(waitingTime)
        assertUIObjectExists(
            languagesList
                .getChildByText(UiSelector().text(language), language, true)
                .getFromParent(UiSelector().resourceId("$packageName:id/locale_selected_icon")),
        )
    }

    fun openSearchBar() {
        onView(withId(R.id.search)).click()
    }

    fun typeInSearchBar(text: String) {
        searchBar.waitForExists(waitingTime)
        searchBar.text = text
    }

    fun verifySearchResultsContains(languageName: String) =
        assertUIObjectExists(language(languageName))

    fun clearSearchBar() {
        onView(withId(R.id.search_close_btn)).click()
    }

    fun verifyLanguageListIsDisplayed() = assertUIObjectExists(languagesList)

    class Transition {

        fun goBack(interact: SettingsRobot.() -> Unit): SettingsRobot.Transition {
            mDevice.waitForIdle()
            goBackButton().perform(ViewActions.click())

            SettingsRobot().interact()
            return SettingsRobot.Transition()
        }
    }
}

private fun goBackButton() =
    onView(CoreMatchers.allOf(ViewMatchers.withContentDescription("Navigate up")))

private val languagesList =
    UiScrollable(
        UiSelector()
            .resourceId("$packageName:id/locale_list")
            .scrollable(true),
    )

private fun language(name: String) = mDevice.findObject(UiSelector().text(name))

private val searchBar =
    mDevice.findObject(UiSelector().resourceId("$packageName:id/search_src_text"))