/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.home.intent

import android.content.Intent
import androidx.navigation.NavController
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import mozilla.components.concept.engine.EngineSession
import mozilla.components.service.glean.testing.GleanTestRule
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.halalz.fenix.BrowserDirection
import org.halalz.fenix.GleanMetrics.Events
import org.halalz.fenix.HomeActivity
import org.halalz.fenix.NavGraphDirections
import org.halalz.fenix.browser.browsingmode.BrowsingModeManager
import org.halalz.fenix.helpers.FenixRobolectricTestRunner
import org.halalz.fenix.onboarding.ReEngagementNotificationWorker
import org.halalz.fenix.utils.Settings

@RunWith(FenixRobolectricTestRunner::class)
class ReEngagementIntentProcessorTest {

    @get:Rule
    val gleanTestRule = GleanTestRule(testContext)

    @Test
    fun `do not process blank intents`() {
        val navController: NavController = mockk()
        val out: Intent = mockk()
        val settings: Settings = mockk()
        val result = ReEngagementIntentProcessor(mockk(), settings)
            .process(Intent(), navController, out)

        assertFalse(result)
        verify { navController wasNot Called }
        verify { out wasNot Called }
    }

    @Test
    fun `WHEN re-engagement notification type is type A THEN load target URL`() {
        val navController: NavController = mockk(relaxed = true)
        val out: Intent = mockk()
        val activity: HomeActivity = mockk(relaxed = true)
        val browsingModeManager: BrowsingModeManager = mockk(relaxed = true)
        val settings: Settings = mockk(relaxed = true)

        val intent = Intent().apply {
            putExtra("org.halalz.fenix.Re-engagement.intent", true)
        }
        every { activity.applicationContext } returns testContext
        every { activity.browsingModeManager } returns browsingModeManager
        every { settings.reEngagementNotificationType } returns ReEngagementNotificationWorker.NOTIFICATION_TYPE_A

        assertNull(Events.reEngagementNotifTapped.testGetValue())

        val result = ReEngagementIntentProcessor(activity, settings)
            .process(intent, navController, out)

        assert(result)

        assertNotNull(Events.reEngagementNotifTapped.testGetValue())
        verify {
            activity.openToBrowserAndLoad(
                searchTermOrURL = ReEngagementNotificationWorker.NOTIFICATION_TARGET_URL,
                newTab = true,
                from = org.halalz.fenix.BrowserDirection.FromGlobal,
                customTabSessionId = null,
                engine = null,
                forceSearch = false,
                flags = EngineSession.LoadUrlFlags.external(),
                requestDesktopMode = false,
                historyMetadata = null,
            )
        }
        verify { navController wasNot Called }
        verify { out wasNot Called }
    }

    @Test
    fun `WHEN re-engagement notification type is 2 THEN open search dialog`() {
        val navController: NavController = mockk(relaxed = true)
        val out: Intent = mockk()
        val activity: HomeActivity = mockk(relaxed = true)
        val browsingModeManager: BrowsingModeManager = mockk(relaxed = true)
        val settings: Settings = mockk(relaxed = true)

        val intent = Intent().apply {
            putExtra("org.halalz.fenix.Re-engagement.intent", true)
        }
        every { activity.applicationContext } returns testContext
        every { activity.browsingModeManager } returns browsingModeManager
        every { settings.reEngagementNotificationType } returns ReEngagementNotificationWorker.NOTIFICATION_TYPE_B

        assertNull(Events.reEngagementNotifTapped.testGetValue())

        val result = ReEngagementIntentProcessor(activity, settings)
            .process(intent, navController, out)

        assert(result)

        assertNotNull(Events.reEngagementNotifTapped.testGetValue())
        val directions = org.halalz.fenix.NavGraphDirections.actionGlobalSearchDialog(sessionId = null)
        verify { navController.navigate(directions, navOptions = any()) }
    }
}
