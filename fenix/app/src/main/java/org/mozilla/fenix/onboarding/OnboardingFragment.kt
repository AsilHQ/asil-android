/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.onboarding

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.action.WebExtensionAction
import mozilla.components.browser.state.state.extension.WebExtensionPromptRequest
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.addons.Addon
import mozilla.components.lib.state.ext.consumeFrom
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import mozilla.components.support.base.log.logger.Logger
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.browsingmode.BrowsingMode
import org.mozilla.fenix.components.StoreProvider
import org.mozilla.fenix.databinding.FragmentHomeBinding
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.hideToolbar
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.ext.runIfFragmentIsAttached
import org.mozilla.fenix.home.HomeMenuView
import org.mozilla.fenix.home.ToolbarView
import org.mozilla.fenix.home.privatebrowsing.controller.DefaultPrivateBrowsingController
import org.mozilla.fenix.home.toolbar.DefaultToolbarController
import org.mozilla.fenix.home.toolbar.SearchSelectorBinding
import org.mozilla.fenix.home.toolbar.SearchSelectorMenuBinding
import org.mozilla.fenix.onboarding.controller.DefaultOnboardingController
import org.mozilla.fenix.onboarding.interactor.DefaultOnboardingInteractor
import org.mozilla.fenix.onboarding.view.OnboardingView
import org.mozilla.fenix.search.toolbar.DefaultSearchSelectorController
import org.mozilla.fenix.search.toolbar.SearchSelectorMenu
import java.lang.ref.WeakReference

/**
 * Displays the first run onboarding screen.
 */
class OnboardingFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val searchSelectorMenu by lazy {
        SearchSelectorMenu(
            context = requireContext(),
            interactor = interactor,
        )
    }

    private val store: BrowserStore
        get() = requireComponents.core.store
    private val browsingModeManager
        get() = (activity as HomeActivity).browsingModeManager

    private var _interactor: DefaultOnboardingInteractor? = null
    private val interactor: DefaultOnboardingInteractor
        get() = _interactor!!

    private var onboardingView: OnboardingView? = null
    private var homeMenuView: HomeMenuView? = null
    private var toolbarView: ToolbarView? = null

    private lateinit var onboardingStore: OnboardingStore
    private lateinit var onboardingAccountObserver: OnboardingAccountObserver

    private val searchSelectorBinding = ViewBoundFeatureWrapper<SearchSelectorBinding>()
    private val searchSelectorMenuBinding = ViewBoundFeatureWrapper<SearchSelectorMenuBinding>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val activity = activity as HomeActivity
        binding.sessionControlRecyclerView.visibility = View.INVISIBLE
        onboardingAccountObserver = OnboardingAccountObserver(
            context = requireContext(),
            dispatchChanges = ::dispatchOnboardingStateChanges,
        )

        onboardingStore = StoreProvider.get(this) {
            OnboardingStore(
                initialState = OnboardingFragmentState(
                    onboardingState = onboardingAccountObserver.getOnboardingState(),
                ),
            )
        }

        _interactor = DefaultOnboardingInteractor(
            controller = DefaultOnboardingController(
                activity = activity,
                navController = findNavController(),
                onboarding = requireComponents.fenixOnboarding,
            ),
            privateBrowsingController = DefaultPrivateBrowsingController(
                activity = activity,
                appStore = requireComponents.appStore,
                navController = findNavController(),
            ),
            searchSelectorController = DefaultSearchSelectorController(
                activity = activity,
                navController = findNavController(),
            ),
            toolbarController = DefaultToolbarController(
                activity = activity,
                store = store,
                navController = findNavController(),
            ),
        )

        toolbarView = ToolbarView(
            binding = binding,
            context = requireContext(),
            interactor = interactor,
        )

        onboardingView = OnboardingView(
            containerView = binding.sessionControlRecyclerView,
            interactor = interactor,
        )

        activity.themeManager.applyStatusBarTheme(activity)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        consumeFrom(onboardingStore) {
            onboardingView?.update(requireComponents.fenixOnboarding.config)
        }

        homeMenuView = HomeMenuView(
            view = view,
            context = view.context,
            lifecycleOwner = viewLifecycleOwner,
            homeActivity = activity as HomeActivity,
            navController = findNavController(),
            menuButton = WeakReference((activity as HomeActivity).getMenuButton()),
            hideOnboardingIfNeeded = { interactor.onFinishOnboarding(focusOnAddressBar = false) },
        ).also { it.build() }

        toolbarView?.build()


        searchSelectorBinding.set(
            feature = SearchSelectorBinding(
                context = view.context,
                browserStore = store,
            ),
            owner = viewLifecycleOwner,
            view = binding.root,
        )

        searchSelectorMenuBinding.set(
            feature = SearchSelectorMenuBinding(
                context = view.context,
                interactor = interactor,
                searchSelectorMenu = searchSelectorMenu,
                browserStore = store,
            ),
            owner = viewLifecycleOwner,
            view = view,
        )
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        handleDefaultAdBlocker(context)
    }

    override fun onResume() {
        super.onResume()

        if (browsingModeManager.mode == BrowsingMode.Private) {
            activity?.window?.setBackgroundDrawableResource(R.drawable.private_home_background_gradient)
        }

        hideToolbar()
    }

    override fun onPause() {
        super.onPause()

        if (browsingModeManager.mode == BrowsingMode.Private) {
            activity?.window?.setBackgroundDrawable(
                ColorDrawable(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.fx_mobile_private_layer_color_1,
                    ),
                ),
            )
        }
    }

    private fun handleDefaultAdBlocker(context: Context) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO){
                try {
                    val addons = requireContext().components.addonManager.getAddons(true)
                    for (addon in addons){
                        if (addon.translatableName.containsValue("uBlock Origin")){
                            installAddon(addon, context)
                            store.flowScoped { flow ->
                                flow.mapNotNull { state ->
                                    state.webExtensionPromptRequest
                                }.distinctUntilChanged().collect { promptRequest ->
                                    when (promptRequest) {
                                        is WebExtensionPromptRequest.Permissions -> {
                                            promptRequest.onConfirm(true)
                                        }else -> {
                                        println("")
                                    }
                                    }
                                }
                            }
                            handlePostInstallationButtonClicked(
                                addon = addon,
                                context = WeakReference(requireActivity().applicationContext),
                                allowInPrivateBrowsing = true,
                            )
                            break
                        }
                    }
                }catch (e: Exception){
                    println("Exception is ${e.localizedMessage}")
                }
            }
        }
    }

    private fun handlePostInstallationButtonClicked(
        context: WeakReference<Context>,
        allowInPrivateBrowsing: Boolean,
        addon: Addon,
    ) {
        if (allowInPrivateBrowsing) {
            context.get()?.components?.addonManager?.setAddonAllowedInPrivateBrowsing(
                addon = addon,
                allowed = true,
                onSuccess = { updatedAddon ->
                    CoroutineScope(Dispatchers.Main).launch{
                        binding.sessionControlRecyclerView.visibility = View.VISIBLE
                    }
                    println("Success -> $updatedAddon")
                },
                onError = {
                    CoroutineScope(Dispatchers.Main).launch{
                        binding.sessionControlRecyclerView.visibility = View.VISIBLE
                    }
                },
            )
        }
        consumePromptRequest()
    }

    private fun consumePromptRequest() {
        store.dispatch(WebExtensionAction.ConsumePromptRequestWebExtensionAction)
    }

    private fun installAddon(addon: Addon, context: Context) {
        lifecycleScope.launch(Dispatchers.Main) {
            context.applicationContext.components.addonManager.installAddon(
                addon,
                onSuccess = {
                    runIfFragmentIsAttached {
                        Logger("Success")
                    }
                },
                onError = { _, _ ->
                    Logger("Error")
                },
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        onboardingView = null
        homeMenuView = null
        toolbarView = null
        _interactor = null
        _binding = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        homeMenuView?.dismissMenu()
    }

    private fun dispatchOnboardingStateChanges(state: OnboardingState) {
        if (state != onboardingStore.state.onboardingState) {
            onboardingStore.dispatch(OnboardingAction.UpdateState(state))
        }
    }
}
