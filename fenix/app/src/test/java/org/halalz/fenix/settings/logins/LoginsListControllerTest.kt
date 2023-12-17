/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.settings.logins

import androidx.navigation.NavController
import io.mockk.mockk
import io.mockk.verifyAll
import mozilla.components.service.glean.testing.GleanTestRule
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.halalz.fenix.BrowserDirection
import org.halalz.fenix.GleanMetrics.Logins
import org.halalz.fenix.helpers.FenixRobolectricTestRunner
import org.halalz.fenix.settings.SupportUtils
import org.halalz.fenix.settings.logins.controller.LoginsListController
import org.halalz.fenix.settings.logins.fragment.SavedLoginsFragmentDirections
import org.halalz.fenix.utils.Settings

@RunWith(FenixRobolectricTestRunner::class)
class LoginsListControllerTest {
    @get:Rule
    val gleanTestRule = GleanTestRule(testContext)

    private val store: LoginsFragmentStore = mockk(relaxed = true)
    private val settings: Settings = mockk(relaxed = true)
    private val sortingStrategy: SortingStrategy = SortingStrategy.Alphabetically
    private val navController: NavController = mockk(relaxed = true)
    private val browserNavigator: (String, Boolean, org.halalz.fenix.BrowserDirection) -> Unit = mockk(relaxed = true)
    private val controller =
        LoginsListController(
            loginsFragmentStore = store,
            navController = navController,
            browserNavigator = browserNavigator,
            settings = settings,
        )

    @Test
    fun `handle selecting the sorting strategy and save pref`() {
        controller.handleSort(sortingStrategy)

        verifyAll {
            store.dispatch(LoginsAction.SortLogins(SortingStrategy.Alphabetically))
            settings.savedLoginsSortingStrategy = sortingStrategy
        }
    }

    @Test
    fun `handle login item clicked`() {
        val login: SavedLogin = mockk(relaxed = true)
        assertNull(Logins.openIndividualLogin.testGetValue())

        controller.handleItemClicked(login)

        verifyAll {
            store.dispatch(LoginsAction.LoginSelected(login))
            navController.navigate(
                SavedLoginsFragmentDirections.actionSavedLoginsFragmentToLoginDetailFragment(login.guid),
            )
        }

        assertNotNull(Logins.openIndividualLogin.testGetValue())
        assertEquals(1, Logins.openIndividualLogin.testGetValue()!!.size)
        assertNull(Logins.openIndividualLogin.testGetValue()!!.single().extra)
    }

    @Test
    fun `Open the correct support webpage when Learn More is clicked`() {
        controller.handleLearnMoreClicked()

        verifyAll {
            browserNavigator.invoke(
                SupportUtils.getGenericSumoURLForTopic(SupportUtils.SumoTopic.SYNC_SETUP),
                true,
                org.halalz.fenix.BrowserDirection.FromSavedLoginsFragment,
            )
        }
    }

    @Test
    fun `handle add login clicked`() {
        controller.handleAddLoginClicked()

        verifyAll {
            navController.navigate(
                SavedLoginsFragmentDirections.actionSavedLoginsFragmentToAddLoginFragment(),
            )
        }
    }
}
