/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.ext

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.halalz.fenix.Config
import org.halalz.fenix.ReleaseChannel

class LogTest {

    private val mockThrowable: Throwable = mockk()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        mockkObject(org.halalz.fenix.Config)

        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.d(any(), any(), any()) } returns 0
        every { Log.d(any(), any(), any()) } returns 0
    }

    @Test
    fun `Test log debug function`() {
        every { org.halalz.fenix.Config.channel } returns org.halalz.fenix.ReleaseChannel.Debug
        logDebug("hi", "hi")
        verify { Log.d("hi", "hi") }
    }

    @Test
    fun `Test log warn function with tag and message args`() {
        every { org.halalz.fenix.Config.channel } returns org.halalz.fenix.ReleaseChannel.Debug
        logWarn("hi", "hi")
        verify { Log.w("hi", "hi") }
    }

    @Test
    fun `Test log warn function with tag, message, and exception args`() {
        every { org.halalz.fenix.Config.channel } returns org.halalz.fenix.ReleaseChannel.Debug
        logWarn("hi", "hi", mockThrowable)
        verify { Log.w("hi", "hi", mockThrowable) }
    }

    @Test
    fun `Test log error function with tag, message, and exception args`() {
        every { org.halalz.fenix.Config.channel } returns org.halalz.fenix.ReleaseChannel.Debug
        logErr("hi", "hi", mockThrowable)
        verify { Log.e("hi", "hi", mockThrowable) }
    }

    @Test
    fun `Test no log in production channel`() {
        every { org.halalz.fenix.Config.channel } returns org.halalz.fenix.ReleaseChannel.Release

        logDebug("hi", "hi")
        logWarn("hi", "hi")
        logWarn("hi", "hi", mockThrowable)
        logErr("hi", "hi", mockThrowable)

        verify(exactly = 0) { Log.d(any(), any()) }
        verify(exactly = 0) { Log.w(any(), any<String>()) }
        verify(exactly = 0) { Log.d(any(), any(), any()) }
        verify(exactly = 0) { Log.d(any(), any(), any()) }
    }
}