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

data class NowPlayingData(
    val title: String,
    val artist: String?,
    val albumArtUri: String?,
    val isRecognition: Boolean = false,
    val isFavorite: Boolean = false,
    val iconOverride: Int = 0,
)

data class SportsData(
    val team1Name: String,
    val team2Name: String,
    val score1: String,
    val score2: String,
    val team1IconBytes: ByteArray?,
    val team2IconBytes: ByteArray?,
    val status: String,
    val statusDetail: String,
    val league: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SportsData) return false
        return team1Name == other.team1Name &&
            team2Name == other.team2Name &&
            score1 == other.score1 &&
            score2 == other.score2 &&
            status == other.status
    }

    override fun hashCode(): Int {
        var result = team1Name.hashCode()
        result = 31 * result + team2Name.hashCode()
        result = 31 * result + score1.hashCode()
        result = 31 * result + score2.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }
}

data class ActionChipData(
    val title: String?,
    val subtitle: String?,
    val contentDescription: String?,
    val iconBytes: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ActionChipData) return false
        return title == other.title && subtitle == other.subtitle
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (subtitle?.hashCode() ?: 0)
        return result
    }
}

data class SmartspaceTargetData(
    val id: String,
    val title: String,
    val subtitle: String,
    val featureType: Int,
    val effectiveFeatureType: Int,
    val iconBytes: ByteArray?,
    val componentName: String?,
    val isSensitive: Boolean,
    val sourceType: Int,
    val creationTime: Long,
    val score: Float,
    val actionChips: List<ActionChipData>,
    val iconGrid: List<ActionChipData>,
    val hasTemplateData: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SmartspaceTargetData) return false
        return id == other.id &&
            title == other.title &&
            subtitle == other.subtitle &&
            featureType == other.featureType &&
            effectiveFeatureType == other.effectiveFeatureType &&
            sourceType == other.sourceType
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + subtitle.hashCode()
        result = 31 * result + featureType
        result = 31 * result + effectiveFeatureType
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
            isRecognition =
                extras?.getBoolean(QuickLookTarget.EXTRA_NOW_PLAYING_IS_RECOGNITION, false)
                    ?: false,
            isFavorite =
                extras?.getBoolean(QuickLookTarget.EXTRA_NOW_PLAYING_IS_FAVORITE, false) ?: false,
            iconOverride =
                extras?.getInt(QuickLookTarget.EXTRA_NOW_PLAYING_ICON_OVERRIDE, 0) ?: 0,
        )
    }

val QuickLookTarget.sportsData: SportsData?
    get() {
        if (targetType != QuickLookTarget.TYPE_SPORTS) return null
        val extras = extras ?: return null
        return SportsData(
            team1Name = extras.getString(QuickLookTarget.EXTRA_SPORTS_TEAM1_NAME) ?: "",
            team2Name = extras.getString(QuickLookTarget.EXTRA_SPORTS_TEAM2_NAME) ?: "",
            score1 = extras.getString(QuickLookTarget.EXTRA_SPORTS_SCORE1) ?: "",
            score2 = extras.getString(QuickLookTarget.EXTRA_SPORTS_SCORE2) ?: "",
            team1IconBytes = extras.getByteArray(QuickLookTarget.EXTRA_SPORTS_TEAM1_ICON),
            team2IconBytes = extras.getByteArray(QuickLookTarget.EXTRA_SPORTS_TEAM2_ICON),
            status = extras.getString(QuickLookTarget.EXTRA_SPORTS_STATUS) ?: "",
            statusDetail = extras.getString(QuickLookTarget.EXTRA_SPORTS_STATUS_DETAIL) ?: "",
            league = extras.getString(QuickLookTarget.EXTRA_SPORTS_LEAGUE) ?: "",
        )
    }

private fun deserializeChips(extras: android.os.Bundle, countKey: String, prefixKey: String): List<ActionChipData> {
    val count = extras.getInt(countKey, 0)
    if (count == 0) return emptyList()
    val chips = mutableListOf<ActionChipData>()
    for (i in 0 until count) {
        val chipBundle = extras.getBundle("${prefixKey}${i}_data") ?: continue
        chips.add(
            ActionChipData(
                title = chipBundle.getString("title"),
                subtitle = chipBundle.getString("subtitle"),
                contentDescription = chipBundle.getString("content_description"),
                iconBytes = chipBundle.getByteArray("icon_bytes"),
            )
        )
    }
    return chips
}

val QuickLookTarget.smartspaceData: SmartspaceTargetData?
    get() {
        if (
            targetType != QuickLookTarget.TYPE_SMARTSPACER &&
                targetType != QuickLookTarget.TYPE_GOOGLE_SMARTSPACE
        )
            return null
        val extras = extras
        val featureType = extras?.getInt(QuickLookTarget.EXTRA_SMARTSPACE_FEATURE_TYPE, 0) ?: 0
        return SmartspaceTargetData(
            id = id,
            title = title ?: "",
            subtitle = subtitle ?: "",
            featureType = featureType,
            effectiveFeatureType =
                extras?.getInt(QuickLookTarget.EXTRA_SMARTSPACE_EFFECTIVE_FEATURE_TYPE, featureType)
                    ?: featureType,
            iconBytes = iconBytes,
            componentName = extras?.getString(QuickLookTarget.EXTRA_SMARTSPACE_COMPONENT),
            isSensitive =
                extras?.getBoolean(QuickLookTarget.EXTRA_SMARTSPACE_SENSITIVE, false) ?: false,
            sourceType = targetType,
            creationTime = creationTime,
            score = score,
            actionChips =
                extras?.let {
                    deserializeChips(
                        it,
                        QuickLookTarget.EXTRA_SMARTSPACE_ACTION_CHIPS_COUNT,
                        QuickLookTarget.EXTRA_SMARTSPACE_ACTION_CHIP_PREFIX,
                    )
                } ?: emptyList(),
            iconGrid =
                extras?.let {
                    deserializeChips(
                        it,
                        QuickLookTarget.EXTRA_SMARTSPACE_ICON_GRID_COUNT,
                        QuickLookTarget.EXTRA_SMARTSPACE_ICON_GRID_PREFIX,
                    )
                } ?: emptyList(),
            hasTemplateData =
                extras?.getBoolean(QuickLookTarget.EXTRA_SMARTSPACE_HAS_TEMPLATE_DATA, false)
                    ?: false,
        )
    }
