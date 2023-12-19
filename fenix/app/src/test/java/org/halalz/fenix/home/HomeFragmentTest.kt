/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.home

import android.content.Context
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.SearchState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.top.sites.TopSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.halalz.fenix.FenixApplication
import org.halalz.fenix.HomeActivity
import org.halalz.fenix.R
import org.halalz.fenix.components.Core
import org.halalz.fenix.ext.application
import org.halalz.fenix.ext.components
import org.halalz.fenix.home.HomeFragment.Companion.AMAZON_SPONSORED_TITLE
import org.halalz.fenix.home.HomeFragment.Companion.EBAY_SPONSORED_TITLE
import org.halalz.fenix.home.HomeFragment.Companion.TOAST_ELEVATION
import org.halalz.fenix.utils.Settings
import org.halalz.fenix.utils.allowUndo

class HomeFragmentTest {

    private lateinit var settings: Settings
    private lateinit var context: Context
    private lateinit var core: Core
    private lateinit var homeFragment: HomeFragment

    @Before
    fun setup() {
        settings = mockk(relaxed = true)
        context = mockk(relaxed = true)
        core = mockk(relaxed = true)

        val fenixApplication: org.halalz.fenix.FenixApplication = mockk(relaxed = true)

        homeFragment = spyk(HomeFragment())

        every { context.application } returns fenixApplication
        every { homeFragment.context } answers { context }
        every { context.components.settings } answers { settings }
        every { context.components.core } answers { core }
    }

    @Test
    fun `WHEN getTopSitesConfig is called THEN it returns TopSitesConfig with non-null frecencyConfig`() {
        every { settings.topSitesMaxLimit } returns 10

        val topSitesConfig = homeFragment.getTopSitesConfig()

        assertNotNull(topSitesConfig.frecencyConfig)
    }

    @Test
    fun `GIVEN a topSitesMaxLimit WHEN getTopSitesConfig is called THEN it returns TopSitesConfig with totalSites = topSitesMaxLimit`() {
        val topSitesMaxLimit = 10
        every { settings.topSitesMaxLimit } returns topSitesMaxLimit

        val topSitesConfig = homeFragment.getTopSitesConfig()

        assertEquals(topSitesMaxLimit, topSitesConfig.totalSites)
    }

    @Test
    fun `GIVEN the selected search engine is set to eBay WHEN getTopSitesConfig is called THEN providerFilter filters the eBay provided top sites`() {
        val searchEngine: SearchEngine = mockk()
        val browserStore = BrowserStore(
            initialState = BrowserState(
                search = SearchState(
                    regionSearchEngines = listOf(searchEngine),
                ),
            ),
        )

        every { core.store } returns browserStore
        every { searchEngine.name } returns EBAY_SPONSORED_TITLE

        val eBayTopSite = TopSite.Provided(1L, EBAY_SPONSORED_TITLE, "eBay.com", "", "", "", 0L)
        val amazonTopSite = TopSite.Provided(2L, AMAZON_SPONSORED_TITLE, "Amazon.com", "", "", "", 0L)
        val firefoxTopSite = TopSite.Provided(3L, "Firefox", "mozilla.org", "", "", "", 0L)
        val providedTopSites = listOf(eBayTopSite, amazonTopSite, firefoxTopSite)

        val topSitesConfig = homeFragment.getTopSitesConfig()

        val filteredProvidedSites = providedTopSites.filter {
            topSitesConfig.providerConfig?.providerFilter?.invoke(it) ?: true
        }
        assertTrue(filteredProvidedSites.containsAll(listOf(amazonTopSite, firefoxTopSite)))
        assertFalse(filteredProvidedSites.contains(eBayTopSite))
    }

    @Test
    fun `WHEN configuration changed THEN menu is dismissed`() {
        val homeMenuView: HomeMenuView = mockk(relaxed = true)
        homeFragment.homeMenuView = homeMenuView

        homeFragment.onConfigurationChanged(mockk(relaxed = true))

        verify(exactly = 1) { homeMenuView.dismissMenu() }
    }

    fun `GIVEN the user is in normal mode WHEN checking if should enable wallpaper THEN return true`() {
        val activity: HomeActivity = mockk {
            every { themeManager.currentTheme.isPrivate } returns false
        }
        every { homeFragment.activity } returns activity

        assertTrue(homeFragment.shouldEnableWallpaper())
    }

    @Test
    fun `GIVEN the user is in private mode WHEN checking if should enable wallpaper THEN return false`() {
        val activity: HomeActivity = mockk {
            every { themeManager.currentTheme.isPrivate } returns true
        }
        every { homeFragment.activity } returns activity

        assertFalse(homeFragment.shouldEnableWallpaper())
    }

    @Test
    fun `WHEN a pinned top is removed THEN show the undo snackbar`() {
        try {
            val topSite = TopSite.Default(
                id = 1L,
                title = "Mozilla",
                url = "https://mozilla.org",
                null,
            )
            mockkStatic("org.halalz.fenix.utils.UndoKt")
            mockkStatic("androidx.lifecycle.LifecycleOwnerKt")
            val view: ViewGroup = mockk(relaxed = true)
            val lifecycleScope: LifecycleCoroutineScope = mockk(relaxed = true)
            every { any<LifecycleOwner>().lifecycleScope } returns lifecycleScope
            every { homeFragment.getString(R.string.snackbar_top_site_removed) } returns "Mocked Removed Top Site"
            every { homeFragment.getString(R.string.snackbar_deleted_undo) } returns "Mocked Undo Removal"
            every {
                any<CoroutineScope>().allowUndo(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } just Runs
            every { homeFragment.requireView() } returns view

            homeFragment.showUndoSnackbarForTopSite(topSite)

            verify {
                lifecycleScope.allowUndo(
                    view,
                    "Mocked Removed Top Site",
                    "Mocked Undo Removal",
                    any(),
                    any(),
                    any(),
                    TOAST_ELEVATION,
                    true,
                )
            }
        } finally {
            unmockkStatic("org.halalz.fenix.utils.UndoKt")
            unmockkStatic("androidx.lifecycle.LifecycleOwnerKt")
        }
    }
}