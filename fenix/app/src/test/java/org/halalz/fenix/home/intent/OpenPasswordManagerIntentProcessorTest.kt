/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.home.intent

import android.content.Intent
import androidx.navigation.NavController
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.halalz.fenix.HomeActivity
import org.halalz.fenix.NavGraphDirections
import org.halalz.fenix.ext.nav
import org.halalz.fenix.helpers.FenixRobolectricTestRunner
import org.halalz.fenix.shortcut.PasswordManagerIntentProcessor

@RunWith(FenixRobolectricTestRunner::class)
class OpenPasswordManagerIntentProcessorTest {

    private lateinit var activity: HomeActivity
    private lateinit var navController: NavController
    private lateinit var out: Intent
    private lateinit var processor: OpenPasswordManagerIntentProcessor

    @Before
    fun setup() {
        activity = mockk(relaxed = true)
        navController = mockk(relaxed = true)
        out = mockk(relaxed = true)
        processor = OpenPasswordManagerIntentProcessor()
    }

    @Test
    fun `GIVEN a blank intent WHEN it is processed THEN nothing should happen`() {
        assertFalse(processor.process(Intent(), navController, out))

        verify { activity wasNot Called }
        verify { navController wasNot Called }
        verify { out wasNot Called }
    }

    @Test
    fun `GIVEN an intent with wrong action WHEN it is processed THEN nothing should happen`() {
        val intent = Intent().apply {
            action = TEST_WRONG_ACTION
        }

        assertFalse(processor.process(intent, navController, out))

        verify { activity wasNot Called }
        verify { navController wasNot Called }
        verify { out wasNot Called }
    }

    @Test
    fun `GIVEN an intent with correct action and extra boolean WHEN it is processed THEN navigate should be called`() {
        val intent = Intent().apply {
            action = PasswordManagerIntentProcessor.Companion.ACTION_OPEN_PASSWORD_MANAGER
            putExtra(HomeActivity.OPEN_PASSWORD_MANAGER, true)
        }

        assertTrue(processor.process(intent, navController, out))

        verify { navController.nav(null, org.halalz.fenix.NavGraphDirections.actionGlobalSavedLoginsAuthFragment()) }
        verify { out.removeExtra(HomeActivity.OPEN_PASSWORD_MANAGER) }
    }

    companion object {
        const val TEST_WRONG_ACTION = "test-action"
    }
}