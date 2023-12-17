/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix

enum class ReleaseChannel {
    Debug,
    Nightly,
    Beta,
    Release,
    ;

    val isReleased: Boolean
        get() = when (this) {
            Debug -> false
            else -> true
        }

    /**
     * True if this is a debug release channel, false otherwise.
     *
     * This constant should often be used instead of [BuildConfig.DEBUG], which indicates
     * if the `debuggable` flag is set which can be true even on released channel builds
     * (e.g. performance).
     */
    val isDebug: Boolean
        get() = !this.isReleased

    val isReleaseOrBeta: Boolean
        get() = this == org.halalz.fenix.ReleaseChannel.Release || this == org.halalz.fenix.ReleaseChannel.Beta

    val isRelease: Boolean
        get() = when (this) {
            org.halalz.fenix.ReleaseChannel.Release -> true
            else -> false
        }

    val isBeta: Boolean
        get() = this == org.halalz.fenix.ReleaseChannel.Beta

    val isNightlyOrDebug: Boolean
        get() = this == org.halalz.fenix.ReleaseChannel.Debug || this == org.halalz.fenix.ReleaseChannel.Nightly

    /**
     * Is this a "Mozilla Online" build of Fenix? "Mozilla Online" is the Chinese branch of Mozilla
     * and this flag will be `true` for builds shipping to Chinese app stores.
     */
    val isMozillaOnline: Boolean
        get() = BuildConfig.MOZILLA_ONLINE
}

object Config {
    val channel = when (BuildConfig.BUILD_TYPE) {
        "debug" -> org.halalz.fenix.ReleaseChannel.Debug
        "nightly", "benchmark" -> org.halalz.fenix.ReleaseChannel.Nightly
        "beta" -> org.halalz.fenix.ReleaseChannel.Beta
        "release" -> org.halalz.fenix.ReleaseChannel.Release
        else -> {
            throw IllegalStateException("Unknown build type: ${BuildConfig.BUILD_TYPE}")
        }
    }
}
