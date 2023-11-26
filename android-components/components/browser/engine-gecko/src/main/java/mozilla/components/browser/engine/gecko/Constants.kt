package mozilla.components.browser.engine.gecko

import android.annotation.SuppressLint
import android.content.Context
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.addons.AddonManager

@SuppressLint("StaticFieldLeak")
object Constants {
    var context: Context? = null
    var store: BrowserStore? = null
    var addonManager: AddonManager? = null
}