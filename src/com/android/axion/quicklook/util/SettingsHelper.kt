/*
 * Copyright (C) 2025 AxionOS Project
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

package com.android.axion.quicklook.util

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings

object SettingsHelper {

    const val KEY_WEATHER = "nt_quicklook_weather"
    const val KEY_CALENDAR = "nt_quicklook_events"
    const val KEY_ALARM = "nt_quicklook_alarm"
    const val KEY_MEDIA = "nt_quicklook_np"
    const val KEY_SMARTSPACER = "nt_quicklook_smartspacer"
    const val KEY_GOOGLE_SMARTSPACE = "nt_quicklook_google_smartspace"
    const val KEY_NOW_PLAYING = "nt_quicklook_now_playing"

    private val ALL_KEYS =
        arrayOf(
            KEY_WEATHER,
            KEY_CALENDAR,
            KEY_ALARM,
            KEY_MEDIA,
            KEY_SMARTSPACER,
            KEY_GOOGLE_SMARTSPACE,
            KEY_NOW_PLAYING,
        )

    fun registerSettingsObserver(
        context: Context,
        handler: Handler,
        onChange: () -> Unit,
    ): ContentObserver {
        val observer =
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    onChange()
                }
            }
        for (key in ALL_KEYS) {
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(key),
                false,
                observer,
            )
        }
        return observer
    }
}
