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

import android.os.Handler
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.util.Log
import com.android.axion.quicklook.provider.QuickLookProvider
import java.io.PrintWriter
import java.util.concurrent.CopyOnWriteArrayList

class DataAggregator(private val workerHandler: Handler) : QuickLookProvider.UpdateListener {

    private val callbacks = RemoteCallbackList<IQuickLookCallback>()
    private val providers = CopyOnWriteArrayList<QuickLookProvider>()
    @Volatile private var currentTargets: List<QuickLookTarget> = emptyList()

    fun addProvider(provider: QuickLookProvider) {
        providers.add(provider)
        provider.addUpdateListener(this)
    }

    fun removeProvider(provider: QuickLookProvider) {
        provider.removeUpdateListener(this)
        providers.remove(provider)
    }

    override fun onDataChanged() {
        workerHandler.post(::aggregateAndBroadcast)
    }

    fun requestUpdate() {
        workerHandler.post(::aggregateAndBroadcast)
    }

    fun registerCallback(callback: IQuickLookCallback) {
        callbacks.register(callback)
        try {
            callback.onTargetsUpdated(currentTargets)
        } catch (e: RemoteException) {
            Log.w(TAG, "Failed to send initial targets to callback", e)
        }
    }

    fun unregisterCallback(callback: IQuickLookCallback) {
        callbacks.unregister(callback)
    }

    fun getCurrentTargets(): List<QuickLookTarget> = ArrayList(currentTargets)

    private fun aggregateAndBroadcast() {
        val allTargets = mutableListOf<QuickLookTarget>()

        for (provider in providers) {
            if (!provider.isEnabled) continue
            provider.getTargets().let { allTargets.addAll(it) }
        }

        val now = System.currentTimeMillis()
        allTargets.removeAll { it.expiryTime > 0 && it.expiryTime < now }

        allTargets.sortWith(
            compareBy<QuickLookTarget> {
                    if (it.targetType == QuickLookTarget.TYPE_WEATHER) 0 else 1
                }
                .thenByDescending { it.score }
                .thenByDescending { it.creationTime }
        )

        val capped =
            if (allTargets.size > MAX_TARGETS) {
                allTargets.subList(0, MAX_TARGETS).toList()
            } else {
                allTargets.toList()
            }

        if (capped == currentTargets) return

        currentTargets = capped
        broadcastTargets(currentTargets)
    }

    private fun broadcastTargets(targets: List<QuickLookTarget>) {
        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                callbacks.getBroadcastItem(i).onTargetsUpdated(targets)
            } catch (e: RemoteException) {
                Log.w(TAG, "Failed to send targets to callback", e)
            }
        }
        callbacks.finishBroadcast()
    }

    fun dump(pw: PrintWriter) {
        pw.println()
        pw.println("DataAggregator:")
        pw.println("  Registered providers: ${providers.size}")
        val cbCount = callbacks.beginBroadcast()
        callbacks.finishBroadcast()
        pw.println("  Registered callbacks: $cbCount")
        val targets = currentTargets
        pw.println("  Current targets (${targets.size}):")
        for (target in targets) {
            pw.println("    $target")
        }
    }

    companion object {
        private const val TAG = "DataAggregator"
        private const val MAX_TARGETS = 10
    }
}
