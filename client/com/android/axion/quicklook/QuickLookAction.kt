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

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable

class QuickLookAction
private constructor(
    val id: String,
    val label: String?,
    val pendingIntent: PendingIntent?,
    val intent: Intent?,
    val extras: Bundle?,
) : Parcelable {

    private constructor(
        parcel: Parcel
    ) : this(
        id = parcel.readString()!!,
        label = parcel.readString(),
        pendingIntent = parcel.readTypedObject(PendingIntent.CREATOR),
        intent = parcel.readTypedObject(Intent.CREATOR),
        extras = parcel.readBundle(QuickLookAction::class.java.classLoader),
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(id)
        dest.writeString(label)
        dest.writeTypedObject(pendingIntent, flags)
        dest.writeTypedObject(intent, flags)
        dest.writeBundle(extras)
    }

    override fun describeContents() = 0

    companion object {
        @JvmField
        val CREATOR =
            object : Parcelable.Creator<QuickLookAction> {
                override fun createFromParcel(source: Parcel) = QuickLookAction(source)

                override fun newArray(size: Int) = arrayOfNulls<QuickLookAction>(size)
            }
    }

    class Builder(private val id: String) {
        private var label: String? = null
        private var pendingIntent: PendingIntent? = null
        private var intent: Intent? = null
        private var extras: Bundle? = null

        fun setLabel(label: String?) = apply { this.label = label }

        fun setPendingIntent(pendingIntent: PendingIntent?) = apply {
            this.pendingIntent = pendingIntent
        }

        fun setIntent(intent: Intent?) = apply { this.intent = intent }

        fun setExtras(extras: Bundle?) = apply { this.extras = extras }

        fun build() = QuickLookAction(id, label, pendingIntent, intent, extras)
    }
}
