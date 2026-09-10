package io.morgan.mio.domain.progress

import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class ProgressRulesTest {
    private val timestamp = Instant.parse("2026-08-23T12:00:00Z")

    @Test
    fun everyUnitUsesTheEntryUnitAndCurrentOnlyEligibility() {
        ProgressUnit.entries.forEach { unit ->
            val current = entry(unit = unit, progress = 3, total = 12)
            val increase =
                assertInstanceOf(
                    ProgressRules.Decision.Change::class.java,
                    ProgressRules.decide(current, ProgressDirection.INCREASE),
                )
            val decrease =
                assertInstanceOf(
                    ProgressRules.Decision.Change::class.java,
                    ProgressRules.decide(current, ProgressDirection.DECREASE),
                )

            assertEquals(4, increase.progress.value)
            assertEquals(2, decrease.progress.value)
            assertEquals(CollectionStatus.CURRENT, increase.status)
            assertEquals(CollectionStatus.CURRENT, decrease.status)
            assertEquals(unit, current.progressUnit)

            listOf(
                CollectionStatus.PLANNED,
                CollectionStatus.PAUSED,
                CollectionStatus.COMPLETED,
                CollectionStatus.DROPPED,
                CollectionStatus.REPEATING,
            ).forEach { status ->
                val inactive = current.copy(status = status)
                assertEquals(
                    ProgressWriteResult.Rejection.INELIGIBLE,
                    assertInstanceOf(
                        ProgressRules.Decision.Rejected::class.java,
                        ProgressRules.decide(inactive, ProgressDirection.INCREASE),
                    ).reason,
                )
                assertEquals(
                    ProgressWriteResult.Rejection.INELIGIBLE,
                    assertInstanceOf(
                        ProgressRules.Decision.Rejected::class.java,
                        ProgressRules.decide(inactive, ProgressDirection.DECREASE),
                    ).reason,
                )
            }
        }
    }

    @Test
    fun boundariesKnownUnknownAndFinalCompletionAreExplicit() {
        ProgressUnit.entries.forEach { unit ->
            assertEquals(
                ProgressWriteResult.Rejection.AT_BOUNDARY,
                rejectedReason(entry(unit = unit, progress = 0, total = 12), ProgressDirection.DECREASE),
            )
            assertEquals(
                ProgressWriteResult.Rejection.AT_BOUNDARY,
                rejectedReason(entry(unit = unit, progress = 12, total = 12), ProgressDirection.INCREASE),
            )
            assertEquals(
                ProgressWriteResult.Rejection.AT_BOUNDARY,
                rejectedReason(entry(unit = unit, progress = 13, total = 12), ProgressDirection.INCREASE),
            )
            assertEquals(
                ProgressWriteResult.Rejection.AT_BOUNDARY,
                rejectedReason(entry(unit = unit, progress = Int.MAX_VALUE, total = null), ProgressDirection.INCREASE),
            )

            val final =
                assertInstanceOf(
                    ProgressRules.Decision.Change::class.java,
                    ProgressRules.decide(entry(unit = unit, progress = 11, total = 12), ProgressDirection.INCREASE),
                )
            assertEquals(12, final.progress.value)
            assertEquals(CollectionStatus.COMPLETED, final.status)
            assertEquals(unit, entry(unit = unit, progress = 11, total = 12).progressUnit)

            val unknown =
                assertInstanceOf(
                    ProgressRules.Decision.Change::class.java,
                    ProgressRules.decide(entry(unit = unit, progress = 41, total = null), ProgressDirection.INCREASE),
                )
            assertEquals(42, unknown.progress.value)
            assertEquals(CollectionStatus.CURRENT, unknown.status)

            val staleTotalCorrection =
                assertInstanceOf(
                    ProgressRules.Decision.Change::class.java,
                    ProgressRules.decide(entry(unit = unit, progress = 13, total = 12), ProgressDirection.DECREASE),
                )
            assertEquals(12, staleTotalCorrection.progress.value)
            assertEquals(CollectionStatus.CURRENT, staleTotalCorrection.status)
        }
    }

    @Test
    fun oneStepDoesNotChangeUnitOrImplicitlyChangeStatusOnDecrease() {
        ProgressUnit.entries.forEach { unit ->
            val entry = entry(unit = unit, progress = 8, total = 12)
            val decrease =
                assertInstanceOf(
                    ProgressRules.Decision.Change::class.java,
                    ProgressRules.decide(entry, ProgressDirection.DECREASE),
                )
            assertEquals(7, decrease.progress.value)
            assertEquals(CollectionStatus.CURRENT, decrease.status)
            assertTrue(entry.progressUnit == unit)
        }
    }

    private fun entry(
        unit: ProgressUnit = ProgressUnit.EPISODE,
        progress: Int,
        total: Int?,
        status: CollectionStatus = CollectionStatus.CURRENT,
    ): CollectionEntry =
        CollectionEntry(
            id = CollectionEntryId("entry-1"),
            accountId = AccountId("account-1"),
            mediaId = MediaId("media-1"),
            status = status,
            progress = ProgressValue(progress),
            progressUnit = unit,
            total = total?.let(::UnitTotal),
            updatedAt = timestamp,
        )

    private fun rejectedReason(
        entry: CollectionEntry,
        direction: ProgressDirection,
    ): ProgressWriteResult.Rejection =
        assertInstanceOf(
            ProgressRules.Decision.Rejected::class.java,
            ProgressRules.decide(entry, direction),
        ).reason
}
