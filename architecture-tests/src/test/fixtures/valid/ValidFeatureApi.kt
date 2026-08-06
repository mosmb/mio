package io.morgan.mio.feature.today.api

import androidx.navigation3.runtime.NavKey
import io.morgan.mio.domain.model.ValidDomain

class ValidFeatureApi(
    val model: ValidDomain,
) : NavKey
