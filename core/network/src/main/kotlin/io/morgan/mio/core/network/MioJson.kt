package io.morgan.mio.core.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
public val MioJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        isLenient = false
        coerceInputValues = false
        allowStructuredMapKeys = false
        exceptionsWithDebugInfo = false
    }
