/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.settings.wallpaper

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import mozilla.components.lib.state.ext.observeAsComposableState
import mozilla.components.service.glean.private.NoExtras
import org.halalz.fenix.BrowserDirection
import org.halalz.fenix.GleanMetrics.Wallpapers
import org.halalz.fenix.HomeActivity
import org.halalz.fenix.R
import org.halalz.fenix.browser.browsingmode.BrowsingMode
import org.halalz.fenix.components.FenixSnackbar
import org.halalz.fenix.ext.requireComponents
import org.halalz.fenix.ext.settings
import org.halalz.fenix.ext.showToolbar
import org.halalz.fenix.theme.FirefoxTheme
import org.halalz.fenix.wallpapers.Wallpaper

class WallpaperSettingsFragment : Fragment() {
    private val appStore by lazy {
        requireComponents.appStore
    }

    private val wallpaperUseCases by lazy {
        requireComponents.useCases.wallpaperUseCases
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Wallpapers.wallpaperSettingsOpened.record(NoExtras())
        val wallpaperSettings = ComposeView(requireContext()).apply {
            setContent {
                FirefoxTheme {
                    val wallpapers = appStore.observeAsComposableState { state ->
                        state.wallpaperState.availableWallpapers
                    }.value ?: listOf()
                    val currentWallpaper = appStore.observeAsComposableState { state ->
                        state.wallpaperState.currentWallpaper
                    }.value ?: Wallpaper.Default

                    val coroutineScope = rememberCoroutineScope()

                    WallpaperSettings(
                        wallpaperGroups = wallpapers.groupByDisplayableCollection(),
                        defaultWallpaper = Wallpaper.Default,
                        selectedWallpaper = currentWallpaper,
                        loadWallpaperResource = {
                            wallpaperUseCases.loadThumbnail(it)
                        },
                        onSelectWallpaper = {
                            if (it.name != currentWallpaper.name) {
                                coroutineScope.launch {
                                    val result = wallpaperUseCases.selectWallpaper(it)
                                    onWallpaperSelected(it, result, requireView())
                                }
                            }
                        },
                        onLearnMoreClick = { url, collectionName ->
                            (activity as HomeActivity).openToBrowserAndLoad(
                                searchTermOrURL = url,
                                newTab = true,
                                from = org.halalz.fenix.BrowserDirection.FromWallpaper,
                            )
                            Wallpapers.learnMoreLinkClick.record(
                                Wallpapers.LearnMoreLinkClickExtra(
                                    url = url,
                                    collectionName = collectionName,
                                ),
                            )
                        },
                    )
                }
            }
        }

        // Using CoordinatorLayout as a parent view for the fragment gives the benefit of hiding
        // snackbars automatically when the fragment is closed.
        return CoordinatorLayout(requireContext()).apply {
            addView(wallpaperSettings)
        }
    }

    private fun onWallpaperSelected(
        wallpaper: Wallpaper,
        result: Wallpaper.ImageFileState,
        view: View,
    ) {
        when (result) {
            Wallpaper.ImageFileState.Downloaded -> {
                FenixSnackbar.make(
                    view = view,
                    isDisplayedWithBrowserToolbar = false,
                )
                    .setText(view.context.getString(R.string.wallpaper_updated_snackbar_message))
                    .setAction(requireContext().getString(R.string.wallpaper_updated_snackbar_action)) {
                        (activity as HomeActivity).browsingModeManager.mode = BrowsingMode.Normal
                        findNavController().navigate(R.id.homeFragment)
                    }
                    .show()

                Wallpapers.wallpaperSelected.record(
                    Wallpapers.WallpaperSelectedExtra(
                        name = wallpaper.name,
                        source = "settings",
                        themeCollection = wallpaper.collection.name,
                    ),
                )
            }
            Wallpaper.ImageFileState.Error -> {
                FenixSnackbar.make(
                    view = view,
                    isDisplayedWithBrowserToolbar = false,
                )
                    .setText(view.context.getString(R.string.wallpaper_download_error_snackbar_message))
                    .setAction(view.context.getString(R.string.wallpaper_download_error_snackbar_action)) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            val retryResult = wallpaperUseCases.selectWallpaper(wallpaper)
                            onWallpaperSelected(wallpaper, retryResult, view)
                        }
                    }
                    .show()
            }
            else -> { /* noop */ }
        }

        view.context.settings().showWallpaperOnboarding = false
    }

    override fun onResume() {
        super.onResume()
        showToolbar(getString(R.string.customize_wallpapers))
    }
}