/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.ui.robots

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.CoreMatchers.allOf
import org.halalz.fenix.R
import org.halalz.fenix.helpers.DataGenerationHelper.getStringResource
import org.halalz.fenix.helpers.MatcherHelper.assertUIObjectExists
import org.halalz.fenix.helpers.MatcherHelper.itemContainingText
import org.halalz.fenix.helpers.MatcherHelper.itemWithDescription
import org.halalz.fenix.helpers.TestHelper.mDevice
import org.halalz.fenix.helpers.isChecked

/**
 * Implementation of Robot Pattern for the Open Links In Apps sub menu.
 */
class SettingsSubMenuOpenLinksInAppsRobot {

    fun verifyOpenLinksInAppsView(selectedOpenLinkInAppsOption: String) {
        assertUIObjectExists(
            goBackButton,
            itemContainingText(getStringResource(R.string.preferences_open_links_in_apps)),
            itemContainingText(getStringResource(R.string.preferences_open_links_in_apps_always)),
            itemContainingText(getStringResource(R.string.preferences_open_links_in_apps_ask)),
            itemContainingText(getStringResource(R.string.preferences_open_links_in_apps_never)),
        )
        verifySelectedOpenLinksInAppOption(selectedOpenLinkInAppsOption)
    }

    fun verifyPrivateOpenLinksInAppsView(selectedOpenLinkInAppsOption: String) {
        assertUIObjectExists(
            goBackButton,
            itemContainingText(getStringResource(R.string.preferences_open_links_in_apps)),
            itemContainingText(getStringResource(R.string.preferences_open_links_in_apps_ask)),
            itemContainingText(getStringResource(R.string.preferences_open_links_in_apps_never)),
        )
        verifySelectedOpenLinksInAppOption(selectedOpenLinkInAppsOption)
    }

    fun verifySelectedOpenLinksInAppOption(openLinkInAppsOption: String) =
        onView(
            allOf(
                withId(R.id.radio_button),
                hasSibling(withText(openLinkInAppsOption)),
            ),
        ).check(matches(isChecked(true)))

    fun clickOpenLinkInAppOption(openLinkInAppsOption: String) {
        when (openLinkInAppsOption) {
            "Always" -> alwaysOption.click()
            "Ask before opening" -> askBeforeOpeningOption.click()
            "Never" -> neverOption.click()
        }
    }

    class Transition {
        fun goBack(interact: SettingsRobot.() -> Unit): SettingsRobot.Transition {
            mDevice.waitForIdle()
            goBackButton.click()

            SettingsRobot().interact()
            return SettingsRobot.Transition()
        }
    }
}
private val goBackButton = itemWithDescription("Navigate up")
private val alwaysOption =
    itemContainingText(getStringResource(R.string.preferences_open_links_in_apps_always))
private val askBeforeOpeningOption =
    itemContainingText(getStringResource(R.string.preferences_open_links_in_apps_ask))
private val neverOption =
    itemContainingText(getStringResource(R.string.preferences_open_links_in_apps_never))
