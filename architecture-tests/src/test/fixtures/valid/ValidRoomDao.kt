package io.morgan.mio.core.database.fixture

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ValidRoomDao {
    @Query("SELECT 1")
    fun read(): Int
}
