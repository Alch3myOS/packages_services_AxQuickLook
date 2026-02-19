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

import android.util.Log
import com.android.axion.quicklook.DataAggregator
import com.android.axion.quicklook.IAxQuickLookService
import com.android.axion.quicklook.IQuickLookCallback
import com.android.axion.quicklook.QuickLookTarget
import com.android.axion.quicklook.provider.ProviderRegistry

class QuickLookServiceImpl(
    private val aggregator: DataAggregator,
    private val providerRegistry: ProviderRegistry,
) : IAxQuickLookService.Stub() {

    override fun registerCallback(callback: IQuickLookCallback?) {
        if (callback == null) {
            Log.w(TAG, "registerCallback: callback is null")
            return
        }
        aggregator.registerCallback(callback)
    }

    override fun unregisterCallback(callback: IQuickLookCallback?) {
        if (callback == null) {
            Log.w(TAG, "unregisterCallback: callback is null")
            return
        }
        aggregator.unregisterCallback(callback)
    }

    override fun getCurrentTargets(): List<QuickLookTarget> = aggregator.getCurrentTargets()

    override fun requestUpdate() {
        aggregator.requestUpdate()
    }

    override fun isProviderEnabled(providerType: Int): Boolean =
        providerRegistry.isProviderEnabled(providerType)

    companion object {
        private const val TAG = "QuickLookServiceImpl"
    }
}
