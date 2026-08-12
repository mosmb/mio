package io.morgan.mio.feature.today.api

import androidx.navigation3.runtime.NavKey
import io.morgan.mio.domain.model.ValidDomain
import kotlinx.serialization.Serializable

@Serializable
public data class NavKeyWithAggregate(
    public val aggregate: ValidDomain,
) : NavKey
