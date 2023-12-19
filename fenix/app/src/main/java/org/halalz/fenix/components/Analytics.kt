/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.components

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import mozilla.components.lib.crash.CrashReporter
import mozilla.components.lib.crash.sentry.SentryService
import mozilla.components.lib.crash.service.CrashReporterService
import mozilla.components.lib.crash.service.GleanCrashReporterService
import mozilla.components.lib.crash.service.MozillaSocorroService
import mozilla.components.service.nimbus.NimbusApi
import mozilla.components.service.nimbus.messaging.FxNimbusMessaging
import mozilla.components.service.nimbus.messaging.NimbusMessagingStorage
import mozilla.components.service.nimbus.messaging.OnDiskMessageMetadataStorage
import mozilla.components.support.ktx.android.content.isMainProcess
import mozilla.components.support.utils.BrowsersCache
import mozilla.components.support.utils.RunWhenReadyQueue
import org.halalz.fenix.HomeActivity
import org.halalz.fenix.R
import org.halalz.fenix.components.metrics.AdjustMetricsService
import org.halalz.fenix.components.metrics.DefaultMetricsStorage
import org.halalz.fenix.components.metrics.GleanMetricsService
import org.halalz.fenix.components.metrics.InstallReferrerMetricsService
import org.halalz.fenix.components.metrics.MetricController
import org.halalz.fenix.components.metrics.MetricsStorage
import org.halalz.fenix.crashes.CrashFactCollector
import org.halalz.fenix.experiments.createNimbus
import org.halalz.fenix.ext.components
import org.halalz.fenix.ext.settings
import org.halalz.fenix.messaging.CustomAttributeProvider
import org.halalz.fenix.perf.lazyMonitored
import org.halalz.fenix.BuildConfig
import org.mozilla.geckoview.BuildConfig.MOZ_APP_BUILDID
import org.mozilla.geckoview.BuildConfig.MOZ_APP_VENDOR
import org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION
import org.mozilla.geckoview.BuildConfig.MOZ_UPDATE_CHANNEL

/**
 * Component group for all functionality related to analytics e.g. crash reporting and telemetry.
 */
class Analytics(
    private val context: Context,
    private val runWhenReadyQueue: RunWhenReadyQueue,
) {
    val crashReporter: CrashReporter by lazyMonitored {
        val services = mutableListOf<CrashReporterService>()
        val distributionId = when (org.halalz.fenix.Config.channel.isMozillaOnline) {
            true -> "MozillaOnline"
            false -> "Mozilla"
        }

        if (isSentryEnabled()) {
            // We treat caught exceptions similar to debug logging.
            // On the release channel volume of these is too high for our Sentry instances, and
            // we get most value out of nightly/beta logging anyway.
            val shouldSendCaughtExceptions = when (org.halalz.fenix.Config.channel) {
                org.halalz.fenix.ReleaseChannel.Release -> false
                else -> true
            }
            val sentryService = SentryService(
                context,
                BuildConfig.SENTRY_TOKEN,
                tags = mapOf(
                    "geckoview" to "$MOZ_APP_VERSION-$MOZ_APP_BUILDID",
                    "fenix.git" to BuildConfig.GIT_HASH,
                ),
                environment = BuildConfig.BUILD_TYPE,
                sendEventForNativeCrashes = false, // Do not send native crashes to Sentry
                sendCaughtExceptions = shouldSendCaughtExceptions,
                sentryProjectUrl = getSentryProjectUrl(),
            )

            // We only want to initialize Sentry on startup on the main process.
            if (context.isMainProcess()) {
                runWhenReadyQueue.runIfReadyOrQueue {
                    sentryService.initIfNeeded()
                }
            }

            services.add(sentryService)
        }

        // The name "Fenix" here matches the product name on Socorro and is unrelated to the actual app name:
        // https://bugzilla.mozilla.org/show_bug.cgi?id=1523284
        val socorroService = MozillaSocorroService(
            context,
            appName = "Fenix",
            version = MOZ_APP_VERSION,
            buildId = MOZ_APP_BUILDID,
            vendor = MOZ_APP_VENDOR,
            releaseChannel = MOZ_UPDATE_CHANNEL,
            distributionId = distributionId,
        )
        services.add(socorroService)

        val intent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val crashReportingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0 // No flags. Default behavior.
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            crashReportingIntentFlags,
        )

        CrashReporter(
            context = context,
            services = services,
            telemetryServices = listOf(GleanCrashReporterService(context)),
            shouldPrompt = CrashReporter.Prompt.ALWAYS,
            promptConfiguration = CrashReporter.PromptConfiguration(
                appName = context.getString(R.string.app_name),
                organizationName = "Mozilla",
            ),
            enabled = true,
            nonFatalCrashIntent = pendingIntent,
            notificationsDelegate = context.components.notificationsDelegate,
        )
    }

    val crashFactCollector: CrashFactCollector by lazyMonitored {
        CrashFactCollector(crashReporter)
    }

    val metricsStorage: MetricsStorage by lazyMonitored {
        DefaultMetricsStorage(
            context = context,
            settings = context.settings(),
            checkDefaultBrowser = { BrowsersCache.all(context).isDefaultBrowser },
        )
    }

    val metrics: MetricController by lazyMonitored {
        MetricController.create(
            listOf(
                GleanMetricsService(context),
                AdjustMetricsService(
                    application = context as Application,
                    storage = metricsStorage,
                    crashReporter = crashReporter,
                ),
                InstallReferrerMetricsService(context),
            ),
            isDataTelemetryEnabled = { context.settings().isTelemetryEnabled },
            isMarketingDataTelemetryEnabled = { context.settings().isMarketingTelemetryEnabled },
            context.settings(),
        )
    }

    val experiments: NimbusApi by lazyMonitored {
        createNimbus(context, BuildConfig.NIMBUS_ENDPOINT)
    }

    val messagingStorage by lazyMonitored {
        NimbusMessagingStorage(
            context = context,
            metadataStorage = OnDiskMessageMetadataStorage(context),
            gleanPlumb = experiments,
            messagingFeature = FxNimbusMessaging.features.messaging,
            attributeProvider = CustomAttributeProvider,
        )
    }
}

private fun isSentryEnabled() = !BuildConfig.SENTRY_TOKEN.isNullOrEmpty()

private fun getSentryProjectUrl(): String? {
    val baseUrl = "https://sentry.io/organizations/mozilla/issues"
    return when (org.halalz.fenix.Config.channel) {
        org.halalz.fenix.ReleaseChannel.Nightly -> "$baseUrl/?project=6295546"
        org.halalz.fenix.ReleaseChannel.Release -> "$baseUrl/?project=6375561"
        org.halalz.fenix.ReleaseChannel.Beta -> "$baseUrl/?project=6295551"
        else -> null
    }
}