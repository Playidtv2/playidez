package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist_errors")
data class PlaylistError(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Int,
    val lineNum: Int,
    val lineContent: String,
    val errorMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)
