package io.morgan.mio.domain.progress

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.ZERO

public class ObserveProgressUndoUseCase(
    private val repository: ProgressRepository,
    private val clock: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    public operator fun invoke(): Flow<ProgressAction?> =
        repository.observeLatestAction().flatMapLatest { action ->
            flow {
                if (action == null || action.undoneAt != null) {
                    emit(null)
                    return@flow
                }

                val initialNow = clock.now()
                if (initialNow >= action.undoUntil) {
                    emit(null)
                    return@flow
                }

                emit(action)
                while (true) {
                    val remaining = action.undoUntil - clock.now()
                    if (remaining <= ZERO) break
                    delay(remaining)
                }
                emit(null)
            }
        }
}
