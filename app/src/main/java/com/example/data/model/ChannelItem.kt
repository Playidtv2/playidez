package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Int,
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val category: String, // E.g., "Thai News", "Action Movie"
    val superCategory: String, // "TV", "Movie", "Series"
    val isFavorite: Boolean = false
)
