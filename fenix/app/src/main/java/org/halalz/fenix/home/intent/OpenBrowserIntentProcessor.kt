/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.home.intent

import android.content.Intent
import androidx.navigation.NavController
import mozilla.components.support.utils.SafeIntent
import mozilla.components.support.utils.toSafeIntent
import org.halalz.fenix.BrowserDirection
import org.halalz.fenix.HomeActivity

/**
 * The [org.halalz.fenix.IntentReceiverActivity] may set the [HomeActivity.OPEN_TO_BROWSER] flag
 * when the browser should be opened in response to an intent.
 */
class OpenBrowserIntentProcessor(
    private val activity: HomeActivity,
    private val getIntentSessionId: (SafeIntent) -> String?,
) : HomeIntentProcessor {

    override fun process(intent: Intent, navController: NavController, out: Intent): Boolean {
        return if (intent.extras?.getBoolean(HomeActivity.OPEN_TO_BROWSER) == true) {
            out.putExtra(HomeActivity.OPEN_TO_BROWSER, false)

            activity.openToBrowser(org.halalz.fenix.BrowserDirection.FromGlobal, getIntentSessionId(intent.toSafeIntent()))
            true
        } else {
            false
        }
    }
}
