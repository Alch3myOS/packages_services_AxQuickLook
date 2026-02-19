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

package com.android.axion.quicklook.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import com.android.axion.quicklook.DataAggregator
import com.android.axion.quicklook.provider.ProviderRegistry
import java.io.FileDescriptor
import java.io.PrintWriter

class AxQuickLookService : Service() {

    private lateinit var binder: QuickLookServiceImpl
    private lateinit var providerRegistry: ProviderRegistry
    private lateinit var aggregator: DataAggregator
    private lateinit var workerThread: HandlerThread

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AxQuickLook service starting")

        workerThread = HandlerThread("AxQuickLookWorker").also { it.start() }
        val workerHandler = Handler(workerThread.looper)

        aggregator = DataAggregator(workerHandler)
        providerRegistry = ProviderRegistry(this, workerHandler, aggregator)
        providerRegistry.initialize()
        binder = QuickLookServiceImpl(aggregator, providerRegistry)

        Log.i(TAG, "AxQuickLook service started")
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "Client bound: $intent")
        return binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.d(TAG, "Client unbound: $intent")
        return true
    }

    override fun onRebind(intent: Intent) {
        Log.d(TAG, "Client rebound: $intent")
    }

    override fun dump(fd: FileDescriptor, pw: PrintWriter, args: Array<out String>?) {
        pw.println("AxQuickLook Service Dump")
        pw.println("========================")
        if (::aggregator.isInitialized) aggregator.dump(pw)
        if (::providerRegistry.isInitialized) providerRegistry.dump(pw)
    }

    override fun onDestroy() {
        Log.i(TAG, "AxQuickLook service stopping")
        if (::providerRegistry.isInitialized) providerRegistry.shutdown()
        if (::workerThread.isInitialized) workerThread.quitSafely()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AxQuickLookService"
    }
}
