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
import android.os.Handler
import android.util.Log
import com.android.axion.quicklook.DataAggregator
import com.android.axion.quicklook.util.SettingsHelper
import java.io.PrintWriter

class ProviderRegistry(
    private val context: Context,
    private val workerHandler: Handler,
    private val aggregator: DataAggregator,
) {

    private val providers = mutableListOf<QuickLookProvider>()
    private var settingsObserver: ContentObserver? = null

    fun initialize() {
        Log.i(TAG, "Initializing providers...")

        registerProvider(WeatherProvider(context, workerHandler))
        registerProvider(CalendarProvider(context, workerHandler))
        registerProvider(AlarmProvider(context, workerHandler))
        registerProvider(MediaProvider(context, workerHandler))
        registerProvider(NowPlayingProvider(context, workerHandler))
        registerProvider(SmartspacerBridgeProvider(context, workerHandler))
        registerProvider(GoogleSmartspaceProvider(context, workerHandler))

        for (provider in providers) {
            try {
                provider.start()
                Log.i(TAG, "Started provider: ${provider.javaClass.simpleName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start provider: ${provider.javaClass.simpleName}", e)
            }
        }

        settingsObserver =
            SettingsHelper.registerSettingsObserver(context, workerHandler) {
                aggregator.requestUpdate()
            }
    }

    private fun registerProvider(provider: QuickLookProvider) {
        providers.add(provider)
        aggregator.addProvider(provider)
    }

    fun isProviderEnabled(providerType: Int): Boolean =
        providers.any { it.providerType == providerType && it.isEnabled }

    fun dump(pw: PrintWriter) {
        pw.println()
        pw.println("ProviderRegistry:")
        pw.println("  Providers (${providers.size}):")
        for (provider in providers) {
            pw.println(
                "    ${provider.javaClass.simpleName}" +
                    " [type=${provider.providerType}" +
                    ", priority=${provider.priority}" +
                    ", enabled=${provider.isEnabled}" +
                    ", targets=${provider.getTargets().size}]"
            )
        }
    }

    fun shutdown() {
        settingsObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            settingsObserver = null
        }

        for (provider in providers) {
            try {
                aggregator.removeProvider(provider)
                provider.shutdown()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to shutdown provider: ${provider.javaClass.simpleName}", e)
            }
        }
        providers.clear()
    }

    companion object {
        private const val TAG = "ProviderRegistry"
    }
}
