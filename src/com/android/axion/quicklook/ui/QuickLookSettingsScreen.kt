/*
 * Copyright (C) 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.axion.quicklook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.axion.compose.preferences.SecureSettingSwitch
import com.android.axion.compose.scaffold.AxionScaffold
import com.android.axion.quicklook.R
import com.android.axion.quicklook.util.SettingsHelper

@Composable
fun QuickLookSettingsScreen(onBack: () -> Unit) {
    AxionScaffold(
        title = stringResource(R.string.quicklook_settings_title),
        onBackClick = onBack,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreferenceGroup(title = stringResource(R.string.quicklook_category_sources)) {
                item {
                    SecureSettingSwitch(
                        settingKey = SettingsHelper.KEY_WEATHER,
                        title = stringResource(R.string.quicklook_weather_title),
                        summary = stringResource(R.string.quicklook_weather_summary),
                        defaultValue = true,
                    )
                }
                item {
                    SecureSettingSwitch(
                        settingKey = SettingsHelper.KEY_CALENDAR,
                        title = stringResource(R.string.quicklook_calendar_title),
                        summary = stringResource(R.string.quicklook_calendar_summary),
                        defaultValue = true,
                    )
                }
                item {
                    SecureSettingSwitch(
                        settingKey = SettingsHelper.KEY_ALARM,
                        title = stringResource(R.string.quicklook_alarm_title),
                        summary = stringResource(R.string.quicklook_alarm_summary),
                        defaultValue = true,
                    )
                }
                item {
                    SecureSettingSwitch(
                        settingKey = SettingsHelper.KEY_MEDIA,
                        title = stringResource(R.string.quicklook_media_title),
                        summary = stringResource(R.string.quicklook_media_summary),
                        defaultValue = true,
                    )
                }
                item {
                    SecureSettingSwitch(
                        settingKey = SettingsHelper.KEY_NOW_PLAYING,
                        title = stringResource(R.string.quicklook_now_playing_title),
                        summary = stringResource(R.string.quicklook_now_playing_summary),
                        defaultValue = true,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.quicklook_category_smartspace)) {
                item {
                    SecureSettingSwitch(
                        settingKey = SettingsHelper.KEY_GOOGLE_SMARTSPACE,
                        title = stringResource(R.string.quicklook_google_smartspace_title),
                        summary = stringResource(R.string.quicklook_google_smartspace_summary),
                        defaultValue = true,
                    )
                }
                item {
                    SecureSettingSwitch(
                        settingKey = SettingsHelper.KEY_SMARTSPACER,
                        title = stringResource(R.string.quicklook_smartspacer_title),
                        summary = stringResource(R.string.quicklook_smartspacer_summary),
                        defaultValue = true,
                    )
                }
            }
        }
    }
}
