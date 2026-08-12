package io.morgan.mio.feature.today.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
public data object FeatureApiImportsJson : NavKey

public val featureApiJson = Json
