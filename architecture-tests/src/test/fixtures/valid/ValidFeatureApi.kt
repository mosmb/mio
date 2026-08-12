package io.morgan.mio.feature.today.api

import androidx.navigation3.runtime.NavKey
import io.morgan.mio.domain.model.ValidMediaId
import kotlinx.serialization.Serializable

@Serializable
class ValidFeatureApi(
    val mediaId: ValidMediaId,
) : NavKey
