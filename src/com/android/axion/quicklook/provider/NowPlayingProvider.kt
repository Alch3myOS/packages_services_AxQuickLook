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
import android.os.UserHandle
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
                addAction(ACTION_EXPAND)
            }

        try {
            context.registerReceiverAsUser(
                receiver,
                UserHandle.ALL,
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
        Log.d(TAG, "handleIntent: action=${intent.action}")
        when (intent.action) {
            ACTION_SHOW -> handleShow(intent)
            ACTION_EXPAND -> handleExpand(intent)
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
        val favoritingIntent =
            intent.getParcelableExtra(EXTRA_FAVORITING_INTENT, PendingIntent::class.java)
        val skipUnlock = intent.getBooleanExtra(EXTRA_SKIP_UNLOCK, false)
        val iconOverride = intent.getIntExtra(EXTRA_ICON_OVERRIDE, 0)
        val iconDescription = intent.getStringExtra(EXTRA_ICON_DESCRIPTION)
        val useExtended = intent.getBooleanExtra(EXTRA_USE_EXTENDED, false)

        val title = songTitle?.toString() ?: text?.toString() ?: return
        val subtitle = artistName?.toString()

        val extras =
            Bundle().apply {
                putString(QuickLookTarget.EXTRA_NOW_PLAYING_TITLE, songTitle?.toString())
                putString(QuickLookTarget.EXTRA_NOW_PLAYING_ARTIST, artistName?.toString())
                putBoolean("np_skip_unlock", skipUnlock)
                putInt(QuickLookTarget.EXTRA_NOW_PLAYING_ICON_OVERRIDE, iconOverride)
                iconDescription?.let {
                    putString(QuickLookTarget.EXTRA_NOW_PLAYING_ICON_DESCRIPTION, it)
                }
                favoritingIntent?.let {
                    putParcelable(QuickLookTarget.EXTRA_NOW_PLAYING_FAVORITING_INTENT, it)
                }
                if (useExtended) {
                    putBoolean(QuickLookTarget.EXTRA_NOW_PLAYING_IS_RECOGNITION, true)
                    intent.getParcelableExtra(EXTRA_EXPAND_INTENT, PendingIntent::class.java)?.let {
                        putParcelable(QuickLookTarget.EXTRA_NOW_PLAYING_EXPAND_INTENT, it)
                    }
                }
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

    private fun handleExpand(intent: Intent) {
        if (!isEnabled) return

        val text = intent.getCharSequenceExtra(EXTRA_TEXT)
        val songTitle = intent.getCharSequenceExtra(EXTRA_SONG_TITLE)
        val artistName = intent.getCharSequenceExtra(EXTRA_ARTIST_NAME)

        if (TextUtils.isEmpty(text) && TextUtils.isEmpty(songTitle)) return

        val ttlMillis =
            intent.getLongExtra(EXTRA_TTL_MILLIS, DEFAULT_TTL_MILLIS).coerceIn(0, MAX_TTL_MILLIS)
        val openIntent = intent.getParcelableExtra(EXTRA_OPEN_INTENT, PendingIntent::class.java)
        val favoritingIntent =
            intent.getParcelableExtra(EXTRA_FAVORITING_INTENT, PendingIntent::class.java)
        val albumArtUri = intent.getStringExtra(EXTRA_ALBUM_ART_URI)
        val dmpIntent = intent.getParcelableExtra(EXTRA_DMP_INTENT, PendingIntent::class.java)
        val dmpPackageName = intent.getStringExtra(EXTRA_DMP_PACKAGE_NAME)
        val isFavorite = intent.getBooleanExtra(EXTRA_IS_FAVORITE, false)

        val title = songTitle?.toString() ?: text?.toString() ?: return
        val subtitle = artistName?.toString()

        val extras =
            Bundle().apply {
                putString(QuickLookTarget.EXTRA_NOW_PLAYING_TITLE, songTitle?.toString())
                putString(QuickLookTarget.EXTRA_NOW_PLAYING_ARTIST, artistName?.toString())
                albumArtUri?.let { putString(QuickLookTarget.EXTRA_NOW_PLAYING_ALBUM_ART_URI, it) }
                putBoolean(QuickLookTarget.EXTRA_NOW_PLAYING_IS_RECOGNITION, true)
                putBoolean(QuickLookTarget.EXTRA_NOW_PLAYING_IS_FAVORITE, isFavorite)
                favoritingIntent?.let {
                    putParcelable(QuickLookTarget.EXTRA_NOW_PLAYING_FAVORITING_INTENT, it)
                }
                dmpIntent?.let {
                    putParcelable(QuickLookTarget.EXTRA_NOW_PLAYING_DMP_INTENT, it)
                }
                dmpPackageName?.let {
                    putString(QuickLookTarget.EXTRA_NOW_PLAYING_DMP_PACKAGE, it)
                }
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

        private const val ACTION_SHOW =
            "com.google.android.ambientindication.action.AMBIENT_INDICATION_SHOW"
        private const val ACTION_HIDE =
            "com.google.android.ambientindication.action.AMBIENT_INDICATION_HIDE"
        private const val ACTION_EXPAND =
            "com.google.android.ambientindication.action.AMBIENT_INDICATION_EXPAND"

        private const val PERMISSION_AMBIENT_INDICATION =
            "com.google.android.ambientindication.permission.AMBIENT_INDICATION"

        private const val EXTRA_VERSION =
            "com.google.android.ambientindication.extra.VERSION"
        private const val EXTRA_TEXT =
            "com.google.android.ambientindication.extra.TEXT"
        private const val EXTRA_SONG_TITLE =
            "com.google.android.ambientindication.extra.SONG_TITLE"
        private const val EXTRA_ARTIST_NAME =
            "com.google.android.ambientindication.extra.ARTIST_NAME"
        private const val EXTRA_TTL_MILLIS =
            "com.google.android.ambientindication.extra.TTL_MILLIS"
        private const val EXTRA_OPEN_INTENT =
            "com.google.android.ambientindication.extra.OPEN_INTENT"
        private const val EXTRA_FAVORITING_INTENT =
            "com.google.android.ambientindication.extra.FAVORITING_INTENT"
        private const val EXTRA_SKIP_UNLOCK =
            "com.google.android.ambientindication.extra.SKIP_UNLOCK"
        private const val EXTRA_ICON_OVERRIDE =
            "com.google.android.ambientindication.extra.ICON_OVERRIDE"
        private const val EXTRA_ICON_DESCRIPTION =
            "com.google.android.ambientindication.extra.ICON_DESCRIPTION"
        private const val EXTRA_USE_EXTENDED =
            "com.google.android.ambientindication.extra.USE_EXTENDED_INTERACTION"
        private const val EXTRA_EXPAND_INTENT =
            "com.google.android.ambientindication.extra.EXPAND_INTENT"
        private const val EXTRA_ALBUM_ART_URI =
            "com.google.android.ambientindication.extra.ALBUM_ART_URI"
        private const val EXTRA_IS_RECOGNITION =
            "com.google.android.ambientindication.extra.IS_RECOGNITION_RESULT"
        private const val EXTRA_DMP_INTENT =
            "com.google.android.ambientindication.extra.DMP_INTENT"
        private const val EXTRA_DMP_PACKAGE_NAME =
            "com.google.android.ambientindication.extra.DMP_PACKAGE_NAME"
        private const val EXTRA_IS_FAVORITE =
            "com.google.android.ambientindication.extra.IS_FAVORITE"

        private const val DEFAULT_TTL_MILLIS = 180_000L
        private const val MAX_TTL_MILLIS = 180_000L
    }
}
