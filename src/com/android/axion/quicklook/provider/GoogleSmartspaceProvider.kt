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

import android.app.smartspace.SmartspaceAction
import android.app.smartspace.SmartspaceConfig
import android.app.smartspace.SmartspaceManager
import android.app.smartspace.SmartspaceSession
import android.app.smartspace.SmartspaceTarget
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import com.android.axion.quicklook.QuickLookAction
import com.android.axion.quicklook.QuickLookTarget
import com.android.axion.quicklook.util.SettingsHelper
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor

class GoogleSmartspaceProvider(context: Context, workerHandler: Handler) :
    QuickLookProvider(context, workerHandler) {

    private var session: SmartspaceSession? = null
    @Volatile private var currentTargets: List<QuickLookTarget> = emptyList()

    private val listener =
        SmartspaceSession.OnTargetsAvailableListener { targets -> onTargetsAvailable(targets) }

    override val providerType
        get() = QuickLookTarget.TYPE_GOOGLE_SMARTSPACE

    override val settingsKey
        get() = SettingsHelper.KEY_GOOGLE_SMARTSPACE

    override val priority
        get() = 50

    override fun getTargets(): List<QuickLookTarget> =
        if (!isEnabled) emptyList() else ArrayList(currentTargets)

    override fun start() {
        val manager = context.getSystemService(SmartspaceManager::class.java)
        if (manager == null) {
            Log.d(TAG, "SmartspaceManager not available")
            return
        }

        try {
            val config =
                SmartspaceConfig.Builder(context, UI_SURFACE).setSmartspaceTargetCount(10).build()
            session = manager.createSmartspaceSession(config)
            session!!.addOnTargetsAvailableListener(Executor { workerHandler.post(it) }, listener)
            session!!.requestSmartspaceUpdate()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create SmartSpace session", e)
        }
    }

    override fun shutdown() {
        session?.let {
            try {
                it.removeOnTargetsAvailableListener(listener)
                it.close()
            } catch (_: Exception) {}
            session = null
        }
    }

    private fun onTargetsAvailable(smartTargets: List<SmartspaceTarget>) {
        if (!isEnabled) {
            currentTargets = emptyList()
            notifyUpdate()
            return
        }

        currentTargets =
            smartTargets.mapNotNull { st ->
                try {
                    convertTarget(st)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to convert SmartSpace target: ${st.smartspaceTargetId}", e)
                    null
                }
            }
        notifyUpdate()
    }

    private fun convertTarget(st: SmartspaceTarget): QuickLookTarget? {

        if (st.featureType == FEATURE_WEATHER) {
            return convertWeatherTarget(st)
        }

        val header = st.headerAction
        val base = st.baseAction

        var title: String? = header?.title?.toString()
        var subtitle: String? = header?.subtitle?.toString()

        if (TextUtils.isEmpty(title)) {
            title = base?.title?.toString()
        }
        if (TextUtils.isEmpty(subtitle)) {
            subtitle = base?.subtitle?.toString()
        }

        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(subtitle)) {
            return null
        }

        val primaryAction = buildAction(st, base, header)

        val extras =
            Bundle().apply {
                putInt(QuickLookTarget.EXTRA_SMARTSPACE_FEATURE_TYPE, st.featureType)
                putString(
                    QuickLookTarget.EXTRA_SMARTSPACE_COMPONENT,
                    st.componentName.flattenToShortString(),
                )
                putBoolean(QuickLookTarget.EXTRA_SMARTSPACE_SENSITIVE, st.isSensitive)

                header?.extras?.let { putBundle("smartspace_header_extras", it) }
                base?.extras?.let { putBundle("smartspace_base_extras", it) }
            }

        val iconBytes = header?.icon?.let { iconToBytes(it) }

        return QuickLookTarget.Builder(
                "axql_gss_${st.smartspaceTargetId}",
                QuickLookTarget.TYPE_GOOGLE_SMARTSPACE,
            )
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIconBytes(iconBytes)
            .setScore(st.score)
            .setCreationTime(st.creationTimeMillis)
            .setExpiryTime(st.expiryTimeMillis)
            .setPrimaryAction(primaryAction)
            .setExtras(extras)
            .build()
    }

    private fun convertWeatherTarget(st: SmartspaceTarget): QuickLookTarget? {
        val header = st.headerAction ?: return null
        val title = header.title?.toString() ?: return null
        val rawCondition = header.subtitle?.toString() ?: ""

        val temp = title.replace(Regex("[^\\d.-]"), "")
        if (temp.isEmpty()) return null

        val condition =
            if (rawCondition.replace(Regex("[\\d°.,\\s CFcf-]"), "").isEmpty()) {
                ""
            } else {
                rawCondition
            }

        val extras =
            Bundle().apply {
                putString(QuickLookTarget.EXTRA_WEATHER_TEMP, temp)
                putString(QuickLookTarget.EXTRA_WEATHER_CONDITION, condition)
                putLong(QuickLookTarget.EXTRA_WEATHER_TIMESTAMP, System.currentTimeMillis())
            }

        return QuickLookTarget.Builder("axql_gss_weather", QuickLookTarget.TYPE_WEATHER)
            .setTitle(title)
            .setIconBytes(header.icon?.let { iconToBytes(it) })
            .setScore(st.score)
            .setCreationTime(st.creationTimeMillis)
            .setExpiryTime(st.expiryTimeMillis)
            .setExtras(extras)
            .build()
    }

    private fun buildAction(
        st: SmartspaceTarget,
        base: SmartspaceAction?,
        header: SmartspaceAction?,
    ): QuickLookAction? {
        var source = base ?: header ?: return null

        if (source.pendingIntent == null && source.intent == null) {
            source = if (source === base && header != null) header else return null
            if (source.pendingIntent == null && source.intent == null) return null
        }

        val builder = QuickLookAction.Builder("gss_action_${st.smartspaceTargetId}")
        source.title?.toString()?.let { builder.setLabel(it) }

        if (source.pendingIntent != null) {
            builder.setPendingIntent(source.pendingIntent)
        } else if (source.intent != null) {
            builder.setIntent(source.intent)
        }

        return builder.build()
    }

    private fun iconToBytes(icon: Icon): ByteArray? {
        return try {
            val drawable = icon.loadDrawable(context) ?: return null
            val bitmap = drawable.toBitmap()
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            bitmap.recycle()
            stream.toByteArray()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to convert icon to bytes", e)
            null
        }
    }

    companion object {
        private const val TAG = "GoogleSmartspaceProv"
        private const val UI_SURFACE = "lockscreen"
        private const val FEATURE_WEATHER = 1
    }
}
