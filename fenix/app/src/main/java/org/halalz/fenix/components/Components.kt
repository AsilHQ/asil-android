/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.components

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import com.google.android.play.core.review.ReviewManagerFactory
import mozilla.components.feature.addons.AddonManager
import mozilla.components.feature.addons.amo.AMOAddonsProvider
import mozilla.components.feature.addons.migration.DefaultSupportedAddonsChecker
import mozilla.components.feature.addons.update.DefaultAddonUpdater
import mozilla.components.feature.autofill.AutofillConfiguration
import mozilla.components.lib.publicsuffixlist.PublicSuffixList
import mozilla.components.support.base.android.NotificationsDelegate
import mozilla.components.support.base.worker.Frequency
import org.halalz.fenix.R
import org.halalz.fenix.autofill.AutofillConfirmActivity
import org.halalz.fenix.autofill.AutofillSearchActivity
import org.halalz.fenix.autofill.AutofillUnlockActivity
import org.halalz.fenix.components.appstate.AppState
import org.halalz.fenix.components.metrics.MetricsMiddleware
import org.halalz.fenix.datastore.pocketStoriesSelectedCategoriesDataStore
import org.halalz.fenix.ext.asRecentTabs
import org.halalz.fenix.ext.components
import org.halalz.fenix.ext.filterState
import org.halalz.fenix.ext.settings
import org.halalz.fenix.ext.sort
import org.halalz.fenix.home.PocketUpdatesMiddleware
import org.halalz.fenix.home.blocklist.BlocklistHandler
import org.halalz.fenix.home.blocklist.BlocklistMiddleware
import org.halalz.fenix.messaging.state.MessagingMiddleware
import org.halalz.fenix.onboarding.FenixOnboarding
import org.halalz.fenix.perf.AppStartReasonProvider
import org.halalz.fenix.perf.StartupActivityLog
import org.halalz.fenix.perf.StartupStateProvider
import org.halalz.fenix.perf.StrictModeManager
import org.halalz.fenix.perf.lazyMonitored
import org.halalz.fenix.utils.ClipboardHandler
import org.halalz.fenix.utils.Settings
import org.halalz.fenix.wifi.WifiConnectionMonitor
import org.halalz.fenix.BuildConfig
import java.util.concurrent.TimeUnit

private const val AMO_COLLECTION_MAX_CACHE_AGE = 2 * 24 * 60L // Two days in minutes

/**
 * Provides access to all components. This class is an implementation of the Service Locator
 * pattern, which helps us manage the dependencies in our app.
 *
 * Note: these aren't just "components" from "android-components": they're any "component" that
 * can be considered a building block of our app.
 */
class Components(private val context: Context) {
    val backgroundServices by lazyMonitored {
        BackgroundServices(
            context,
            push,
            analytics.crashReporter,
            core.lazyHistoryStorage,
            core.lazyBookmarksStorage,
            core.lazyPasswordsStorage,
            core.lazyRemoteTabsStorage,
            core.lazyAutofillStorage,
            strictMode,
        )
    }
    val services by lazyMonitored { Services(context, backgroundServices.accountManager) }
    val core by lazyMonitored { Core(context, analytics.crashReporter, strictMode) }

    @Suppress("Deprecation")
    val useCases by lazyMonitored {
        UseCases(
            context,
            core.engine,
            core.store,
            core.webAppShortcutManager,
            core.topSitesStorage,
            core.bookmarksStorage,
            core.historyStorage,
            appStore,
            core.client,
            strictMode,
        )
    }

    private val notificationManagerCompat = NotificationManagerCompat.from(context)

    val notificationsDelegate: NotificationsDelegate by lazyMonitored {
        NotificationsDelegate(
            notificationManagerCompat,
        )
    }

    val intentProcessors by lazyMonitored {
        IntentProcessors(
            context,
            core.store,
            useCases.sessionUseCases,
            useCases.tabsUseCases,
            useCases.customTabsUseCases,
            useCases.searchUseCases,
            core.webAppManifestStorage,
            core.engine,
        )
    }

