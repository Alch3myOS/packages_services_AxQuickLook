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

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.database.Cursor
import android.icu.text.DateTimePatternGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.util.Log
import com.android.axion.quicklook.QuickLookAction
import com.android.axion.quicklook.QuickLookTarget
import com.android.axion.quicklook.R
import com.android.axion.quicklook.util.SettingsHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CalendarProvider(context: Context, workerHandler: Handler) :
    QuickLookProvider(context, workerHandler) {

    private var calendarObserver: ContentObserver? = null
    @Volatile private var currentTarget: QuickLookTarget? = null
    private val refreshRunnable = Runnable { queryAndUpdate() }

    override val providerType
        get() = QuickLookTarget.TYPE_CALENDAR

    override val settingsKey
        get() = SettingsHelper.KEY_CALENDAR

    override val priority
        get() = 200

    override fun getTargets(): List<QuickLookTarget> {
        val target = currentTarget
        return if (target == null || !isEnabled) emptyList() else listOf(target)
    }

    override fun start() {
        calendarObserver =
            object : ContentObserver(workerHandler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    queryAndUpdate()
                }
            }

        val uris =
            arrayOf(
                CalendarContract.Instances.CONTENT_URI,
                CalendarContract.Events.CONTENT_URI,
                CalendarContract.Calendars.CONTENT_URI,
                CalendarContract.Reminders.CONTENT_URI,
                CalendarContract.Attendees.CONTENT_URI,
            )
        for (uri in uris) {
            context.contentResolver.registerContentObserver(uri, true, calendarObserver!!)
        }

        workerHandler.post(::queryAndUpdate)
    }

    override fun shutdown() {
        workerHandler.removeCallbacks(refreshRunnable)
        calendarObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            calendarObserver = null
        }
    }

    private fun queryAndUpdate() {
        workerHandler.removeCallbacks(refreshRunnable)

        if (!isEnabled) {
            currentTarget = null
            notifyUpdate()
            return
        }

        val now = System.currentTimeMillis()
        val end = now + WINDOW_MILLIS

        val queryUri =
            CalendarContract.Instances.CONTENT_URI.buildUpon()
                .apply {
                    ContentUris.appendId(this, now)
                    ContentUris.appendId(this, end)
                }
                .build()

        var event: EventData? = null
        try {
            context.contentResolver.query(queryUri, PROJECTION, SELECTION, null, SORT_ORDER)?.use {
                cursor ->
                while (cursor.moveToNext()) {
                    val candidate = EventData.fromCursor(cursor)
                    if (isEventVisible(candidate) && isEventValid(candidate)) {
                        event = candidate
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query calendar", e)
        }

        val ev = event
        if (ev == null) {
            currentTarget = null
            notifyUpdate()
        } else {
            currentTarget = buildTarget(ev)
            notifyUpdate()
        }

        workerHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MILLIS)
    }

    private fun getEventStatus(event: EventData): Int {
        val nowMinutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis())
        val startMinutes = TimeUnit.MILLISECONDS.toMinutes(event.startTime)
        val toBeginMinutes = startMinutes - nowMinutes
        return when {
            toBeginMinutes > THRESHOLD_TO_BEGIN -> STATUS_TO_SCHEDULE
            toBeginMinutes > 0 -> STATUS_TO_BEGIN
            toBeginMinutes > -THRESHOLD_NOW -> STATUS_NOW
            else -> STATUS_END
        }
    }

    private fun isEventVisible(event: EventData): Boolean {
        val status = getEventStatus(event)
        return status == STATUS_TO_BEGIN || status == STATUS_NOW
    }

    private fun getDescription(event: EventData): String {
        return when (getEventStatus(event)) {
            STATUS_TO_BEGIN -> {
                val nowMinutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis())
                val startMinutes = TimeUnit.MILLISECONDS.toMinutes(event.startTime)
                val toBegin = startMinutes - nowMinutes
                context.getString(R.string.calendar_in_time, toBegin.toInt())
            }
            STATUS_NOW -> context.getString(R.string.calendar_now)
            else -> ""
        }
    }

    private fun isEventValid(event: EventData): Boolean {
        return try {
            context.contentResolver
                .query(
                    CalendarContract.Events.CONTENT_URI,
                    arrayOf("_id", "deleted"),
                    "_id = ?",
                    arrayOf(event.id.toString()),
                    null,
                )
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex("deleted")
                        idx == -1 || cursor.getInt(idx) == 0
                    } else false
                } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate event", e)
            false
        }
    }

    private fun buildTarget(event: EventData): QuickLookTarget {
        val status = getEventStatus(event)
        val description = getDescription(event)
        val timeRange = formatTimeRange(event.startTime, event.endTime)

        val extras =
            Bundle().apply {
                putLong(QuickLookTarget.EXTRA_CALENDAR_EVENT_ID, event.id)
                putLong(QuickLookTarget.EXTRA_CALENDAR_START_TIME, event.startTime)
                putLong(QuickLookTarget.EXTRA_CALENDAR_END_TIME, event.endTime)
                event.location?.let { putString(QuickLookTarget.EXTRA_CALENDAR_LOCATION, it) }
                putString(QuickLookTarget.EXTRA_CALENDAR_DESCRIPTION, description)
                putString(QuickLookTarget.EXTRA_CALENDAR_TIME_RANGE, timeRange)
                putInt(QuickLookTarget.EXTRA_CALENDAR_STATUS, status)
            }

        val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
        val calIntent = Intent(Intent.ACTION_VIEW).setData(eventUri)
        val action =
            QuickLookAction.Builder("calendar_action_${event.id}")
                .setLabel("Open event")
                .setIntent(calIntent)
                .build()

        val nowMinutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis())
        val startMinutes = TimeUnit.MILLISECONDS.toMinutes(event.startTime)
        val minutesUntilStart = startMinutes - nowMinutes
        val score = if (minutesUntilStart <= 0) 0.9f else 0.8f - (minutesUntilStart * 0.01f)

        return QuickLookTarget.Builder("axql_calendar_${event.id}", QuickLookTarget.TYPE_CALENDAR)
            .setTitle(event.title ?: "Calendar event")
            .setSubtitle(timeRange)
            .setIconResId(R.drawable.ic_calendar)
            .setScore(maxOf(0.1f, score))
            .setExpiryTime(event.endTime)
            .setPrimaryAction(action)
            .setExtras(extras)
            .build()
    }

    private fun formatTimeRange(startMillis: Long, endMillis: Long): String {
        val now = System.currentTimeMillis()
        var startStr = getFormattedTime(startMillis, now)
        var endStr = getFormattedTime(endMillis, now)

        if (isNextDay(now, startMillis) && isNextDay(now, endMillis)) {
            val tomorrowPrefix = "${context.getString(R.string.calendar_tomorrow)} "
            if (endStr.startsWith(tomorrowPrefix)) {
                endStr = endStr.removePrefix(tomorrowPrefix)
            }
        }

        return "$startStr - $endStr"
    }

    private fun getFormattedTime(eventTime: Long, referenceTime: Long): String {
        val timeFormat = if (DateFormat.is24HourFormat(context)) FORMAT_24_HOUR else FORMAT_12_HOUR
        return when {
            isSameDay(referenceTime, eventTime) -> {
                formatTimestamp(eventTime, timeFormat)
            }
            isNextDay(referenceTime, eventTime) -> {
                "${context.getString(R.string.calendar_tomorrow)} ${formatTimestamp(eventTime, timeFormat)}"
            }
            isPreviousDay(referenceTime, eventTime) -> {
                "${context.getString(R.string.calendar_yesterday)} ${formatTimestamp(eventTime, timeFormat)}"
            }
            isSameYear(referenceTime, eventTime) -> {
                formatTimestamp(eventTime, FORMAT_SAME_YEAR + timeFormat)
            }
            else -> {
                formatTimestamp(eventTime, FORMAT_DIFF_YEAR + timeFormat)
            }
        }
    }

    private fun formatTimestamp(timestamp: Long, patternSkeleton: String): String {
        val pattern =
            DateTimePatternGenerator.getInstance(Locale.getDefault())
                .getBestPattern(patternSkeleton)
        return android.icu.text
            .SimpleDateFormat(pattern, Locale.getDefault())
            .format(Date(timestamp))
    }

    private fun isSameYear(time1: Long, time2: Long): Boolean {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time1
        val year1 = cal.get(Calendar.YEAR)
        cal.timeInMillis = time2
        return year1 == cal.get(Calendar.YEAR)
    }

    private fun isSameDay(time1: Long, time2: Long): Boolean {
        if (!isSameYear(time1, time2)) return false
        val cal = Calendar.getInstance()
        cal.timeInMillis = time1
        val day1 = cal.get(Calendar.DAY_OF_YEAR)
        cal.timeInMillis = time2
        return day1 == cal.get(Calendar.DAY_OF_YEAR)
    }

    private fun isNextDay(base: Long, target: Long): Boolean =
        isSameDay(base + ONE_DAY_MILLIS, target)

    private fun isPreviousDay(base: Long, target: Long): Boolean =
        isSameDay(target + ONE_DAY_MILLIS, base)

    private data class EventData(
        val id: Long,
        val title: String?,
        val startTime: Long,
        val endTime: Long,
        val location: String?,
    ) {
        companion object {
            fun fromCursor(cursor: Cursor) =
                EventData(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("event_id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    startTime = cursor.getLong(cursor.getColumnIndexOrThrow("begin")),
                    endTime = cursor.getLong(cursor.getColumnIndexOrThrow("end")),
                    location = cursor.getString(cursor.getColumnIndexOrThrow("eventLocation")),
                )
        }
    }

    companion object {
        private const val TAG = "CalendarProvider"
        private val WINDOW_MILLIS = TimeUnit.HOURS.toMillis(24)
        private val REFRESH_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1)
        private val PROJECTION = arrayOf("event_id", "title", "begin", "end", "eventLocation")
        private const val SELECTION = "visible = 1 AND allDay = 0"
        private const val SORT_ORDER = "begin ASC"
        private const val THRESHOLD_TO_BEGIN = 20
        private const val THRESHOLD_NOW = 10
        private const val ONE_DAY_MILLIS = 86_400_000L

        const val STATUS_TO_SCHEDULE = 0
        const val STATUS_TO_BEGIN = 1
        const val STATUS_NOW = 2
        const val STATUS_END = 3

        private const val FORMAT_12_HOUR = "hh:mm"
        private const val FORMAT_24_HOUR = "HH:mm"
        private const val FORMAT_SAME_YEAR = "d/MMM "
        private const val FORMAT_DIFF_YEAR = "d/MMM/yyyy "
    }
}
