package io.morgan.mio.feature.today.impl

import io.morgan.mio.domain.model.ValidDomain
import io.morgan.mio.feature.today.api.TodayDestination

class ValidFeatureImpl(
    private val destination: TodayDestination,
    private val model: ValidDomain,
)
