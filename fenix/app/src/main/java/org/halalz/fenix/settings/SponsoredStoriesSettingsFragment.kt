/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.settings

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import org.halalz.fenix.Config
import org.halalz.fenix.R
import org.halalz.fenix.ext.settings

/**
 * Allows customizing sponsored stories fetch parameters.
 */
class SponsoredStoriesSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.sponsored_stories_settings, rootKey)

        requirePreference<SwitchPreference>(R.string.pref_key_custom_sponsored_stories_parameters_enabled).apply {
            isVisible = org.halalz.fenix.Config.channel.isNightlyOrDebug
            isChecked = context.settings().useCustomConfigurationForSponsoredStories
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }

        requirePreference<EditTextPreference>(R.string.pref_key_custom_sponsored_stories_site_id).apply {
            isVisible = org.halalz.fenix.Config.channel.isNightlyOrDebug
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                context.settings().pocketSponsoredStoriesSiteId = (newValue as String)
                true
            }
        }

        requirePreference<EditTextPreference>(R.string.pref_key_custom_sponsored_stories_country).apply {
            isVisible = org.halalz.fenix.Config.channel.isNightlyOrDebug
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                context.settings().pocketSponsoredStoriesCountry = (newValue as String)
                true
            }
        }

        requirePreference<EditTextPreference>(R.string.pref_key_custom_sponsored_stories_city).apply {
            isVisible = org.halalz.fenix.Config.channel.isNightlyOrDebug
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                context.settings().pocketSponsoredStoriesCity = (newValue as String)
                true
            }
        }
    }
}
