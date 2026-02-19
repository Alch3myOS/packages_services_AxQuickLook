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

package com.android.axion.quicklook.provider

import android.content.Context
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import com.android.axion.quicklook.QuickLookTarget
import com.android.axion.quicklook.R
import com.android.axion.quicklook.util.SettingsHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.json.JSONObject

class AlarmProvider(context: Context, workerHandler: Handler) :
    QuickLookProvider(context, workerHandler) {

    @Volatile private var currentTarget: QuickLookTarget? = null
    private var settingsObserver: ContentObserver? = null

    override val providerType
        get() = QuickLookTarget.TYPE_ALARM

    override val settingsKey
        get() = SettingsHelper.KEY_ALARM

    override val priority
        get() = 300

    override fun getTargets(): List<QuickLookTarget> {
        val target = currentTarget
        return if (target == null || !isEnabled) emptyList() else listOf(target)
    }

    override fun start() {
        settingsObserver =
            object : ContentObserver(workerHandler) {
                override fun onChange(selfChange: Boolean) {
                    workerHandler.post(::updateFromHook)
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(KEY_NEXT_ALARM),
            false,
            settingsObserver!!,
        )
        workerHandler.post(::updateFromHook)
    }

    override fun shutdown() {
        settingsObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            settingsObserver = null
        }
    }

    private fun updateFromHook() {
        if (!isEnabled) {
            currentTarget = null
            notifyUpdate()
            return
        }

        val raw = Settings.Secure.getString(context.contentResolver, KEY_NEXT_ALARM)
        if (raw.isNullOrEmpty()) {
            currentTarget = null
            notifyUpdate()
            return
        }

        try {
            val json = JSONObject(raw)
            val triggerTime = json.optLong("triggerTime", 0L)
            if (triggerTime == 0L) {
                currentTarget = null
                notifyUpdate()
                return
            }

            val now = System.currentTimeMillis()
            if (triggerTime - now > SHOW_THRESHOLD_MILLIS || triggerTime <= now) {
                currentTarget = null
                notifyUpdate()
                return
            }

            currentTarget = buildTarget(triggerTime)
            notifyUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse alarm hook", e)
            currentTarget = null
            notifyUpdate()
        }
    }

    private fun buildTarget(triggerTime: Long): QuickLookTarget {
        val is24h = DateFormat.is24HourFormat(context)
        val pattern = if (is24h) "HH:mm" else "h:mm a"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        val timeText = sdf.format(Date(triggerTime))

        val extras =
            Bundle().apply { putLong(QuickLookTarget.EXTRA_ALARM_TRIGGER_TIME, triggerTime) }

        val minutesUntil = TimeUnit.MILLISECONDS.toMinutes(triggerTime - System.currentTimeMillis())
        val score = maxOf(0.3f, 0.7f - (minutesUntil * 0.001f))

        return QuickLookTarget.Builder("axql_alarm", QuickLookTarget.TYPE_ALARM)
            .setTitle("Alarm at $timeText")
            .setSubtitle(formatTimeUntil(triggerTime))
            .setIconResId(R.drawable.ic_alarm)
            .setScore(score)
            .setExpiryTime(triggerTime + TimeUnit.MINUTES.toMillis(5))
            .setExtras(extras)
            .build()
    }

    private fun formatTimeUntil(triggerTime: Long): String {
        val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(triggerTime - System.currentTimeMillis())
        return when {
            diffMinutes < 1 -> "now"
            diffMinutes < 60 -> "in $diffMinutes min"
            else -> {
                val hours = diffMinutes / 60
                val mins = diffMinutes % 60
                if (mins == 0L) "in ${hours}h" else "in ${hours}h ${mins}m"
            }
        }
    }

    companion object {
        private const val TAG = "AlarmProvider"
        private const val KEY_NEXT_ALARM = "ax_next_alarm"
        private val SHOW_THRESHOLD_MILLIS = TimeUnit.HOURS.toMillis(12)
    }
}
