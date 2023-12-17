/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.onboarding

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import mozilla.components.lib.state.ext.observeAsComposableState
import mozilla.telemetry.glean.private.NoExtras
import org.halalz.fenix.GleanMetrics.Wallpapers
import org.halalz.fenix.R
import org.halalz.fenix.components.FenixSnackbar
import org.halalz.fenix.ext.components
import org.halalz.fenix.ext.requireComponents
import org.halalz.fenix.ext.settings
import org.halalz.fenix.settings.wallpaper.getWallpapersForOnboarding
import org.halalz.fenix.theme.FirefoxTheme
import org.halalz.fenix.wallpapers.Wallpaper
import org.halalz.fenix.wallpapers.WallpaperOnboarding
import org.halalz.fenix.NavGraphDirections

/**
 * Dialog displaying the wallpapers onboarding.
 */
@OptIn(ExperimentalMaterialApi::class)
class WallpaperOnboardingDialogFragment : BottomSheetDialogFragment() {
    private val appStore by lazy {
        requireComponents.appStore
    }

    private val wallpaperUseCases by lazy {
        requireComponents.useCases.wallpaperUseCases
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.WallpaperOnboardingDialogStyle)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        val currentWallpaper = requireContext().components.appStore.state.wallpaperState.currentWallpaper
        Wallpapers.onboardingClosed.record(
            Wallpapers.OnboardingClosedExtra(
                isSelected = currentWallpaper.name != Wallpaper.defaultName,
            ),
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireContext().settings().showWallpaperOnboarding = false
        Wallpapers.onboardingOpened.record(NoExtras())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        this@WallpaperOnboardingDialogFragment.dialog?.setCanceledOnTouchOutside(true)

        setContent {
            FirefoxTheme {
                val wallpapers = appStore.observeAsComposableState { state ->
                    state.wallpaperState.availableWallpapers.getWallpapersForOnboarding()
                }.value ?: listOf()
                val currentWallpaper = appStore.observeAsComposableState { state ->
                    state.wallpaperState.currentWallpaper
                }.value ?: Wallpaper.Default

                val coroutineScope = rememberCoroutineScope()

                WallpaperOnboarding(
                    wallpapers = wallpapers,
                    currentWallpaper = currentWallpaper,
                    onCloseClicked = { dismiss() },
                    onExploreMoreButtonClicked = {
                        val directions = NavGraphDirections.actionGlobalWallpaperSettingsFragment()
                        findNavController().navigate(directions)
                        Wallpapers.onboardingExploreMoreClick.record(NoExtras())
                    },
                    loadWallpaperResource = { wallpaperUseCases.loadThumbnail(it) },
                    onSelectWallpaper = {
                        coroutineScope.launch {
                            val result = wallpaperUseCases.selectWallpaper(it)
                            onWallpaperSelected(it, result, this@WallpaperOnboardingDialogFragment.requireView())
                        }
                    },
                )
            }
        }
    }

    private fun onWallpaperSelected(
        wallpaper: Wallpaper,
        result: Wallpaper.ImageFileState,
        view: View,
    ) {
        when (result) {
            Wallpaper.ImageFileState.Downloaded -> {
                Wallpapers.wallpaperSelected.record(
                    Wallpapers.WallpaperSelectedExtra(
                        name = wallpaper.name,
                        source = "onboarding",
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
    }

    companion object {
        // The number of wallpaper thumbnails to display.
        const val THUMBNAILS_SELECTION_COUNT = 6

        // The desired amount of seasonal wallpapers inside of the selector.
        const val SEASONAL_WALLPAPERS_COUNT = 3

        // The desired amount of seasonal wallpapers inside of the selector.
        const val CLASSIC_WALLPAPERS_COUNT = 2
    }
}
