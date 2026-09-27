/*
 * Copyright (C) 2025 AxionOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.axion.widgets.utils

import android.content.Context
import android.graphics.drawable.Drawable

object WeatherUtils {

    private val CANDIDATE_CLASSES = listOf(
        "com.android.internal.util.android.OmniJawsClient",
        "com.android.internal.util.omni.OmniJawsClient"
    )

    fun getWeatherIcon(context: Context, conditionCode: Int): Drawable? {
        for (className in CANDIDATE_CLASSES) {
            val result = runCatching {
                val clazz = Class.forName(className)
                val getMethod = clazz.getMethod("get")
                val instance = getMethod.invoke(null)
                val getImageMethod = clazz.getMethod(
                    "getWeatherConditionImage",
                    Context::class.java,
                    Int::class.javaPrimitiveType
                )
                getImageMethod.invoke(instance, context, conditionCode) as? Drawable
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }
}