    val addonsProvider by lazyMonitored {
        // Check if we have a customized (overridden) AMO collection (supported in Nightly & Beta)
        if (org.halalz.fenix.FeatureFlags.customExtensionCollectionFeature && context.settings().amoCollectionOverrideConfigured()) {
            AMOAddonsProvider(
                context,
                core.client,
                collectionUser = context.settings().overrideAmoUser,
                collectionName = context.settings().overrideAmoCollection,
            )
        }
        // Use build config otherwise
        else if (!BuildConfig.AMO_COLLECTION_USER.isNullOrEmpty() &&
            !BuildConfig.AMO_COLLECTION_NAME.isNullOrEmpty()
        ) {
            AMOAddonsProvider(
                context,
                core.client,
                serverURL = BuildConfig.AMO_SERVER_URL,
                collectionUser = BuildConfig.AMO_COLLECTION_USER,
                collectionName = BuildConfig.AMO_COLLECTION_NAME,
                maxCacheAgeInMinutes = AMO_COLLECTION_MAX_CACHE_AGE,
            )
        }
        // Fall back to defaults
        else {
            AMOAddonsProvider(context, core.client, maxCacheAgeInMinutes = AMO_COLLECTION_MAX_CACHE_AGE)
        }
    }

    @Suppress("MagicNumber")
    val addonUpdater by lazyMonitored {
        DefaultAddonUpdater(context, Frequency(12, TimeUnit.HOURS), notificationsDelegate)
    }

    @Suppress("MagicNumber")
    val supportedAddonsChecker by lazyMonitored {
        DefaultSupportedAddonsChecker(
            context,
            Frequency(12, TimeUnit.HOURS),
        )
    }

    val addonManager by lazyMonitored {
        AddonManager(core.store, core.engine, addonsProvider, addonUpdater)
    }

    val analytics by lazyMonitored { Analytics(context, performance.visualCompletenessQueue.queue) }
    val publicSuffixList by lazyMonitored { PublicSuffixList(context) }
    val clipboardHandler by lazyMonitored { ClipboardHandler(context) }
    val performance by lazyMonitored { PerformanceComponent() }
    val push by lazyMonitored { Push(context, analytics.crashReporter) }
    val wifiConnectionMonitor by lazyMonitored { WifiConnectionMonitor(context as Application) }
    val strictMode by lazyMonitored { StrictModeManager(org.halalz.fenix.Config, this) }

    val settings by lazyMonitored { Settings(context) }
    val fenixOnboarding by lazyMonitored { FenixOnboarding(context) }

    val reviewPromptController by lazyMonitored {
        ReviewPromptController(
            manager = ReviewManagerFactory.create(context),
            reviewSettings = FenixReviewSettings(settings),
        )
    }

    @delegate:SuppressLint("NewApi")
    val autofillConfiguration by lazyMonitored {
        AutofillConfiguration(
            storage = core.passwordsStorage,
            publicSuffixList = publicSuffixList,
            unlockActivity = AutofillUnlockActivity::class.java,
            confirmActivity = AutofillConfirmActivity::class.java,
            searchActivity = AutofillSearchActivity::class.java,
            applicationName = context.getString(R.string.app_name),
            httpClient = core.client,
        )
    }

    val appStartReasonProvider by lazyMonitored { AppStartReasonProvider() }
    val startupActivityLog by lazyMonitored { StartupActivityLog() }
    val startupStateProvider by lazyMonitored { StartupStateProvider(startupActivityLog, appStartReasonProvider) }

    val appStore by lazyMonitored {
        val blocklistHandler = BlocklistHandler(settings)

        AppStore(
            initialState = AppState(
                collections = core.tabCollectionStorage.cachedTabCollections,
                expandedCollections = emptySet(),
                topSites = core.topSitesStorage.cachedTopSites.sort(),
                recentBookmarks = emptyList(),
                showCollectionPlaceholder = settings.showCollectionsPlaceholderOnHome,
                // Provide an initial state for recent tabs to prevent re-rendering on the home screen.
                //  This will otherwise cause a visual jump as the section gets rendered from no state
                //  to some state.
                recentTabs = if (settings.showRecentTabsFeature) {
                    core.store.state.asRecentTabs()
                } else {
                    emptyList()
                },
                recentHistory = emptyList(),
            ).run { filterState(blocklistHandler) },
            middlewares = listOf(
                BlocklistMiddleware(blocklistHandler),
                PocketUpdatesMiddleware(
                    core.pocketStoriesService,
                    context.pocketStoriesSelectedCategoriesDataStore,
                ),
                MessagingMiddleware(
                    messagingStorage = analytics.messagingStorage,
                ),
                MetricsMiddleware(metrics = analytics.metrics),
            ),
        )
    }

    val fxSuggest by lazyMonitored { FxSuggest(context) }
}

/**
 * Returns the [Components] object from within a [Composable].
 */
val components: Components
    @Composable
    get() = LocalContext.current.components
