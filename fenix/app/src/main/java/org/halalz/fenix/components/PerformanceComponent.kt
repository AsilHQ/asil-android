/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.components

import mozilla.components.support.utils.RunWhenReadyQueue
import org.halalz.fenix.perf.ColdStartupDurationTelemetry
import org.halalz.fenix.perf.VisualCompletenessQueue
import org.halalz.fenix.perf.lazyMonitored

/**
 * Component group for all functionality related to performance.
 */
class PerformanceComponent {
    val visualCompletenessQueue by lazyMonitored { VisualCompletenessQueue(RunWhenReadyQueue()) }
    val coldStartupDurationTelemetry by lazyMonitored { ColdStartupDurationTelemetry() }
}
