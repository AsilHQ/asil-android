package mozilla.components.browser.toolbar.singleton

import android.annotation.SuppressLint
import mozilla.components.browser.menu.view.MenuButton

object MenuButtonHolder {
    @SuppressLint("StaticFieldLeak")
    var menuButton: MenuButton? = null
}