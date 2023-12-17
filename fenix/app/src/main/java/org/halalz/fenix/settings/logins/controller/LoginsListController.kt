/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.settings.logins.controller

import androidx.navigation.NavController
import mozilla.components.service.glean.private.NoExtras
import org.halalz.fenix.BrowserDirection
import org.halalz.fenix.GleanMetrics.Logins
import org.halalz.fenix.settings.SupportUtils
import org.halalz.fenix.settings.logins.LoginsAction
import org.halalz.fenix.settings.logins.LoginsFragmentStore
import org.halalz.fenix.settings.logins.SavedLogin
import org.halalz.fenix.settings.logins.SortingStrategy
import org.halalz.fenix.settings.logins.fragment.SavedLoginsFragmentDirections
import org.halalz.fenix.utils.Settings

/**
 * Controller for the saved logins list
 *
 * @param loginsFragmentStore Store used to hold in-memory collection state.
 * @param navController NavController manages app navigation within a NavHost.
 * @param browserNavigator Controller allowing browser navigation to any Uri.
 * @param settings SharedPreferences wrapper for easier usage.
 */
class LoginsListController(
    private val loginsFragmentStore: LoginsFragmentStore,
    private val navController: NavController,
    private val browserNavigator: (
        searchTermOrURL: String,
        newTab: Boolean,
        from: org.halalz.fenix.BrowserDirection,
    ) -> Unit,
    private val settings: Settings,
) {

    fun handleItemClicked(item: SavedLogin) {
        Logins.managementLoginsTapped.record(NoExtras())
        loginsFragmentStore.dispatch(LoginsAction.LoginSelected(item))
        Logins.openIndividualLogin.record(NoExtras())
        navController.navigate(
            SavedLoginsFragmentDirections.actionSavedLoginsFragmentToLoginDetailFragment(item.guid),
        )
    }

    fun handleAddLoginClicked() {
        Logins.managementAddTapped.record(NoExtras())
        navController.navigate(
            SavedLoginsFragmentDirections.actionSavedLoginsFragmentToAddLoginFragment(),
        )
    }

    fun handleLearnMoreClicked() {
        browserNavigator.invoke(
            SupportUtils.getGenericSumoURLForTopic(SupportUtils.SumoTopic.SYNC_SETUP),
            true,
            org.halalz.fenix.BrowserDirection.FromSavedLoginsFragment,
        )
    }

    fun handleSort(sortingStrategy: SortingStrategy) {
        loginsFragmentStore.dispatch(
            LoginsAction.SortLogins(
                sortingStrategy,
            ),
        )
        settings.savedLoginsSortingStrategy = sortingStrategy
    }
}
