/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.home.intent

import android.content.Intent
import androidx.navigation.NavController
import org.halalz.fenix.HomeActivity
import org.halalz.fenix.ext.nav
import org.halalz.fenix.NavGraphDirections

/**
 * When the open password manager shortcut is tapped, Fenix should open to the password and login fragment.
 */
class OpenPasswordManagerIntentProcessor : HomeIntentProcessor {

    override fun process(intent: Intent, navController: NavController, out: Intent): Boolean {
        return if (intent.extras?.getBoolean(HomeActivity.OPEN_PASSWORD_MANAGER) == true) {
            out.removeExtra(HomeActivity.OPEN_PASSWORD_MANAGER)

            val directions = NavGraphDirections.actionGlobalSavedLoginsAuthFragment()
            navController.nav(null, directions)
            true
        } else {
            false
        }
    }
}
