/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.toolbar.display

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.menu.BrowserMenuBuilder
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.browser.toolbar.R
import mozilla.components.concept.engine.webextension.WebExtensionRuntime
import mozilla.components.concept.menu.MenuController
import mozilla.components.concept.toolbar.Toolbar
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.AddonManager
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.android.content.isScreenReaderEnabled
import mozilla.components.support.ktx.android.util.dpToPx
import mozilla.components.ui.colors.R.color as photonColors


/**
 * Sub-component of the browser toolbar responsible for displaying the URL and related controls ("display mode").
 *
 * Structure:
 * ```
 *   +-------------+------------+-----------------------+----------+------+
 *   | navigation  | indicators | url       [ page    ] | browser  | menu |
 *   |   actions   |            |           [ actions ] | actions  |      |
 *   +-------------+------------+-----------------------+----------+------+
 *   +------------------------progress------------------------------------+
 * ```
 *
 * Navigation actions (optional):
 *     A dynamic list of clickable icons usually used for navigation on larger devices
 *     (e.g. “back”/”forward” buttons.)
 *
 * Indicators (optional):
 *     Tracking protection indicator icon (e.g. “shield” icon) that may show a doorhanger when clicked.
 *     Separator icon: a vertical line that separate the above and below icons.
 *     Site security indicator icon (e.g. “Lock” icon) that may show a doorhanger when clicked.
 *     Empty indicator: Icon that will be displayed if the URL is empty.
 *
 * URL:
 *     Section that displays the current URL (read-only)
 *
 * Page actions (optional):
 *     A dynamic list of clickable icons inside the URL section (e.g. “reader mode” icon)
 *
 * Browser actions (optional):
 *     A list of dynamic clickable icons on the toolbar (e.g. tabs tray button)
 *
 * Menu (optional):
 *     A button that shows an overflow menu when clicked (constructed using the browser-menu
 *     component)
 *
 * Progress (optional):
 *     A horizontal progress bar showing the loading progress (at the top or bottom of the toolbar).
 */
