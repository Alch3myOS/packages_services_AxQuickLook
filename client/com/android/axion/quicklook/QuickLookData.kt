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

package com.android.axion.quicklook

data class WeatherData(
    val temp: String,
    val condition: String,
    val conditionCode: Int,
    val city: String?,
    val humidity: String?,
    val wind: String?,
    val windDirection: String?,
    val tempUnit: String?,
    val windUnit: String?,
    val pinWheel: String?,
    val timestamp: Long,
    val iconBytes: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WeatherData) return false
        return temp == other.temp &&
            condition == other.condition &&
            conditionCode == other.conditionCode &&
            city == other.city
    }

    override fun hashCode(): Int {
        var result = temp.hashCode()
        result = 31 * result + condition.hashCode()
        result = 31 * result + conditionCode
        result = 31 * result + (city?.hashCode() ?: 0)
        return result
    }
}

data class CalendarData(
    val id: Long,
    val title: String?,
    val startTime: Long,
    val endTime: Long,
    val location: String?,
    val description: String = "",
    val formattedTime: String = "",
    val eventStatus: Int = 0,
) {
    val isVisible: Boolean
        get() = eventStatus == 1 || eventStatus == 2
}

data class MediaData(
    val track: String,
    val artist: String,
    val album: String,
    val packageName: String?,
    val isPlaying: Boolean,
)

data class AlarmData(val triggerTime: Long)

data class NowPlayingData(val title: String, val artist: String?, val albumArtUri: String?)

data class SmartspaceTargetData(
    val id: String,
    val title: String,
    val subtitle: String,
    val featureType: Int,
    val iconBytes: ByteArray?,
    val componentName: String?,
    val isSensitive: Boolean,
    val sourceType: Int,
    val creationTime: Long,
    val score: Float,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SmartspaceTargetData) return false
        return id == other.id &&
            title == other.title &&
            subtitle == other.subtitle &&
            featureType == other.featureType &&
            sourceType == other.sourceType
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + subtitle.hashCode()
        result = 31 * result + featureType
        result = 31 * result + sourceType
        return result
    }
}

val QuickLookTarget.weatherData: WeatherData?
    get() {
        if (targetType != QuickLookTarget.TYPE_WEATHER) return null
        val extras = extras ?: return null
        return WeatherData(
            temp = extras.getString(QuickLookTarget.EXTRA_WEATHER_TEMP) ?: "",
            condition = extras.getString(QuickLookTarget.EXTRA_WEATHER_CONDITION) ?: "",
            conditionCode = extras.getInt(QuickLookTarget.EXTRA_WEATHER_CONDITION_CODE, 0),
            city = extras.getString(QuickLookTarget.EXTRA_WEATHER_CITY),
            humidity = extras.getString(QuickLookTarget.EXTRA_WEATHER_HUMIDITY),
            wind = extras.getString(QuickLookTarget.EXTRA_WEATHER_WIND),
            windDirection = extras.getString(QuickLookTarget.EXTRA_WEATHER_WIND_DIRECTION),
            tempUnit = extras.getString(QuickLookTarget.EXTRA_WEATHER_TEMP_UNIT),
            windUnit = extras.getString(QuickLookTarget.EXTRA_WEATHER_WIND_UNIT),
            pinWheel = extras.getString(QuickLookTarget.EXTRA_WEATHER_PIN_WHEEL),
            timestamp = extras.getLong(QuickLookTarget.EXTRA_WEATHER_TIMESTAMP, 0L),
            iconBytes = iconBytes,
        )
    }

val QuickLookTarget.calendarData: CalendarData?
    get() {
        if (targetType != QuickLookTarget.TYPE_CALENDAR) return null
        val extras = extras ?: return null
        return CalendarData(
            id = extras.getLong(QuickLookTarget.EXTRA_CALENDAR_EVENT_ID, 0L),
            title = title,
            startTime = extras.getLong(QuickLookTarget.EXTRA_CALENDAR_START_TIME, 0L),
            endTime = extras.getLong(QuickLookTarget.EXTRA_CALENDAR_END_TIME, 0L),
            location = extras.getString(QuickLookTarget.EXTRA_CALENDAR_LOCATION),
            description = extras.getString(QuickLookTarget.EXTRA_CALENDAR_DESCRIPTION) ?: "",
            formattedTime = extras.getString(QuickLookTarget.EXTRA_CALENDAR_TIME_RANGE) ?: "",
            eventStatus = extras.getInt(QuickLookTarget.EXTRA_CALENDAR_STATUS, 0),
        )
    }

val QuickLookTarget.mediaData: MediaData?
    get() {
        if (targetType != QuickLookTarget.TYPE_MEDIA) return null
        val extras = extras
        return MediaData(
            track = title ?: "",
            artist = extras?.getString(QuickLookTarget.EXTRA_MEDIA_ARTIST) ?: "",
            album = extras?.getString(QuickLookTarget.EXTRA_MEDIA_ALBUM) ?: "",
            packageName = extras?.getString(QuickLookTarget.EXTRA_MEDIA_PACKAGE),
            isPlaying = extras?.getBoolean(QuickLookTarget.EXTRA_MEDIA_IS_PLAYING, false) ?: false,
        )
    }

val QuickLookTarget.alarmData: AlarmData?
    get() {
        if (targetType != QuickLookTarget.TYPE_ALARM) return null
        val extras = extras ?: return null
        val trigger = extras.getLong(QuickLookTarget.EXTRA_ALARM_TRIGGER_TIME, 0L)
        return if (trigger > 0) AlarmData(trigger) else null
    }

val QuickLookTarget.nowPlayingData: NowPlayingData?
    get() {
        if (targetType != QuickLookTarget.TYPE_NOW_PLAYING) return null
        val extras = extras
        return NowPlayingData(
            title = title ?: "",
            artist = extras?.getString(QuickLookTarget.EXTRA_NOW_PLAYING_ARTIST),
            albumArtUri = extras?.getString(QuickLookTarget.EXTRA_NOW_PLAYING_ALBUM_ART_URI),
        )
    }

val QuickLookTarget.smartspaceData: SmartspaceTargetData?
    get() {
        if (
            targetType != QuickLookTarget.TYPE_SMARTSPACER &&
                targetType != QuickLookTarget.TYPE_GOOGLE_SMARTSPACE
        )
            return null
        val extras = extras
        return SmartspaceTargetData(
            id = id,
            title = title ?: "",
            subtitle = subtitle ?: "",
            featureType = extras?.getInt(QuickLookTarget.EXTRA_SMARTSPACE_FEATURE_TYPE, 0) ?: 0,
            iconBytes = iconBytes,
            componentName = extras?.getString(QuickLookTarget.EXTRA_SMARTSPACE_COMPONENT),
            isSensitive =
                extras?.getBoolean(QuickLookTarget.EXTRA_SMARTSPACE_SENSITIVE, false) ?: false,
            sourceType = targetType,
            creationTime = creationTime,
            score = score,
        )
    }
