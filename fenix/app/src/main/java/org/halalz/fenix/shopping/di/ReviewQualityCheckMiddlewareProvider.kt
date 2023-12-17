/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.shopping.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.tabs.TabsUseCases
import org.halalz.fenix.components.AppStore
import org.halalz.fenix.shopping.DefaultShoppingExperienceFeature
import org.halalz.fenix.shopping.middleware.DefaultNetworkChecker
import org.halalz.fenix.shopping.middleware.DefaultReviewQualityCheckPreferences
import org.halalz.fenix.shopping.middleware.DefaultReviewQualityCheckService
import org.halalz.fenix.shopping.middleware.DefaultReviewQualityCheckVendorsService
import org.halalz.fenix.shopping.middleware.GetReviewQualityCheckSumoUrl
import org.halalz.fenix.shopping.middleware.ReviewQualityCheckNavigationMiddleware
import org.halalz.fenix.shopping.middleware.ReviewQualityCheckNetworkMiddleware
import org.halalz.fenix.shopping.middleware.ReviewQualityCheckPreferencesMiddleware
import org.halalz.fenix.shopping.middleware.ReviewQualityCheckTelemetryMiddleware
import org.halalz.fenix.shopping.store.ReviewQualityCheckMiddleware
import org.halalz.fenix.utils.Settings

/**
 * Provides middleware for review quality check store.
 */
object ReviewQualityCheckMiddlewareProvider {

    /**
     * Provides middlewares for review quality check feature.
     *
     * @param settings The [Settings] instance to use.
     * @param browserStore The [BrowserStore] instance to access state.
     * @param appStore The [AppStore] instance to access state.
     * @param context The [Context] instance to use.
     * @param scope The [CoroutineScope] to use for launching coroutines.
     */
    fun provideMiddleware(
        settings: Settings,
        browserStore: BrowserStore,
        appStore: AppStore,
        context: Context,
        scope: CoroutineScope,
    ): List<ReviewQualityCheckMiddleware> =
        listOf(
            providePreferencesMiddleware(settings, browserStore, appStore, scope),
            provideNetworkMiddleware(browserStore, context, scope),
            provideNavigationMiddleware(TabsUseCases.SelectOrAddUseCase(browserStore), context),
            provideTelemetryMiddleware(),
        )

    private fun providePreferencesMiddleware(
        settings: Settings,
        browserStore: BrowserStore,
        appStore: AppStore,
        scope: CoroutineScope,
    ) = ReviewQualityCheckPreferencesMiddleware(
        reviewQualityCheckPreferences = DefaultReviewQualityCheckPreferences(settings),
        reviewQualityCheckVendorsService = DefaultReviewQualityCheckVendorsService(browserStore),
        appStore = appStore,
        shoppingExperienceFeature = DefaultShoppingExperienceFeature(),
        scope = scope,
    )

    private fun provideNetworkMiddleware(
        browserStore: BrowserStore,
        context: Context,
        scope: CoroutineScope,
    ) = ReviewQualityCheckNetworkMiddleware(
        reviewQualityCheckService = DefaultReviewQualityCheckService(browserStore),
        networkChecker = DefaultNetworkChecker(context),
        scope = scope,
    )

    private fun provideNavigationMiddleware(
        selectOrAddUseCase: TabsUseCases.SelectOrAddUseCase,
        context: Context,
    ) = ReviewQualityCheckNavigationMiddleware(
        selectOrAddUseCase = selectOrAddUseCase,
        GetReviewQualityCheckSumoUrl(context),
    )

    private fun provideTelemetryMiddleware() =
        ReviewQualityCheckTelemetryMiddleware()
}
