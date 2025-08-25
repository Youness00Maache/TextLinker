package com.xfire.textlinker

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "from_server", defaultValue = "0") val fromServer: Boolean = false
)
