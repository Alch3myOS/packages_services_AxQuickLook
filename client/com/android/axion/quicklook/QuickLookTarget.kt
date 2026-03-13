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

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import java.util.Objects

class QuickLookTarget
private constructor(
    val id: String,
    val targetType: Int,
    val title: String?,
    val subtitle: String?,
    val iconResId: Int,
    val iconBytes: ByteArray?,
    val creationTime: Long,
    val expiryTime: Long,
    val score: Float,
    val primaryAction: QuickLookAction?,
    val extras: Bundle?,
) : Parcelable {

    val isExpired: Boolean
        get() = expiryTime > 0 && System.currentTimeMillis() > expiryTime

    private constructor(
        parcel: Parcel
    ) : this(
        id = parcel.readString()!!,
        targetType = parcel.readInt(),
        title = parcel.readString(),
        subtitle = parcel.readString(),
        iconResId = parcel.readInt(),
        iconBytes = parcel.createByteArray(),
        creationTime = parcel.readLong(),
        expiryTime = parcel.readLong(),
        score = parcel.readFloat(),
        primaryAction = parcel.readTypedObject(QuickLookAction.CREATOR),
        extras = parcel.readBundle(QuickLookTarget::class.java.classLoader),
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(id)
        dest.writeInt(targetType)
        dest.writeString(title)
        dest.writeString(subtitle)
        dest.writeInt(iconResId)
        dest.writeByteArray(iconBytes)
        dest.writeLong(creationTime)
        dest.writeLong(expiryTime)
        dest.writeFloat(score)
        dest.writeTypedObject(primaryAction, flags)
        dest.writeBundle(extras)
    }

    override fun describeContents() = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QuickLookTarget) return false
        return targetType == other.targetType &&
            creationTime == other.creationTime &&
            score.compareTo(other.score) == 0 &&
            id == other.id &&
            title == other.title &&
            subtitle == other.subtitle
    }

    override fun hashCode() = Objects.hash(id, targetType, title, subtitle, creationTime, score)

    override fun toString() =
        "QuickLookTarget{id='$id', type=$targetType, title='$title', " +
            "subtitle='$subtitle', score=$score, expiry=$expiryTime}"

    companion object {

        const val TYPE_WEATHER = 1
        const val TYPE_CALENDAR = 2
        const val TYPE_ALARM = 3
        const val TYPE_MEDIA = 4
        const val TYPE_TIMER = 5
        const val TYPE_NOW_PLAYING = 6
        const val TYPE_SPORTS = 7
        const val TYPE_SMARTSPACER = 100
        const val TYPE_GOOGLE_SMARTSPACE = 200

        const val EXTRA_WEATHER_TEMP = "weather_temp"
        const val EXTRA_WEATHER_CONDITION = "weather_condition"
        const val EXTRA_WEATHER_CONDITION_CODE = "weather_condition_code"
        const val EXTRA_WEATHER_CITY = "weather_city"
        const val EXTRA_WEATHER_HUMIDITY = "weather_humidity"
        const val EXTRA_WEATHER_WIND = "weather_wind"
        const val EXTRA_WEATHER_WIND_DIRECTION = "weather_wind_direction"
        const val EXTRA_WEATHER_TEMP_UNIT = "weather_temp_unit"
        const val EXTRA_WEATHER_WIND_UNIT = "weather_wind_unit"
        const val EXTRA_WEATHER_PIN_WHEEL = "weather_pin_wheel"
        const val EXTRA_WEATHER_TIMESTAMP = "weather_timestamp"

        const val EXTRA_CALENDAR_EVENT_ID = "cal_event_id"
        const val EXTRA_CALENDAR_LOCATION = "cal_location"
        const val EXTRA_CALENDAR_START_TIME = "cal_start_time"
        const val EXTRA_CALENDAR_END_TIME = "cal_end_time"
        const val EXTRA_CALENDAR_DESCRIPTION = "cal_description"
        const val EXTRA_CALENDAR_TIME_RANGE = "cal_time_range"
        const val EXTRA_CALENDAR_STATUS = "cal_status"

        const val EXTRA_MEDIA_ARTIST = "media_artist"
        const val EXTRA_MEDIA_ALBUM = "media_album"
        const val EXTRA_MEDIA_PACKAGE = "media_package"
        const val EXTRA_MEDIA_IS_PLAYING = "media_is_playing"

        const val EXTRA_ALARM_TRIGGER_TIME = "alarm_trigger_time"

        const val EXTRA_NOW_PLAYING_TITLE = "np_title"
        const val EXTRA_NOW_PLAYING_ARTIST = "np_artist"
        const val EXTRA_NOW_PLAYING_ALBUM_ART_URI = "np_album_art_uri"

        const val EXTRA_SPORTS_TEAM1_NAME = "sports_team1_name"
        const val EXTRA_SPORTS_TEAM2_NAME = "sports_team2_name"
        const val EXTRA_SPORTS_SCORE1 = "sports_score1"
        const val EXTRA_SPORTS_SCORE2 = "sports_score2"
        const val EXTRA_SPORTS_TEAM1_ICON = "sports_team1_icon"
        const val EXTRA_SPORTS_TEAM2_ICON = "sports_team2_icon"
        const val EXTRA_SPORTS_STATUS = "sports_status"
        const val EXTRA_SPORTS_STATUS_DETAIL = "sports_status_detail"
        const val EXTRA_SPORTS_LEAGUE = "sports_league"

        const val EXTRA_SMARTSPACE_FEATURE_TYPE = "smartspace_feature_type"
        const val EXTRA_SMARTSPACE_EFFECTIVE_FEATURE_TYPE = "smartspace_effective_feature_type"
        const val EXTRA_SMARTSPACE_ICON = "smartspace_icon"
        const val EXTRA_SMARTSPACE_COMPONENT = "smartspace_component"
        const val EXTRA_SMARTSPACE_SENSITIVE = "smartspace_sensitive"
        const val EXTRA_SMARTSPACE_ACTION_CHIPS_COUNT = "smartspace_action_chips_count"
        const val EXTRA_SMARTSPACE_ACTION_CHIP_PREFIX = "smartspace_action_chip_"
        const val EXTRA_SMARTSPACE_ICON_GRID_COUNT = "smartspace_icon_grid_count"
        const val EXTRA_SMARTSPACE_ICON_GRID_PREFIX = "smartspace_icon_grid_"
        const val EXTRA_SMARTSPACE_HAS_TEMPLATE_DATA = "smartspace_has_template_data"

        const val EXTRA_NOW_PLAYING_FAVORITING_INTENT = "np_favoriting_intent"
        const val EXTRA_NOW_PLAYING_ICON_OVERRIDE = "np_icon_override"
        const val EXTRA_NOW_PLAYING_ICON_DESCRIPTION = "np_icon_description"
        const val EXTRA_NOW_PLAYING_IS_RECOGNITION = "np_is_recognition"
        const val EXTRA_NOW_PLAYING_EXPAND_INTENT = "np_expand_intent"
        const val EXTRA_NOW_PLAYING_DMP_INTENT = "np_dmp_intent"
        const val EXTRA_NOW_PLAYING_DMP_PACKAGE = "np_dmp_package"
        const val EXTRA_NOW_PLAYING_IS_FAVORITE = "np_is_favorite"

        @JvmField
        val CREATOR =
            object : Parcelable.Creator<QuickLookTarget> {
                override fun createFromParcel(source: Parcel) = QuickLookTarget(source)

                override fun newArray(size: Int) = arrayOfNulls<QuickLookTarget>(size)
            }
    }

    class Builder(private val id: String, private val targetType: Int) {
        private var title: String? = null
        private var subtitle: String? = null
        private var iconResId: Int = 0
        private var iconBytes: ByteArray? = null
        private var creationTime: Long = System.currentTimeMillis()
        private var expiryTime: Long = 0
        private var score: Float = 0f
        private var primaryAction: QuickLookAction? = null
        private var extras: Bundle? = null

        fun setTitle(title: String?) = apply { this.title = title }

        fun setSubtitle(subtitle: String?) = apply { this.subtitle = subtitle }

        fun setIconResId(iconResId: Int) = apply { this.iconResId = iconResId }

        fun setIconBytes(iconBytes: ByteArray?) = apply { this.iconBytes = iconBytes }

        fun setCreationTime(creationTime: Long) = apply { this.creationTime = creationTime }

        fun setExpiryTime(expiryTime: Long) = apply { this.expiryTime = expiryTime }

        fun setScore(score: Float) = apply { this.score = score }

        fun setPrimaryAction(primaryAction: QuickLookAction?) = apply {
            this.primaryAction = primaryAction
        }

        fun setExtras(extras: Bundle?) = apply { this.extras = extras }

        fun build() =
            QuickLookTarget(
                id,
                targetType,
                title,
                subtitle,
                iconResId,
                iconBytes,
                creationTime,
                expiryTime,
                score,
                primaryAction,
                extras,
            )
    }
}