@Suppress("LargeClass")
class DisplayToolbar internal constructor(
    private val context: Context,
    private val toolbar: BrowserToolbar,
    internal val rootView: View,
    private val menuButton: mozilla.components.browser.menu.view.MenuButton
) : WebExtensionRuntime {
    /**
     * Enum of indicators that can be displayed in the toolbar.
     */
    enum class Indicators {
        SECURITY,
        TRACKING_PROTECTION,
        EMPTY,
        HIGHLIGHT,
    }

    /**
     * Data class holding the customizable colors in "display mode".
     *
     * @property securityIconSecure Color tint for the "secure connection" icon (lock).
     * @property securityIconInsecure Color tint for the "insecure connection" icon (broken lock).
     * @property emptyIcon Color tint for the icon shown when the URL is empty.
     * @property menu Color tint for the menu icon.
     * @property hint Text color of the hint shown when the URL is empty.
     * @property title Text color of the website title.
     * @property text Text color of the URL.
     * @property trackingProtection Color tint for the tracking protection icons.
     * @property separator Color tint for the separator shown between indicators.
     * @property highlight Color tint for the highlight icon.
     *
     * Set/Get the site security icon colours. It uses a pair of color integers which represent the
     * insecure and secure colours respectively.
     */
    data class Colors(
        @ColorInt val securityIconSecure: Int,
        @ColorInt val securityIconInsecure: Int,
        @ColorInt val emptyIcon: Int,
        @ColorInt val menu: Int,
        @ColorInt val hint: Int,
        @ColorInt val title: Int,
        @ColorInt val text: Int,
        @ColorInt val trackingProtection: Int?,
        @ColorInt val separator: Int,
        @ColorInt val highlight: Int?,
    )

    /**
     * Data class holding the customizable icons in "display mode".
     *
     * @property emptyIcon An icon that is shown in front of the URL if the URL is empty.
     * @property trackingProtectionTrackersBlocked An icon that is shown if tracking protection is
     * enabled and trackers have been blocked.
     * @property trackingProtectionNothingBlocked An icon that is shown if tracking protection is
     * enabled and no trackers have been blocked.
     * @property trackingProtectionException An icon that is shown if tracking protection is enabled
     * but the current page is in the "exception list".
     * @property highlight An icon that is shown if any event needs to be brought
     * to the user's attention. Like the autoplay permission been blocked.
     */
    data class Icons(
        val emptyIcon: Drawable?,
        val trackingProtectionTrackersBlocked: Drawable,
        val trackingProtectionNothingBlocked: Drawable,
        val trackingProtectionException: Drawable,
        val highlight: Drawable,
    )

    /**
     * Gravity enum for positioning the progress bar.
     */
    enum class Gravity {
        TOP,
        BOTTOM,
    }

    internal var views = DisplayToolbarViews(
        background = rootView.findViewById(R.id.mozac_browser_toolbar_background),
        separator = rootView.findViewById(R.id.mozac_browser_toolbar_separator),
        emptyIndicator = rootView.findViewById(R.id.mozac_browser_toolbar_empty_indicator),
        menu = MenuButton(menuButton),
        securityIndicator = rootView.findViewById(R.id.mozac_browser_toolbar_security_indicator),
        trackingProtectionIndicator = rootView.findViewById(
            R.id.mozac_browser_toolbar_tracking_protection_indicator,
        ),
        origin = rootView.findViewById<OriginView>(R.id.mozac_browser_toolbar_origin_view).also {
            it.toolbar = toolbar
        },
        progress = rootView.findViewById<ProgressBar>(R.id.mozac_browser_toolbar_progress),
        highlight = rootView.findViewById(R.id.mozac_browser_toolbar_permission_indicator),
    )

    /**
     * Customizable colors in "display mode".
     */
    var colors: Colors = Colors(
        securityIconSecure = ContextCompat.getColor(context, photonColors.photonWhite),
        securityIconInsecure = ContextCompat.getColor(context, photonColors.photonWhite),
        emptyIcon = ContextCompat.getColor(context, photonColors.photonWhite),
        menu = ContextCompat.getColor(context, photonColors.photonWhite),
        hint = views.origin.hintColor,
        title = views.origin.titleColor,
        text = views.origin.textColor,
        trackingProtection = null,
        separator = ContextCompat.getColor(context, photonColors.photonGrey80),
        highlight = null,
    )
        set(value) {
            field = value

            updateSiteSecurityIcon()
            views.emptyIndicator.setColorFilter(value.emptyIcon)
            views.menu.setColorFilter(value.menu)
            views.origin.hintColor = value.hint
            views.origin.titleColor = value.title
            views.origin.textColor = value.text
            views.separator.setColorFilter(value.separator)

            if (value.trackingProtection != null) {
                views.trackingProtectionIndicator.setTint(value.trackingProtection)
                views.trackingProtectionIndicator.setColorFilter(value.trackingProtection)
            }

            if (value.highlight != null) {
                views.highlight.setTint(value.highlight)
            }
        }

    /**
     * Customizable icons in "edit mode".
     */
    var icons: Icons = Icons(
        emptyIcon = null,
        trackingProtectionTrackersBlocked = requireNotNull(
            getDrawable(context, TrackingProtectionIconView.DEFAULT_ICON_ON_TRACKERS_BLOCKED),
        ),
        trackingProtectionNothingBlocked = requireNotNull(
            getDrawable(context, TrackingProtectionIconView.DEFAULT_ICON_ON_NO_TRACKERS_BLOCKED),
        ),
        trackingProtectionException = requireNotNull(
            getDrawable(context, TrackingProtectionIconView.DEFAULT_ICON_OFF_FOR_A_SITE),
        ),
        highlight = requireNotNull(
            getDrawable(context, R.drawable.mozac_dot_notification),
        ),
    )
        set(value) {
            field = value

            views.emptyIndicator.setImageDrawable(value.emptyIcon)

            views.trackingProtectionIndicator.setIcons(
                value.trackingProtectionNothingBlocked,
                value.trackingProtectionTrackersBlocked,
                value.trackingProtectionException,
            )
            views.highlight.setIcon(value.highlight)
        }

    /**
     * Allows customization of URL for display purposes.
     */
    var urlFormatter: ((CharSequence) -> CharSequence)? = null

    /**
     * Sets a listener to be invoked when the site security indicator icon is clicked.
     */
    fun setOnSiteSecurityClickedListener(listener: (() -> Unit)?) {
        if (listener == null) {
            views.securityIndicator.setOnClickListener(null)
            views.securityIndicator.background = null
        } else {
            views.securityIndicator.setOnClickListener {
                listener.invoke()
            }

            val outValue = TypedValue()
            context.theme.resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless,
                outValue,
                true,
            )

            views.securityIndicator.setBackgroundResource(outValue.resourceId)
        }
    }
    var isAddOn = true
    /** Opens the asil shield ad-blocker tracker */
    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("InflateParams")
    fun setAsilIconClickListener(store: BrowserStore, addonManager: AddonManager){
        val popupView = LayoutInflater.from(context).inflate(R.layout.popup_layout, null)
        val popupWindow = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val asilIcon = rootView.findViewById<ImageView>(R.id.asil_shield_image_view)
        val toggle = popupView.findViewById<SwitchCompat>(R.id.asil_shield_toggle_button)
        asilIcon.setOnClickListener {
            val iconRect = Rect()
            asilIcon.getGlobalVisibleRect(iconRect)
            val x = iconRect.left
            val y = iconRect.top
            println("x is -> $x")
            popupWindow.apply {
                animationStyle = 2132017505
                isFocusable = true
            }
            toggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked){
                    isAddOn = true
                    enableAddon(addonManager)
                } else{
                    isAddOn = false
                    disableAddon(addonManager)
                }
                if (isAddOn){
                    popupView.findViewById<TextView>(R.id.asil_shield_state_text_view).text = buildString {
                        this.append("Asil Shields UP")
                    }
                    popupView.findViewById<TextView>(R.id.site_broken_text_view).text = buildString {
                        this.append("If this site appears broken, try Shields down")
                    }
                }else{
                    popupView.findViewById<TextView>(R.id.asil_shield_state_text_view).text = buildString {
                        this.append("Asil Shields DOWN")
                    }
                    popupView.findViewById<TextView>(R.id.site_broken_text_view).text = buildString {
                        this.append("You're browsing this site without Asil Browser's privacy protection.")
                    }
                }
            }
            val toolbarHeight =  context.resources.getDimension(R.dimen.mozac_browser_toolbar_default_toolbar_height)
            asilIcon.post {
                val leftOverDevicePixel = getDeviceWidthInPixels(context) - x
                val popUpLayingOut = 300.dpToPx(context.resources.displayMetrics) - leftOverDevicePixel
                val newPopUpPosition = (x - popUpLayingOut) - 50
                popupWindow.showAtLocation(
                    asilIcon,
                    android.view.Gravity.NO_GRAVITY,
                    newPopUpPosition,
                    (y + toolbarHeight).toInt() - 25
                )
                val pointerArrow =
                    popupView.findViewById<ImageView>(R.id.pointer_arrow_asil_shield_image_view)
                val pointerArrowParams =
                    pointerArrow.layoutParams as ConstraintLayout.LayoutParams
                pointerArrowParams.rightMargin = leftOverDevicePixel - 115
                pointerArrow.layoutParams = pointerArrowParams
            }

            if (store.state.selectedTab?.content?.icon != null){
                val originalBitmap: Bitmap = store.state.selectedTab?.content?.icon!!
                val bitmapWithBackgroundRemoved = removeWhiteBackground(originalBitmap)
                popupView.findViewById<AppCompatImageView>(R.id.site_image_view).setImageBitmap(bitmapWithBackgroundRemoved)
            }

            popupView.findViewById<TextView>(R.id.website_url_text_view).text = store.state.selectedTab?.content?.url
            val extensions = store.state.extensions.values.toList()
            extensions.forEach { extension ->
                store.flowScoped { flow ->
                    flow.distinctUntilChangedBy { it.selectedTab }
                        .collect { state ->
                            val badgeText = state.selectedTab?.extensionState?.get(extension.id)?.browserAction?.badgeText
                            if (!badgeText.isNullOrEmpty()) popupView.findViewById<TextView>(R.id.count_text).text = badgeText
                        }
                }
            }
        }
    }

    private fun removeWhiteBackground(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixelColor = bitmap.getPixel(x, y)

                // Check if the pixel color is white or nearly white (you can adjust the threshold)
                if (isWhite(pixelColor)) {
                    resultBitmap.setPixel(x, y, Color.TRANSPARENT)
                } else {
                    resultBitmap.setPixel(x, y, pixelColor)
                }
            }
        }

        return resultBitmap
    }

    /** Opens the safe gaze tracker */
    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("InflateParams")
    fun setSafeGazeClickListener(store: BrowserStore, addonManager: AddonManager){
        val popupView = LayoutInflater.from(context).inflate(R.layout.popup_safe_gaze_layout, null)
        val popupWindow = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val safeGazeIcon = rootView.findViewById<ImageView>(R.id.asil_safe_gaze_image_view)
        val toggle = popupView.findViewById<SwitchCompat>(R.id.asil_shield_toggle_button)
        val sharedPref = context.getSharedPreferences("safe_gaze_active", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        if (sharedPref.getBoolean("safe_gaze_active", true)){
            popupView.findViewById<TextView>(R.id.asil_shield_state_text_view).text = buildString {
                this.append("Safe Gaze UP")
            }
            toggle.isChecked = true
        }else{
            popupView.findViewById<TextView>(R.id.asil_shield_state_text_view).text = buildString {
                this.append("Safe Gaze DOWN")
            }
            toggle.isChecked = false
        }
        safeGazeIcon.setOnClickListener {
            val iconRect = Rect()
            safeGazeIcon.getGlobalVisibleRect(iconRect)
            val x = iconRect.left
            val y = iconRect.top
            popupWindow.apply {
                animationStyle = 2132017505
                isFocusable = true
            }
            toggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked){
                    enableSafeGaze(store, addonManager)
                    editor.putBoolean("safe_gaze_active", true)
                    popupView.findViewById<TextView>(R.id.asil_shield_state_text_view).text = buildString {
                        this.append("Safe Gaze UP")
                    }
                } else{
                    disableSafeGaze(store, addonManager)
                    editor.putBoolean("safe_gaze_active", false)
                    popupView.findViewById<TextView>(R.id.asil_shield_state_text_view).text = buildString {
                        this.append("Safe Gaze DOWN")
                    }
                }
                editor.apply()
            }
            val toolbarHeight =  context.resources.getDimension(R.dimen.mozac_browser_toolbar_default_toolbar_height)
            safeGazeIcon.post {
                val leftOverDevicePixel = getDeviceWidthInPixels(context) - x
                val popUpLayingOut = 275.dpToPx(context.resources.displayMetrics) - leftOverDevicePixel
                val newPopUpPosition = (x - popUpLayingOut) - 50
                popupWindow.showAtLocation(
                    safeGazeIcon,
                    android.view.Gravity.NO_GRAVITY,
                    newPopUpPosition,
                    (y + toolbarHeight).toInt() - 25
                )
                val pointerArrow =
                    popupView.findViewById<ImageView>(R.id.pointer_arrow_safe_gaze_image_view)
                val pointerArrowParams =
                    pointerArrow.layoutParams as ConstraintLayout.LayoutParams
                pointerArrowParams.rightMargin = leftOverDevicePixel - 113
                pointerArrow.layoutParams = pointerArrowParams
            }

            if (store.state.selectedTab?.content?.icon != null){
                val originalBitmap: Bitmap = store.state.selectedTab?.content?.icon!!
                val bitmapWithBackgroundRemoved = removeWhiteBackground(originalBitmap)
                popupView.findViewById<AppCompatImageView>(R.id.site_image_view).setImageBitmap(bitmapWithBackgroundRemoved);
            }

            popupView.findViewById<TextView>(R.id.website_url_text_view).text = store.state.selectedTab?.content?.url
            val sharedPreferences = context.getSharedPreferences("safe_gaze_preferences", Context.MODE_PRIVATE)
            val totalCensoredText = "Total ${sharedPreferences.getInt("all_time_cencored_count", 0)} Sinful acts avoided since beginning"
            popupView.findViewById<TextView>(R.id.count_text).text = sharedPreferences.getInt("session_cencored_count", 0).toString()
            popupView.findViewById<TextView>(R.id.asil_shield_exp_text).text = buildString {
                this.append("Sinful acts avoided")
            }
            popupView.findViewById<TextView>(R.id.site_broken_text_view).text = totalCensoredText
        }
    }

    @Suppress("DEPRECATION")
    private fun getDeviceWidthInPixels(context: Context): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()

        windowManager.defaultDisplay.getMetrics(displayMetrics)

        return displayMetrics.widthPixels
    }

    private fun isWhite(color: Int): Boolean {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        return red > 200 && green > 200 && blue > 200
    }

    private fun enableAddon(addonManager: AddonManager){
        var uBlockOrigin: Addon? = null
        val job = CoroutineScope(Dispatchers.IO).launch {
            for (addon in addonManager.getAddons()){
                if (addon.translatableName.containsValue("uBlock Origin")){
                    uBlockOrigin = addon
                    break
                }
            }
        }
        runBlocking {
            job.join()
            uBlockOrigin?.let { addOn ->
                if (!addOn.isEnabled()){
                    addonManager.enableAddon(
                        addon = addOn,
                        onSuccess = {
                            Logger("Addon disable success")
                            isAddOn = true
                        },
                        onError = {
                            println("Addon disable error")
                        },
                    )
                }else{
                    return@runBlocking
                }
            }
        }
    }

    private fun disableAddon(addonManager: AddonManager){
        var uBlockOrigin: Addon? = null
        val job = CoroutineScope(Dispatchers.IO).launch {
            for (addon in addonManager.getAddons()){
                if (addon.translatableName.containsValue("uBlock Origin")){
                    uBlockOrigin = addon
                    break
                }
            }
        }
        runBlocking {
            job.join()
            uBlockOrigin?.let { addOn ->
                if (addOn.isEnabled()){
                    addonManager.disableAddon(
                        addon = addOn,
                        onSuccess = {
                            isAddOn = false
                        },
                        onError = {
                            Logger("Addon disable error")
                        },
                    )
                }else{
                    return@runBlocking
                }
            }
        }
    }

    private fun enableSafeGaze(store: BrowserStore, addonManager: AddonManager) {
        val safeGazeExtension = store.state.extensionInstances["safegaze@mozac.org"]
        safeGazeExtension?.let {
            addonManager.getRuntime().enableWebExtension(
                extension = it,
                onSuccess = { result ->
                    store.state.extensionInstances["safegaze@mozac.org"] = result
                    val sharedPref =
                        context.getSharedPreferences("safe_gaze_preferences", Context.MODE_PRIVATE)
                    val editor = sharedPref.edit()
                    editor.putInt("session_cencored_count", 0)
                    editor.apply()
                    println("extension enabled")
                },
                onError = { throwable ->
                    println("extension enabled fail: $throwable")
                },
            )
        }
    }

    private fun disableSafeGaze(store: BrowserStore, addonManager: AddonManager) {
        val safeGazeExtension = store.state.extensionInstances["safegaze@mozac.org"]
        safeGazeExtension?.let {
            addonManager.getRuntime().disableWebExtension(
                extension = it,
                onSuccess = { result ->
                    store.state.extensionInstances["safegaze@mozac.org"] = result
                    println("extension disabled")
                },
                onError = { throwable ->
                    println("extension disable fail: $throwable")
                },
            )
        }
    }

    /**
     * Sets a listener to be invoked when the site tracking protection indicator icon is clicked.
     */
    fun setOnTrackingProtectionClickedListener(listener: (() -> Unit)?) {
        if (listener == null) {
            views.trackingProtectionIndicator.setOnClickListener(null)
            views.trackingProtectionIndicator.background = null
        } else {
            views.trackingProtectionIndicator.setOnClickListener {
                listener.invoke()
            }

            val outValue = TypedValue()
            context.theme.resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless,
                outValue,
                true,
            )

            views.trackingProtectionIndicator.setBackgroundResource(outValue.resourceId)
        }
    }

    /**
     *  Sets a lambda to be invoked when the menu is dismissed
     */
    fun setMenuDismissAction(onDismiss: () -> Unit) {
        views.menu.setMenuDismissAction(onDismiss)
    }

    /**
     * List of indicators that should be displayed next to the URL.
     */
    var indicators: List<Indicators> = listOf(Indicators.SECURITY)
        set(value) {
            field = value

            updateIndicatorVisibility()
        }

    var displayIndicatorSeparator: Boolean = true
        set(value) {
            field = value
            updateIndicatorVisibility()
        }

    /**
     * Sets the background that should be drawn behind the URL, page actions an indicators.
     */
    fun setUrlBackground(background: Drawable?) {
        views.background.setImageDrawable(background)
    }

    /**
     * Whether the progress bar should be drawn at the top or bottom of the toolbar.
     */
    var progressGravity: Gravity = Gravity.BOTTOM
        set(value) {
            field = value

            val layout = rootView as ConstraintLayout
            layout.hashCode()

            val constraintSet = ConstraintSet()
            constraintSet.clone(layout)
            constraintSet.clear(views.progress.id, ConstraintSet.TOP)
            constraintSet.clear(views.progress.id, ConstraintSet.BOTTOM)
            constraintSet.connect(
                views.progress.id,
                if (value == Gravity.TOP) ConstraintSet.TOP else ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                if (value == Gravity.TOP) ConstraintSet.TOP else ConstraintSet.BOTTOM,
            )
            constraintSet.applyTo(layout)
        }

    /**
     * Sets a lambda that will be invoked whenever the URL in display mode was clicked. Only if this
     * lambda returns <code>true</code> the toolbar will switch to editing mode. Return
     * <code>false</code> to not switch to editing mode and handle the click manually.
     */
    var onUrlClicked: () -> Boolean
        get() = views.origin.onUrlClicked
        set(value) {
            views.origin.onUrlClicked = value
        }

    /**
     * Sets the text to be displayed when the URL of the toolbar is empty.
     */
    var hint: String
        get() = views.origin.hint
        set(value) {
            views.origin.hint = value
        }

    /**
     * Sets the size of the text for the title displayed in the toolbar.
     */
    var titleTextSize: Float
        get() = views.origin.titleTextSize
        set(value) {
            views.origin.titleTextSize = value
        }

    /**
     * Sets the size of the text for the URL/search term displayed in the toolbar.
     */
    var textSize: Float
        get() = views.origin.textSize
        set(value) {
            views.origin.textSize = value
        }

    /**
     * Sets the typeface of the text for the URL/search term displayed in the toolbar.
     */
    var typeface: Typeface
        get() = views.origin.typeface
        set(value) {
            views.origin.typeface = value
        }

    /**
     * Sets a [BrowserMenuBuilder] that will be used to create a menu when the menu button is clicked.
     * The menu button will only be visible if a builder or controller has been set.
     */
    var menuBuilder: BrowserMenuBuilder?
        get() = views.menu.menuBuilder
        set(value) {
            views.menu.menuBuilder = value
        }

    /**
     * Sets a [MenuController] that will be used to create a menu when the menu button is clicked.
     * The menu button will only be visible if a builder or controller has been set.
     * If both a [menuBuilder] and controller are present, only the controller will be used.
     */
    var menuController: MenuController?
        get() = views.menu.menuController
        set(value) {
            views.menu.menuController = value
        }

    /**
     * Set a LongClickListener to the urlView of the toolbar.
     */
    fun setOnUrlLongClickListener(handler: ((View) -> Boolean)?) = views.origin.setOnUrlLongClickListener(handler)

    private fun updateIndicatorVisibility() {
        val urlEmpty = url.isEmpty()

        views.securityIndicator.visibility = if (!urlEmpty && indicators.contains(Indicators.SECURITY)) {
            View.VISIBLE
        } else {
            View.GONE
        }

        views.trackingProtectionIndicator.visibility = if (
            !urlEmpty && indicators.contains(Indicators.TRACKING_PROTECTION)
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }

        views.emptyIndicator.visibility = if (urlEmpty && indicators.contains(Indicators.EMPTY)) {
            View.VISIBLE
        } else {
            View.GONE
        }

        views.highlight.visibility = if (!urlEmpty && indicators.contains(Indicators.HIGHLIGHT)) {
            setHighlight(toolbar.highlight)
        } else {
            View.GONE
        }

        updateSeparatorVisibility()
    }

    private fun updateSeparatorVisibility() {
        views.separator.visibility = if (
            displayIndicatorSeparator &&
            views.trackingProtectionIndicator.isVisible &&
            views.securityIndicator.isVisible
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }

        // In Fenix (which is using a beta release of ConstraintLayout) we are seeing issues after
        // early visibility changes. Children of the ConstraintLayout are not visible and have a
        // size of 0x0 (even though they have a fixed size in the layout XML). Explicitly requesting
        // to layout the ConstraintLayout fixes that issue. This may be a bug in the beta of
        // ConstraintLayout and in the future we may be able to just remove this call.
        rootView.requestLayout()
    }

    /**
     * Updates the title to be displayed.
     */
    internal var title: String
        get() = views.origin.title
        set(value) {
            views.origin.title = value
        }

    /**
     * Updates the URL to be displayed.
     */
    internal var url: CharSequence = ""
        set(value) {
            field = value
            views.origin.url = urlFormatter?.invoke(value) ?: value
            updateIndicatorVisibility()
        }

    /**
     * Sets the site's security icon as secure if true, else the regular globe.
     */
    internal var siteSecurity: Toolbar.SiteSecurity = Toolbar.SiteSecurity.INSECURE
        set(value) {
            field = value
            updateSiteSecurityIcon()
        }

    private fun updateSiteSecurityIcon() {
        @ColorInt val color = when (siteSecurity) {
            Toolbar.SiteSecurity.INSECURE -> colors.securityIconInsecure
            Toolbar.SiteSecurity.SECURE -> colors.securityIconSecure
        }
        if (color == Color.TRANSPARENT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            views.securityIndicator.clearColorFilter()
        } else {
            views.securityIndicator.setColorFilter(color)
        }

        views.securityIndicator.siteSecurity = siteSecurity
    }

    internal fun setTrackingProtectionState(state: Toolbar.SiteTrackingProtection) {
        views.trackingProtectionIndicator.siteTrackingProtection = state
        updateSeparatorVisibility()
    }

    internal fun setHighlight(state: Toolbar.Highlight): Int {
        if (!indicators.contains(Indicators.HIGHLIGHT)) {
            return views.highlight.visibility
        }

        views.highlight.state = state

        return views.highlight.visibility
    }

    internal fun onStop() {
        views.menu.dismissMenu()
    }

    /**
     * Updates the progress to be displayed.
     *
     * Accessibility note:
     *     ProgressBars can be made accessible to TalkBack by setting `android:accessibilityLiveRegion`.
     *     They will emit TYPE_VIEW_SELECTED events. TalkBack will format those events into percentage
     *     announcements along with a pitch-change earcon. We are not using that feature here for
     *     several reasons:
     *     1. They are dispatched via a 200ms timeout. Since loading a page can be a short process,
     *        and since we only update the bar a handful of times, these events often never fire and
     *        they don't give the user a true sense of the progress.
     *     2. The last 100% event is dispatched after the view is hidden. This prevents the event
     *        from being fired, so the user never gets a "complete" event.
     *     3. Live regions in TalkBack have their role announced, so the user will hear
     *        "Progress bar, 25%". For a common feature like page load this is very chatty and unintuitive.
     *     4. We can provide custom strings instead of the less useful percentage utterance, but
     *        TalkBack will not play an earcon if an event has its own text.
     *
     *     For all those reasons, we are going another route here with a "loading" announcement
     *     when the progress bar first appears along with scroll events that have the same
     *     pitch-change earcon in TalkBack (although they are a bit louder). This gives a concise and
     *     consistent feedback to the user that they can depend on.
     *
     */
    internal fun updateProgress(progress: Int) {
        if (!views.progress.isVisible && progress > 0) {
            // Loading has just started, make visible.
            views.progress.visibility = View.VISIBLE

            // Announce "loading" for accessibility if it has not been completed
            if (progress < views.progress.max) {
                views.progress.announceForAccessibility(
                    context.getString(R.string.mozac_browser_toolbar_progress_loading),
                )
            }
        }

        views.progress.progress = progress
        val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED)
        } else {
            @Suppress("DEPRECATION")
            AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED)
        }.apply {
            scrollY = progress
            maxScrollY = views.progress.max
        }

        if (context.isScreenReaderEnabled) {
            views.progress.parent.requestSendAccessibilityEvent(views.progress, event)
        }

        if (progress >= views.progress.max) {
            // Loading is done, hide progress bar.
            views.progress.visibility = View.GONE
        }
    }

    /**
     * Declare that the actions (navigation actions, browser actions, page actions) have changed and
     * should be updated if needed.
     */
    internal fun invalidateActions() {
        views.menu.invalidateMenu()
    }

    /**
     * Adds an action to be displayed on the right side of the toolbar (outside of the URL bounding
     * box) in display mode.
     *
     * If there is not enough room to show all icons then some icons may be moved to an overflow
     * menu.
     *
     * Related:
     * https://developer.mozilla.org/en-US/Add-ons/WebExtensions/user_interface/Browser_action
     */

    /**
     * Removes a previously added browser action (see [addBrowserAction]). If the provided
     * action was never added, this method has no effect.
     *
     * @param action the action to remove.
     */

    /**
     * Removes a previously added page action (see [addBrowserAction]). If the provided
     * action was never added, this method has no effect.
     *
     * @param action the action to remove.
     */

    /**
     * Adds an action to be displayed on the right side of the URL in display mode.
     *
     * Related:
     * https://developer.mozilla.org/en-US/Add-ons/WebExtensions/user_interface/Page_actions
     */

    /**
     * Adds an action to be display on the far left side of the toolbar. This area is usually used
     * on larger devices for navigation actions like "back" and "forward".
     */
}

/**
 * Internal holder for view references.
 */
@Suppress("LongParameterList")
internal class DisplayToolbarViews(
    val background: ImageView,
    val separator: ImageView,
    val emptyIndicator: ImageView,
    val menu: MenuButton,
    val securityIndicator: SiteSecurityIconView,
    val trackingProtectionIndicator: TrackingProtectionIconView,
    val origin: OriginView,
    val progress: ProgressBar,
    val highlight: HighlightView,
)
