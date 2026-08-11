package io.morgan.mio.core.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class MioDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MioDatabase::class.java,
            emptyList(),
        )

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun versionOneSchemaIsExportedAndCurrentBuilderOpensIt() {
        helper.createDatabase(TEST_DATABASE_NAME, MioDatabase.VERSION).close()

        val current = MioDatabaseFactory.create(context, TEST_DATABASE_NAME)
        current.close()

        assertEquals(1, MioDatabase.VERSION)
        assertTrue(MioMigrations.ALL.isEmpty())
    }

    @Test
    fun migrationRegistryStartsAtVersionOneWithoutAZeroToOneMigration() {
        assertEquals(1, MioDatabase.VERSION)
        assertTrue(MioMigrations.ALL.isEmpty())
        assertEquals(0, MioMigrations.ALL.size)
    }

    private companion object {
        const val TEST_DATABASE_NAME: String = "mio-migration-test.db"
    }
}
