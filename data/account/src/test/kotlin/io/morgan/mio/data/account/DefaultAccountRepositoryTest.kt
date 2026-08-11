package io.morgan.mio.data.account

import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.data.account.local.RoomAccountLocalDataSource
import io.morgan.mio.data.account.mapper.AccountProviderIdInput
import io.morgan.mio.domain.account.Account
import io.morgan.mio.domain.account.ConnectedProvider
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.ImageReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class DefaultAccountRepositoryTest {
    private lateinit var database: MioDatabase
    private lateinit var localDataSource: RoomAccountLocalDataSource

    @Before
    fun setUp() {
        database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
        localDataSource = RoomAccountLocalDataSource(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observationMapsRoomStateAndRefreshIsSeparate() =
        runTest {
            var refreshCount = 0
            val repository =
                DefaultAccountRepository(
                    localDataSource = localDataSource,
                    refreshRequester = AccountRefreshRequester { refreshCount++ },
                )

            assertNull(repository.observeSelectedAccount().first())
            assertEquals(0, refreshCount)

            val account = account("account-1")
            localDataSource.replaceSelectedAccount(
                account,
                AccountProviderIdInput(StoredConnectedProvider.ANILIST, "remote-1"),
            )

            assertEquals(account, repository.observeSelectedAccount().first())
            repository.refreshSelectedAccount()
            assertEquals(1, refreshCount)
            assertEquals(account, repository.observeSelectedAccount().first())
        }

    @Test
    fun replacingTheSelectedAccountRemovesTheOldMapping() =
        runTest {
            val repository =
                DefaultAccountRepository(
                    localDataSource = localDataSource,
                    refreshRequester = AccountRefreshRequester {},
                )

            localDataSource.replaceSelectedAccount(
                account("account-1"),
                AccountProviderIdInput(StoredConnectedProvider.ANILIST, "remote-1"),
            )
            localDataSource.replaceSelectedAccount(
                account("account-2"),
                AccountProviderIdInput(StoredConnectedProvider.MY_ANIME_LIST, "remote-2"),
            )

            assertEquals(account("account-2"), repository.observeSelectedAccount().first())
            assertEquals(1, countRows("selected_account"))
            assertEquals(1, countRows("account_provider_id"))
            assertEquals(0, countMatchingRemoteId("remote-1"))
            assertEquals(1, countMatchingRemoteId("remote-2"))
        }

    private fun account(id: String): Account =
        Account(
            id = AccountId(id),
            provider = if (id == "account-1") ConnectedProvider.ANILIST else ConnectedProvider.MY_ANIME_LIST,
            displayName = "Display $id",
            avatar = ImageReference("image:$id"),
        )

    private fun countRows(tableName: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tableName").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun countMatchingRemoteId(remoteId: String): Int =
        database.openHelper
            .readableDatabase
            .query("SELECT COUNT(*) FROM account_provider_id WHERE remote_account_id = '$remoteId'")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
}
