package io.morgan.mio.core.database.fixture

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "valid_room_entity")
class ValidRoomEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
)
