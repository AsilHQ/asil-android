/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.settings

import android.os.Bundle
import androidx.core.content.edit
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import org.halalz.fenix.Config
import org.halalz.fenix.FeatureFlags
import org.halalz.fenix.R
import org.halalz.fenix.ext.components
import org.halalz.fenix.ext.nav
import org.halalz.fenix.ext.settings
import org.halalz.fenix.ext.showToolbar
import org.halalz.fenix.BuildConfig

class SecretSettingsFragment : PreferenceFragmentCompat() {

    override fun onResume() {
        super.onResume()
        showToolbar(getString(R.string.preferences_debug_settings))
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.secret_settings_preferences, rootKey)

        requirePreference<SwitchPreference>(R.string.pref_key_allow_third_party_root_certs).apply {
            isVisible = true
            isChecked = context.settings().allowThirdPartyRootCerts
            onPreferenceChangeListener = object : SharedPreferenceUpdater() {
                override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
                    context.components.core.engine.settings.enterpriseRootsEnabled =
                        newValue as Boolean
                    return super.onPreferenceChange(preference, newValue)
                }
            }
        }

        requirePreference<SwitchPreference>(R.string.pref_key_nimbus_use_preview).apply {
            isVisible = true
            isChecked = context.settings().nimbusUsePreview
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }

        requirePreference<SwitchPreference>(R.string.pref_key_toolbar_use_redesign_incomplete).apply {
            isVisible = org.halalz.fenix.Config.channel.isDebug
            isChecked = context.settings().enableIncompleteToolbarRedesign
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }

        requirePreference<SwitchPreference>(R.string.pref_key_enable_tabs_tray_to_compose).apply {
            isVisible = true
            isChecked = context.settings().enableTabsTrayToCompose
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }

        requirePreference<SwitchPreference>(R.string.pref_key_enable_compose_top_sites).apply {
            isVisible = org.halalz.fenix.Config.channel.isNightlyOrDebug
            isChecked = context.settings().enableComposeTopSites
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }

        requirePreference<SwitchPreference>(R.string.pref_key_enable_translations).apply {
            isVisible = org.halalz.fenix.FeatureFlags.translations
            isChecked = context.settings().enableTranslations
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }

        requirePreference<SwitchPreference>(R.string.pref_key_enable_fxsuggest).apply {
            isVisible = org.halalz.fenix.FeatureFlags.fxSuggest
            isChecked = context.settings().enableFxSuggest
            onPreferenceChangeListener = object : Preference.OnPreferenceChangeListener {
                override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
                    val newBooleanValue = newValue as? Boolean ?: return false
                    val ingestionScheduler = requireContext().components.fxSuggest.ingestionScheduler
                    if (newBooleanValue) {
                        ingestionScheduler.startPeriodicIngestion()
                    } else {
                        ingestionScheduler.stopPeriodicIngestion()
                    }
                    requireContext().settings().preferences.edit {
                        putBoolean(preference.key, newBooleanValue)
                    }
                    return true
                }
            }
        }

        requirePreference<SwitchPreference>(R.string.pref_key_should_enable_felt_privacy).apply {
            isVisible = true
            isChecked = context.settings().feltPrivateBrowsingEnabled
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }

        // for performance reasons, this is only available in Nightly or Debug builds
        requirePreference<EditTextPreference>(R.string.pref_key_custom_glean_server_url).apply {
            isVisible = org.halalz.fenix.Config.channel.isNightlyOrDebug && BuildConfig.GLEAN_CUSTOM_URL.isNullOrEmpty()
        }

        requirePreference<Preference>(R.string.pref_key_custom_sponsored_stories_parameters).apply {
            isVisible = org.halalz.fenix.Config.channel.isNightlyOrDebug
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            getString(R.string.pref_key_custom_sponsored_stories_parameters) ->
                findNavController().nav(
                    R.id.secretSettingsPreference,
                    SecretSettingsFragmentDirections.actionSecretSettingsFragmentToSponsoredStoriesSettings(),
                )
        }
        return super.onPreferenceTreeClick(preference)
    }
}