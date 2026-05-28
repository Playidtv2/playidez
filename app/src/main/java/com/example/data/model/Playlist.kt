package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String,
    val type: String, // "m3u" or "xtream"
    val username: String? = null,
    val password: String? = null,
    val host: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)
