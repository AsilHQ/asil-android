/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.onboarding

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import mozilla.components.service.nimbus.evalJexlSafe
import mozilla.components.support.base.ext.areNotificationsEnabledSafe
import org.halalz.fenix.R
import org.halalz.fenix.components.accounts.FenixFxAEntryPoint
import org.halalz.fenix.ext.components
import org.halalz.fenix.ext.hideToolbar
import org.halalz.fenix.ext.nav
import org.halalz.fenix.ext.openSetDefaultBrowserOption
import org.halalz.fenix.ext.requireComponents
import org.halalz.fenix.nimbus.FxNimbus
import org.halalz.fenix.onboarding.view.JunoOnboardingScreen
import org.halalz.fenix.onboarding.view.OnboardingPageUiData
import org.halalz.fenix.onboarding.view.sequencePosition
import org.halalz.fenix.onboarding.view.telemetrySequenceId
import org.halalz.fenix.onboarding.view.toPageUiData
import org.halalz.fenix.settings.SupportUtils
import org.halalz.fenix.theme.FirefoxTheme
import org.halalz.gecko.search.SearchWidgetProvider

/**
 * Fragment displaying the juno onboarding flow.
 */
class JunoOnboardingFragment : Fragment() {

    private val pagesToDisplay by lazy {
        pagesToDisplay(
            canShowNotificationPage(requireContext()),
            canShowAddWidgetCard(),
        )
    }
    private val telemetryRecorder by lazy { JunoOnboardingTelemetryRecorder() }
    private val pinAppWidgetReceiver = WidgetPinnedReceiver()

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isNotATablet()) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val filter = IntentFilter(WidgetPinnedReceiver.ACTION)
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(pinAppWidgetReceiver, filter)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            FirefoxTheme {
                ScreenContent()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideToolbar()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isNotATablet()) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(pinAppWidgetReceiver)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Composable
    @Suppress("LongMethod")
    private fun ScreenContent() {
        val context = LocalContext.current
        println("Pages to display $pagesToDisplay")
        JunoOnboardingScreen(
            pagesToDisplay = pagesToDisplay,
            onMakeFirefoxDefaultClick = {
                activity?.openSetDefaultBrowserOption(useCustomTab = true)
                telemetryRecorder.onSetToDefaultClick(
                    sequenceId = pagesToDisplay.telemetrySequenceId(),
                    sequencePosition = pagesToDisplay.sequencePosition(OnboardingPageUiData.Type.DEFAULT_BROWSER),
                )
            },
            onSkipDefaultClick = {
                telemetryRecorder.onSkipSetToDefaultClick(
                    pagesToDisplay.telemetrySequenceId(),
                    pagesToDisplay.sequencePosition(OnboardingPageUiData.Type.DEFAULT_BROWSER),
                )
            },
            onPrivacyPolicyClick = { url ->
                startActivity(
                    SupportUtils.createSandboxCustomTabIntent(
                        context = context,
                        url = url,
                    ),
                )
                telemetryRecorder.onPrivacyPolicyClick(
                    pagesToDisplay.telemetrySequenceId(),
                    pagesToDisplay.sequencePosition(OnboardingPageUiData.Type.DEFAULT_BROWSER),
                )
            },
            onNotificationPermissionButtonClick = {
                requireComponents.notificationsDelegate.requestNotificationPermission()
                telemetryRecorder.onNotificationPermissionClick(
                    sequenceId = pagesToDisplay.telemetrySequenceId(),
                    sequencePosition =
                    pagesToDisplay.sequencePosition(OnboardingPageUiData.Type.NOTIFICATION_PERMISSION),
                )
            },
            onSkipNotificationClick = {
                telemetryRecorder.onSkipTurnOnNotificationsClick(
                    sequenceId = pagesToDisplay.telemetrySequenceId(),
                    sequencePosition =
                    pagesToDisplay.sequencePosition(OnboardingPageUiData.Type.NOTIFICATION_PERMISSION),
                )
            },
            onAddFirefoxWidgetClick = {
                telemetryRecorder.onAddSearchWidgetClick(
                    pagesToDisplay.telemetrySequenceId(),
                    pagesToDisplay.sequencePosition(OnboardingPageUiData.Type.ADD_SEARCH_WIDGET),
                )
                showAddSearchWidgetDialog()
            },
            onSkipFirefoxWidgetClick = {
                telemetryRecorder.onSkipAddWidgetClick(
                    pagesToDisplay.telemetrySequenceId(),
                    pagesToDisplay.sequencePosition(OnboardingPageUiData.Type.ADD_SEARCH_WIDGET),
                )
            },
            onFinish = {
                onFinish(
                    sequenceId = pagesToDisplay.telemetrySequenceId(),
                    sequencePosition = pagesToDisplay.sequencePosition(it.type),
                )
            },
            onImpression = {
                telemetryRecorder.onImpression(
                    sequenceId = pagesToDisplay.telemetrySequenceId(),
                    pageType = it.type,
                    sequencePosition = pagesToDisplay.sequencePosition(it.type),
                )
            },
        )
    }

    private fun showAddSearchWidgetDialog() {
        // Requesting to pin app widget is only available for Android 8.0 and above
        if (canShowAddWidgetCard()) {
            val appWidgetManager = AppWidgetManager.getInstance(activity)
            val searchWidgetProvider =
                ComponentName(requireActivity(), SearchWidgetProvider::class.java)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val successCallback = WidgetPinnedReceiver.getPendingIntent(requireContext())
                appWidgetManager.requestPinAppWidget(searchWidgetProvider, null, successCallback)
            }
        }
    }

    private fun onFinish(sequenceId: String, sequencePosition: String) {
        requireComponents.fenixOnboarding.finish()
        findNavController().nav(
            id = R.id.junoOnboardingFragment,
            directions = JunoOnboardingFragmentDirections.actionHome(),
        )
        telemetryRecorder.onOnboardingComplete(
            sequenceId = sequenceId,
            sequencePosition = sequencePosition,
        )
    }

    private fun canShowNotificationPage(context: Context) =
        !NotificationManagerCompat.from(context.applicationContext)
            .areNotificationsEnabledSafe() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private fun canShowAddWidgetCard() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    private fun isNotATablet() = !resources.getBoolean(R.bool.tablet)

    private fun pagesToDisplay(
        showNotificationPage: Boolean,
        showAddWidgetPage: Boolean,
    ): List<OnboardingPageUiData> {
        val junoOnboardingFeature = FxNimbus.features.junoOnboarding.value()
        val jexlConditions = junoOnboardingFeature.conditions
        val jexlHelper = requireContext().components.analytics.messagingStorage.helper

        return FxNimbus.features.junoOnboarding.value().cards.values.toPageUiData(
            showNotificationPage,
            showAddWidgetPage,
            jexlConditions,
        ) { condition -> jexlHelper.evalJexlSafe(condition) }
    }
}