package io.morgan.mio.data.media

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.morgan.mio.core.common.di.AccountScope

public interface ValidMetroContract

@ContributesBinding(AccountScope::class)
@Inject
public class ValidMetroContribution(
    private val dependency: ValidMetroDependency,
) : ValidMetroContract

@Inject
public class ValidMetroDependency
