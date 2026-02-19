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

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import com.android.axion.quicklook.QuickLookAction
import com.android.axion.quicklook.QuickLookTarget
import com.android.axion.quicklook.R
import com.android.axion.quicklook.util.SettingsHelper

class NowPlayingProvider(context: Context, workerHandler: Handler) :
    QuickLookProvider(context, workerHandler) {

    @Volatile private var currentTarget: QuickLookTarget? = null
    private var receiver: BroadcastReceiver? = null
    private val hideRunnable = Runnable {
        currentTarget = null
        notifyUpdate()
    }

    override val providerType
        get() = QuickLookTarget.TYPE_NOW_PLAYING

    override val settingsKey
        get() = SettingsHelper.KEY_NOW_PLAYING

    override val priority
        get() = 225

    override fun getTargets(): List<QuickLookTarget> {
        val target = currentTarget
        return if (target == null || !isEnabled) emptyList() else listOf(target)
    }

    override fun start() {
        receiver =
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    workerHandler.post { handleIntent(intent) }
                }
            }

        val filter =
            IntentFilter().apply {
                addAction(ACTION_SHOW)
                addAction(ACTION_HIDE)
            }

        try {
            context.registerReceiver(
                receiver,
                filter,
                PERMISSION_AMBIENT_INDICATION,
                workerHandler,
                Context.RECEIVER_EXPORTED,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Now Playing receiver", e)
        }
    }

    override fun shutdown() {
        workerHandler.removeCallbacks(hideRunnable)
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
            receiver = null
        }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_SHOW -> handleShow(intent)
            ACTION_HIDE -> handleHide()
        }
    }

    private fun handleShow(intent: Intent) {
        if (!isEnabled) return

        val version = intent.getIntExtra(EXTRA_VERSION, 0)
        if (version != 1) {
            Log.w(TAG, "Unsupported ambient indication version: $version")
            return
        }

        val text = intent.getCharSequenceExtra(EXTRA_TEXT)
        val songTitle = intent.getCharSequenceExtra(EXTRA_SONG_TITLE)
        val artistName = intent.getCharSequenceExtra(EXTRA_ARTIST_NAME)

        if (TextUtils.isEmpty(text) && TextUtils.isEmpty(songTitle)) return

        val ttlMillis =
            intent.getLongExtra(EXTRA_TTL_MILLIS, DEFAULT_TTL_MILLIS).coerceIn(0, MAX_TTL_MILLIS)
        val openIntent = intent.getParcelableExtra(EXTRA_OPEN_INTENT, PendingIntent::class.java)
        val skipUnlock = intent.getBooleanExtra(EXTRA_SKIP_UNLOCK, false)
        val albumArtUri = intent.getStringExtra(EXTRA_ALBUM_ART_URI)

        val title = songTitle?.toString() ?: text?.toString() ?: return
        val subtitle = artistName?.toString()

        val extras =
            Bundle().apply {
                putString(QuickLookTarget.EXTRA_NOW_PLAYING_TITLE, songTitle?.toString())
                putString(QuickLookTarget.EXTRA_NOW_PLAYING_ARTIST, artistName?.toString())
                albumArtUri?.let { putString(QuickLookTarget.EXTRA_NOW_PLAYING_ALBUM_ART_URI, it) }
                putBoolean("np_skip_unlock", skipUnlock)
            }

        val action =
            openIntent?.let {
                QuickLookAction.Builder("now_playing_action")
                    .setLabel("Open")
                    .setPendingIntent(it)
                    .build()
            }

        currentTarget =
            QuickLookTarget.Builder("axql_now_playing", QuickLookTarget.TYPE_NOW_PLAYING)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIconResId(R.drawable.ic_music_note)
                .setScore(0.6f)
                .setExpiryTime(System.currentTimeMillis() + ttlMillis)
                .setPrimaryAction(action)
                .setExtras(extras)
                .build()

        notifyUpdate()

        workerHandler.removeCallbacks(hideRunnable)
        workerHandler.postDelayed(hideRunnable, ttlMillis)
    }

    private fun handleHide() {
        workerHandler.removeCallbacks(hideRunnable)
        currentTarget = null
        notifyUpdate()
    }

    companion object {
        private const val TAG = "NowPlayingProvider"

        private const val ACTION_SHOW = "AMBIENT_INDICATION_SHOW"
        private const val ACTION_HIDE = "AMBIENT_INDICATION_HIDE"

        private const val PERMISSION_AMBIENT_INDICATION = "AMBIENT_INDICATION"

        private const val EXTRA_VERSION = "VERSION"
        private const val EXTRA_TEXT = "TEXT"
        private const val EXTRA_SONG_TITLE = "SONG_TITLE"
        private const val EXTRA_ARTIST_NAME = "ARTIST_NAME"
        private const val EXTRA_TTL_MILLIS = "TTL_MILLIS"
        private const val EXTRA_OPEN_INTENT = "OPEN_INTENT"
        private const val EXTRA_SKIP_UNLOCK = "SKIP_UNLOCK"
        private const val EXTRA_ALBUM_ART_URI = "ALBUM_ART_URI"

        private const val DEFAULT_TTL_MILLIS = 180_000L
        private const val MAX_TTL_MILLIS = 180_000L
    }
}
