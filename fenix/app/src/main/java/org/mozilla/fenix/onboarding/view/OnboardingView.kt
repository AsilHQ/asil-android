/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.onboarding.view

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.mozilla.fenix.nimbus.OnboardingPanel
import org.mozilla.fenix.onboarding.OnboardingState
import org.mozilla.fenix.onboarding.interactor.OnboardingInteractor
import org.mozilla.fenix.nimbus.Onboarding as OnboardingConfig

/**
 * Shows a list of onboarding cards.
 */
class OnboardingView(
    containerView: RecyclerView,
    interactor: OnboardingInteractor,
) {

    private val onboardingAdapter = OnboardingAdapter(interactor)

    init {
        containerView.apply {
            adapter = onboardingAdapter
            layoutManager = LinearLayoutManager(containerView.context)
        }
    }

    /**
     * Updates the display of the onboarding cards based on the given [OnboardingState].
     *
     * @param onboardingState The new user onboarding page state.
     * @param onboardingConfig The new user onboarding page configuration.
     */
    fun update(
        onboardingConfig: OnboardingConfig,
    ) {
        onboardingAdapter.submitList(onboardingAdapterItems(onboardingConfig))
    }

    private fun onboardingAdapterItems(
        onboardingConfig: OnboardingConfig,
    ): List<OnboardingAdapterItem> {
        val items: MutableList<OnboardingAdapterItem> =
            mutableListOf(OnboardingAdapterItem.OnboardingHeader)

        onboardingConfig.order.forEach {
            when (it) {
                OnboardingPanel.THEMES -> items.add(OnboardingAdapterItem.OnboardingThemePicker)
                // Customize FxA items based on where we are with the account state:
                OnboardingPanel.TCP -> items.add(OnboardingAdapterItem.OnboardingTrackingProtection)
                else -> {}
            }
        }

        items.addAll(
            listOf(
                OnboardingAdapterItem.OnboardingFinish,
                OnboardingAdapterItem.BottomSpacer,
            ),
        )

        return items
    }
}
