/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.icons.extension

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.MessageHandler

/**
 * [MessageHandler] implementation that receives messages from the icons web extensions and performs icon loads.
 */
internal class SafeGazeMessageHandler(
    private val context: Context
) : MessageHandler {
    private val scope = CoroutineScope(Dispatchers.IO)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) // This only exists so that we can wait in tests.
    internal var lastJob: Job? = null

    override fun onMessage(message: Any, source: EngineSession?): Any {
        val sharedPref = context.getSharedPreferences("safe_gaze_preferences", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

        if (message.toString().contains("page_refresh")) {
            editor.putInt("session_cencored_count", 0)
            editor.apply()
        } else {
            val totalCount = sharedPref.getInt("all_time_cencored_count",-1)
            val sessionCount = sharedPref.getInt("session_cencored_count",-1)
            editor.putInt("all_time_cencored_count", totalCount + 1)
            editor.putInt("session_cencored_count", sessionCount + 1)
            editor.apply()
        }

        return ""
    }
}
