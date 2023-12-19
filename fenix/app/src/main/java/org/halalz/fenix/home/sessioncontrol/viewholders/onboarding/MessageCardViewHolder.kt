/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.home.sessioncontrol.viewholders.onboarding

import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import mozilla.components.lib.state.ext.observeAsComposableState
import mozilla.components.service.nimbus.messaging.Message
import org.halalz.fenix.R
import org.halalz.fenix.components.components
import org.halalz.fenix.compose.ComposeViewHolder
import org.halalz.fenix.compose.MessageCard
import org.halalz.fenix.compose.MessageCardColors
import org.halalz.fenix.home.sessioncontrol.SessionControlInteractor
import org.halalz.fenix.theme.FirefoxTheme
import org.halalz.fenix.wallpapers.Wallpaper
import org.halalz.fenix.wallpapers.WallpaperState

/**
 * View holder for the Nimbus Message Card.
 *
 * @param composeView [ComposeView] which will be populated with Jetpack Compose UI content.
 * @param viewLifecycleOwner [LifecycleOwner] to which this Composable will be tied to.
 * @param interactor [SessionControlInteractor] which will have delegated to all user
 * interactions.
 */
class MessageCardViewHolder(
    composeView: ComposeView,
    viewLifecycleOwner: LifecycleOwner,
    private val interactor: SessionControlInteractor,
) : ComposeViewHolder(composeView, viewLifecycleOwner) {
    private lateinit var messageGlobal: Message

    companion object {
        internal val LAYOUT_ID = View.generateViewId()
    }

    init {
        val horizontalPadding =
            composeView.resources.getDimensionPixelSize(R.dimen.home_item_horizontal_margin)
        composeView.setPadding(horizontalPadding, 0, horizontalPadding, 0)
    }

    fun bind(message: Message) {
        messageGlobal = message
    }

    @Composable
    override fun Content() {
        val message by remember { mutableStateOf(messageGlobal) }
        val wallpaperState = components.appStore
            .observeAsComposableState { state -> state.wallpaperState }.value ?: WallpaperState.default
        val isWallpaperNotDefault = !Wallpaper.nameIsDefault(wallpaperState.currentWallpaper.name)

        var (_, _, _, _, buttonColor, buttonTextColor) = MessageCardColors.buildMessageCardColors()

        if (isWallpaperNotDefault) {
            buttonColor = FirefoxTheme.colors.layer1

            if (!isSystemInDarkTheme()) {
                buttonTextColor = FirefoxTheme.colors.textActionSecondary
            }
        }

        val messageCardColors = MessageCardColors.buildMessageCardColors(
            backgroundColor = wallpaperState.wallpaperCardColor,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor,
        )

        MessageCard(
            messageText = message.data.text,
            titleText = message.data.title,
            buttonText = message.data.buttonLabel,
            messageColors = messageCardColors,
            onClick = { interactor.onMessageClicked(message) },
            onCloseButtonClick = { interactor.onMessageClosedClicked(message) },
        )
    }
}