/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.home.intent

import android.content.Intent
import androidx.navigation.NavController
import androidx.navigation.navOptions
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.halalz.fenix.NavGraphDirections
import org.halalz.fenix.R
import org.halalz.fenix.components.metrics.MetricsUtils
import org.halalz.fenix.ext.nav
import org.halalz.fenix.helpers.FenixRobolectricTestRunner

@RunWith(FenixRobolectricTestRunner::class)
class AssistIntentProcessorTest {
    private val navController: NavController = mockk(relaxed = true)
    private val out: Intent = mockk(relaxed = true)

    @Test
    fun `GIVEN an intent with wrong action WHEN it is processed THEN nothing should happen`() {
        val intent = Intent().apply {
            action = TEST_WRONG_ACTION
        }
        val result = StartSearchIntentProcessor().process(intent, navController, out)

        verify { navController wasNot Called }
        verify { out wasNot Called }
        assertFalse(result)
    }

    @Test
    fun `GIVEN an intent with ACTION_ASSIST action WHEN it is processed THEN navigate to the search dialog`() {
        val intent = Intent().apply {
            action = Intent.ACTION_ASSIST
        }

        AssistIntentProcessor().process(intent, navController, out)
        val options = navOptions {
            popUpTo(R.id.homeFragment)
        }

        verify {
            navController.nav(
                null,
                org.halalz.fenix.NavGraphDirections.actionGlobalSearchDialog(
                    sessionId = null,
                    searchAccessPoint = MetricsUtils.Source.NONE,
                ),
                options,
            )
        }

        verify { out wasNot Called }
    }

    companion object {
        const val TEST_WRONG_ACTION = "test-action"
    }
}
