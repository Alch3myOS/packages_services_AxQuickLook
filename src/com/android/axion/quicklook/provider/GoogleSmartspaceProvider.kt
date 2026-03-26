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
import android.app.smartspace.SmartspaceAction
import android.app.smartspace.SmartspaceConfig
import android.app.smartspace.SmartspaceManager
import android.app.smartspace.SmartspaceSession
import android.app.smartspace.SmartspaceTarget
import android.app.smartspace.uitemplatedata.HeadToHeadTemplateData
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
        Log.d(TAG, "start: isEnabled=$isEnabled")
        val manager = context.getSystemService(SmartspaceManager::class.java)
        if (manager == null) {
            Log.w(TAG, "SmartspaceManager not available")
            return
        }

        try {
            val config =
                SmartspaceConfig.Builder(context, UI_SURFACE).setSmartspaceTargetCount(10).build()
            session = manager.createSmartspaceSession(config)
            session!!.addOnTargetsAvailableListener(Executor { workerHandler.post(it) }, listener)
            session!!.requestSmartspaceUpdate()
            Log.d(TAG, "Session created and update requested")
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
        Log.d(TAG, "onTargetsAvailable: ${smartTargets.size} targets, isEnabled=$isEnabled")
        for (st in smartTargets) {
            Log.d(TAG, "  target: id=${st.smartspaceTargetId} featureType=${st.featureType}" +
                " header=${st.headerAction?.title} component=${st.componentName}")
        }
        if (!isEnabled) {
            currentTargets = emptyList()
            notifyUpdate()
            return
        }

        currentTargets =
            smartTargets.mapNotNull { st ->
                try {
                    val result = convertTarget(st)
                    if (result == null) {
                        Log.d(TAG, "  convertTarget returned null for ${st.smartspaceTargetId}")
                    } else {
                        Log.d(TAG, "  converted: id=${result.id} type=${result.targetType}" +
                            " title=${result.title}")
                    }
                    result
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to convert SmartSpace target: ${st.smartspaceTargetId}", e)
                    null
                }
            }
        Log.d(TAG, "onTargetsAvailable: ${currentTargets.size} converted targets")
        notifyUpdate()
    }

    private fun getEffectiveFeatureType(st: SmartspaceTarget): Int {
        val actionChips = st.actionChips
        val featureType = st.featureType
        if (actionChips.isNullOrEmpty()) return featureType
        return if (featureType == FEATURE_SHOPPING_LIST && actionChips.size == 1) {
            FEATURE_COMBINATION_AT_STORE
        } else {
            FEATURE_COMBINATION
        }
    }

    private fun convertTarget(st: SmartspaceTarget): QuickLookTarget? {
        if (st.featureType == FEATURE_MEDIA) return null
        if (st.featureType == FEATURE_MEDIA_INTERNAL) return null

        if (st.featureType == FEATURE_WEATHER) {
            return convertWeatherTarget(st)
        }

        if (st.featureType == FEATURE_SPORTS) {
            return convertSportsTarget(st)
        }

        val h2h = try {
            st.templateData as? HeadToHeadTemplateData
        } catch (_: Exception) {
            null
        }
        if (h2h != null) {
            return convertHeadToHeadTarget(st, h2h)
        }

        val header = st.headerAction
        val base = st.baseAction
        val actionChips = st.actionChips
        val iconGrid = st.iconGrid

        var title: String? = header?.title?.toString()
        var subtitle: String? = header?.subtitle?.toString()

        if (TextUtils.isEmpty(title)) {
            title = base?.title?.toString()
        }
        if (TextUtils.isEmpty(subtitle)) {
            subtitle = base?.subtitle?.toString()
        }

        val hasActionChips = !actionChips.isNullOrEmpty()
        val hasIconGrid = !iconGrid.isNullOrEmpty()
        val hasTemplateData = try { st.templateData != null } catch (_: Exception) { false }

        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(subtitle) &&
            !hasActionChips && !hasIconGrid && !hasTemplateData) {
            return null
        }

        val primaryAction = buildAction(st, base, header)
        val effectiveFeatureType = getEffectiveFeatureType(st)

        val extras =
            Bundle().apply {
                putInt(QuickLookTarget.EXTRA_SMARTSPACE_FEATURE_TYPE, st.featureType)
                putInt(QuickLookTarget.EXTRA_SMARTSPACE_EFFECTIVE_FEATURE_TYPE, effectiveFeatureType)
                putString(
                    QuickLookTarget.EXTRA_SMARTSPACE_COMPONENT,
                    st.componentName?.flattenToShortString(),
                )
                putBoolean(QuickLookTarget.EXTRA_SMARTSPACE_SENSITIVE, st.isSensitive)

                header?.extras?.let { putBundle("smartspace_header_extras", it) }
                base?.extras?.let { putBundle("smartspace_base_extras", it) }

                if (hasActionChips) {
                    serializeActionChips(this, actionChips!!)
                }

                if (hasIconGrid) {
                    serializeIconGrid(this, iconGrid!!)
                }

                if (hasTemplateData) {
                    putBoolean(QuickLookTarget.EXTRA_SMARTSPACE_HAS_TEMPLATE_DATA, true)
                }
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

    private fun convertSportsTarget(st: SmartspaceTarget): QuickLookTarget? {
        val header = st.headerAction
        val base = st.baseAction
        val headerExtras = try {
            header?.extras
        } catch (_: Exception) { null }
        val baseExtras = try {
            base?.extras
        } catch (_: Exception) { null }

        val extras = Bundle().apply {
            headerExtras?.let { he ->
                safeGetString(he, "matchTimeSummary")?.let {
                    putString(QuickLookTarget.EXTRA_SPORTS_STATUS_DETAIL, it)
                }
                safeGetString(he, "firstCompetitorScore")?.let {
                    putString(QuickLookTarget.EXTRA_SPORTS_SCORE1, it)
                }
                safeGetString(he, "secondCompetitorScore")?.let {
                    putString(QuickLookTarget.EXTRA_SPORTS_SCORE2, it)
                }
                safeGetBitmap(he, "firstCompetitorLogo")?.let { bmp ->
                    bitmapToBytes(bmp)?.let { putByteArray(QuickLookTarget.EXTRA_SPORTS_TEAM1_ICON, it) }
                }
                safeGetBitmap(he, "secondCompetitorLogo")?.let { bmp ->
                    bitmapToBytes(bmp)?.let { putByteArray(QuickLookTarget.EXTRA_SPORTS_TEAM2_ICON, it) }
                }
            }
            baseExtras?.let { be ->
                if (!containsKey(QuickLookTarget.EXTRA_SPORTS_SCORE1)) {
                    safeGetString(be, "firstCompetitorScore")?.let {
                        putString(QuickLookTarget.EXTRA_SPORTS_SCORE1, it)
                    }
                }
                if (!containsKey(QuickLookTarget.EXTRA_SPORTS_SCORE2)) {
                    safeGetString(be, "secondCompetitorScore")?.let {
                        putString(QuickLookTarget.EXTRA_SPORTS_SCORE2, it)
                    }
                }
            }
        }

        val title = header?.title?.toString() ?: base?.title?.toString()
        val subtitle = header?.subtitle?.toString() ?: base?.subtitle?.toString()
        if (title.isNullOrEmpty() &&
            !extras.containsKey(QuickLookTarget.EXTRA_SPORTS_SCORE1)) return null

        parseTitleForTeams(title, extras)

        return QuickLookTarget.Builder(
                "axql_gss_sports_${st.smartspaceTargetId}",
                QuickLookTarget.TYPE_SPORTS,
            )
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIconBytes(header?.icon?.let { iconToBytes(it) })
            .setScore(st.score)
            .setCreationTime(st.creationTimeMillis)
            .setExpiryTime(st.expiryTimeMillis)
            .setPrimaryAction(buildAction(st, base, header))
            .setExtras(extras)
            .build()
    }

    private fun convertHeadToHeadTarget(
        st: SmartspaceTarget,
        h2h: HeadToHeadTemplateData,
    ): QuickLookTarget? {
        val title = h2h.headToHeadTitle?.text?.toString()
            ?: st.headerAction?.title?.toString()
        val team1Text = h2h.headToHeadFirstCompetitorText?.text?.toString() ?: ""
        val team2Text = h2h.headToHeadSecondCompetitorText?.text?.toString() ?: ""

        val extras = Bundle().apply {
            putString(QuickLookTarget.EXTRA_SPORTS_STATUS_DETAIL, title ?: "")
            h2h.headToHeadFirstCompetitorIcon?.icon?.let { icon ->
                iconToBytes(icon)?.let { putByteArray(QuickLookTarget.EXTRA_SPORTS_TEAM1_ICON, it) }
            }
            h2h.headToHeadSecondCompetitorIcon?.icon?.let { icon ->
                iconToBytes(icon)?.let { putByteArray(QuickLookTarget.EXTRA_SPORTS_TEAM2_ICON, it) }
            }
            parseCompetitorText(team1Text, team2Text, this)
        }

        if (!extras.containsKey(QuickLookTarget.EXTRA_SPORTS_TEAM1_NAME) &&
            team1Text.isEmpty() && team2Text.isEmpty()) return null

        val header = st.headerAction
        val base = st.baseAction

        return QuickLookTarget.Builder(
                "axql_gss_sports_${st.smartspaceTargetId}",
                QuickLookTarget.TYPE_SPORTS,
            )
            .setTitle(title)
            .setSubtitle(header?.subtitle?.toString() ?: base?.subtitle?.toString())
            .setIconBytes(header?.icon?.let { iconToBytes(it) })
            .setScore(st.score)
            .setCreationTime(st.creationTimeMillis)
            .setExpiryTime(st.expiryTimeMillis)
            .setPrimaryAction(buildAction(st, base, header))
            .setExtras(extras)
            .build()
    }

    private fun parseCompetitorText(team1: String, team2: String, extras: Bundle) {
        val scoreRegex = Regex("""(\D*?)\s*(\d+)\s*$""")
        val m1 = scoreRegex.find(team1)
        val m2 = scoreRegex.find(team2)
        if (m1 != null && m2 != null) {
            extras.putString(QuickLookTarget.EXTRA_SPORTS_TEAM1_NAME, m1.groupValues[1].trim())
            extras.putString(QuickLookTarget.EXTRA_SPORTS_SCORE1, m1.groupValues[2])
            extras.putString(QuickLookTarget.EXTRA_SPORTS_TEAM2_NAME, m2.groupValues[1].trim())
            extras.putString(QuickLookTarget.EXTRA_SPORTS_SCORE2, m2.groupValues[2])
        } else {
            extras.putString(QuickLookTarget.EXTRA_SPORTS_TEAM1_NAME, team1)
            extras.putString(QuickLookTarget.EXTRA_SPORTS_TEAM2_NAME, team2)
        }
    }

    private fun parseTitleForTeams(title: String?, extras: Bundle) {
        if (title.isNullOrEmpty()) return
        if (extras.containsKey(QuickLookTarget.EXTRA_SPORTS_TEAM1_NAME)) return
        val vsMatch = Regex("""(.+?)\s+(?:vs\.?|v)\s+(.+)""", RegexOption.IGNORE_CASE).find(title)
        if (vsMatch != null) {
            extras.putString(QuickLookTarget.EXTRA_SPORTS_TEAM1_NAME, vsMatch.groupValues[1].trim())
            extras.putString(QuickLookTarget.EXTRA_SPORTS_TEAM2_NAME, vsMatch.groupValues[2].trim())
        }
    }

    private fun safeGetString(bundle: Bundle, key: String): String? {
        return try {
            bundle.getString(key)
        } catch (_: Exception) { null }
    }

    private fun safeGetBitmap(bundle: Bundle, key: String): Bitmap? {
        return try {
            bundle.getParcelable(key, Bitmap::class.java)
        } catch (_: Exception) { null }
    }

    private fun serializeActionChips(bundle: Bundle, chips: List<SmartspaceAction>) {
        bundle.putInt(QuickLookTarget.EXTRA_SMARTSPACE_ACTION_CHIPS_COUNT, chips.size)
        for (i in chips.indices) {
            val chip = chips[i]
            val prefix = "${QuickLookTarget.EXTRA_SMARTSPACE_ACTION_CHIP_PREFIX}${i}_"
            val chipBundle = Bundle().apply {
                chip.title?.toString()?.let { putString("title", it) }
                chip.subtitle?.toString()?.let { putString("subtitle", it) }
                chip.contentDescription?.toString()?.let { putString("content_description", it) }
                chip.icon?.let { icon ->
                    iconToBytes(icon)?.let { putByteArray("icon_bytes", it) }
                }
                chip.pendingIntent?.let { putParcelable("pending_intent", it) }
                chip.intent?.let { putParcelable("intent", it) }
                chip.extras?.let { putBundle("extras", sanitizeExtras(it)) }
            }
            bundle.putBundle("${prefix}data", chipBundle)
        }
    }

    private fun serializeIconGrid(bundle: Bundle, grid: List<SmartspaceAction>) {
        bundle.putInt(QuickLookTarget.EXTRA_SMARTSPACE_ICON_GRID_COUNT, grid.size)
        for (i in grid.indices) {
            val item = grid[i]
            val prefix = "${QuickLookTarget.EXTRA_SMARTSPACE_ICON_GRID_PREFIX}${i}_"
            val gridBundle = Bundle().apply {
                item.title?.toString()?.let { putString("title", it) }
                item.subtitle?.toString()?.let { putString("subtitle", it) }
                item.contentDescription?.toString()?.let { putString("content_description", it) }
                item.icon?.let { icon ->
                    iconToBytes(icon)?.let { putByteArray("icon_bytes", it) }
                }
                item.pendingIntent?.let { putParcelable("pending_intent", it) }
                item.intent?.let { putParcelable("intent", it) }
                item.extras?.let { putBundle("extras", sanitizeExtras(it)) }
            }
            bundle.putBundle("${prefix}data", gridBundle)
        }
    }

    private fun sanitizeExtras(source: Bundle): Bundle {
        val result = Bundle()
        try {
            source.classLoader = javaClass.classLoader
        } catch (_: Exception) {}
        val keys = try {
            source.keySet()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unparcel extras, forwarding raw bundle", e)
            return source
        }
        for (key in keys) {
            try {
                when (val value = source.get(key)) {
                    is Bitmap ->
                        bitmapToBytes(value)?.let { result.putByteArray("${key}_bytes", it) }
                    is String -> result.putString(key, value)
                    is Int -> result.putInt(key, value)
                    is Long -> result.putLong(key, value)
                    is Float -> result.putFloat(key, value)
                    is Double -> result.putDouble(key, value)
                    is Boolean -> result.putBoolean(key, value)
                    is Bundle -> result.putBundle(key, sanitizeExtras(value))
                    is ByteArray -> result.putByteArray(key, value)
                    is IntArray -> result.putIntArray(key, value)
                    is android.os.Parcelable -> result.putParcelable(key, value)
                    else -> {}
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sanitize extra key: $key", e)
            }
        }
        return result
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
            source.intent?.let { builder.setIntent(it) }
        } else if (source.intent != null) {
            val pi = PendingIntent.getActivity(
                context, st.smartspaceTargetId.hashCode(), source.intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.setPendingIntent(pi)
            builder.setIntent(source.intent)
        }

        return builder.build()
    }

    private fun iconToBytes(icon: Icon): ByteArray? {
        return try {
            val drawable = icon.loadDrawable(context) ?: return null
            val bitmap = drawable.toBitmap()
            val bytes = bitmapToBytes(bitmap)
            bitmap.recycle()
            bytes
        } catch (e: Exception) {
            Log.w(TAG, "Failed to convert icon to bytes", e)
            null
        }
    }

    private fun bitmapToBytes(bitmap: Bitmap): ByteArray? {
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compress bitmap", e)
            null
        }
    }

    companion object {
        private const val TAG = "GoogleSmartspaceProv"
        private const val UI_SURFACE = "lockscreen"
        private const val FEATURE_WEATHER = 1
        private const val FEATURE_SPORTS = 9
        private const val FEATURE_SHOPPING_LIST = 13
        private const val FEATURE_MEDIA = 15
        private const val FEATURE_MEDIA_INTERNAL = 34
        private const val FEATURE_COMBINATION = -1
        private const val FEATURE_COMBINATION_AT_STORE = -2
    }
}
