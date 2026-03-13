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
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import com.android.axion.platform.AxPlatformClient
import com.android.axion.quicklook.QuickLookTarget
import com.android.axion.quicklook.R
import com.android.axion.quicklook.util.SettingsHelper
import java.util.concurrent.TimeUnit

class MediaProvider(context: Context, workerHandler: Handler) :
    QuickLookProvider(context, workerHandler) {

    @Volatile private var currentTarget: QuickLookTarget? = null
    private var pauseTime = 0L

    private val listener = object : AxPlatformClient.Listener() {
        override fun onStateChanged(key: String, state: Bundle) {
            if (key == AxPlatformClient.KEY_MEDIA) {
                workerHandler.post { updateFromBundle(state) }
            }
        }
    }

    override val providerType
        get() = QuickLookTarget.TYPE_MEDIA

    override val settingsKey
        get() = SettingsHelper.KEY_MEDIA

    override val priority
        get() = 250

    override fun getTargets(): List<QuickLookTarget> {
        val target = currentTarget
        return if (target == null || !isEnabled) emptyList() else listOf(target)
    }

    override fun start() {
        Log.d(TAG, "start: isEnabled=$isEnabled")
        val client = AxPlatformClient.getInstance()
        client.init(context)
        client.addListener(listener)
        workerHandler.post {
            try {
                val initial = client.getState(AxPlatformClient.KEY_MEDIA)
                if (!initial.isEmpty) updateFromBundle(initial)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read initial media state", e)
            }
        }
    }

    override fun shutdown() {
        AxPlatformClient.getInstance().removeListener(listener)
    }

    private fun updateFromBundle(bundle: Bundle) {
        if (!isEnabled) {
            currentTarget = null
            notifyUpdate()
            return
        }

        try {
            val track = bundle.getString("track", "")
            val artist = bundle.getString("artist", "")
            val album = bundle.getString("album", "")
            val isPlaying = bundle.getBoolean("isPlaying", false)
            val packageName = bundle.getString("packageName", "")

            if (TextUtils.isEmpty(track)) {
                currentTarget = null
                pauseTime = 0
                notifyUpdate()
                return
            }

            if (!isPlaying) {
                if (pauseTime == 0L) pauseTime = System.currentTimeMillis()
            } else {
                pauseTime = 0
            }

            val extras = Bundle().apply {
                putString(QuickLookTarget.EXTRA_MEDIA_ARTIST, artist)
                putString(QuickLookTarget.EXTRA_MEDIA_ALBUM, album)
                putBoolean(QuickLookTarget.EXTRA_MEDIA_IS_PLAYING, isPlaying)
                putString(QuickLookTarget.EXTRA_MEDIA_PACKAGE, packageName)
            }

            val expiryTime = if (!isPlaying && pauseTime > 0) {
                pauseTime + PAUSE_EXPIRY_MILLIS
            } else 0L

            val subtitle = when {
                !TextUtils.isEmpty(artist) -> artist
                !TextUtils.isEmpty(album) -> album
                else -> null
            }

            currentTarget = QuickLookTarget.Builder("axql_media", QuickLookTarget.TYPE_MEDIA)
                .setTitle(track)
                .setSubtitle(subtitle)
                .setIconResId(R.drawable.ic_music_note)
                .setScore(if (isPlaying) 0.75f else 0.3f)
                .setExpiryTime(expiryTime)
                .setExtras(extras)
                .build()

            notifyUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse media state", e)
            currentTarget = null
            notifyUpdate()
        }
    }

    companion object {
        private const val TAG = "MediaProvider"
        private val PAUSE_EXPIRY_MILLIS = TimeUnit.SECONDS.toMillis(30)
    }
}
