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
import android.os.Handler
import android.provider.Settings
import com.android.axion.quicklook.QuickLookTarget
import java.util.concurrent.CopyOnWriteArraySet

abstract class QuickLookProvider(
    protected val context: Context,
    protected val workerHandler: Handler,
) {

    interface UpdateListener {
        fun onDataChanged()
    }

    private val listeners = CopyOnWriteArraySet<UpdateListener>()

    abstract val providerType: Int

    abstract val settingsKey: String?

    abstract val priority: Int

    abstract fun getTargets(): List<QuickLookTarget>

    abstract fun start()

    abstract fun shutdown()

    fun addUpdateListener(listener: UpdateListener) {
        listeners.add(listener)
    }

    fun removeUpdateListener(listener: UpdateListener) {
        listeners.remove(listener)
    }

    open val isEnabled: Boolean
        get() {
            val key = settingsKey ?: return true
            return Settings.Secure.getInt(context.contentResolver, key, 1) == 1
        }

    protected fun notifyUpdate() {
        for (l in listeners) {
            l.onDataChanged()
        }
    }
}
