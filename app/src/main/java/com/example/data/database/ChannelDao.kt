package com.example.data.database

import androidx.room.*
import com.example.data.model.ChannelItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId")
    fun getChannelsByPlaylist(playlistId: Int): Flow<List<ChannelItem>>

    @Query("SELECT * FROM channels WHERE superCategory = :superCategory")
    fun getChannelsBySuperCategory(superCategory: String): Flow<List<ChannelItem>>

    @Query("SELECT * FROM channels")
    fun getAllChannels(): Flow<List<ChannelItem>>

    @Query("SELECT * FROM channels WHERE isFavorite = 1")
    fun getFavoriteChannels(): Flow<List<ChannelItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelItem>)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsByPlaylist(playlistId: Int)

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Int, isFavorite: Boolean)

    @Query("SELECT DISTINCT category FROM channels WHERE superCategory = :superCategory")
    fun getCategoriesBySuperCategory(superCategory: String): Flow<List<String>>
}
