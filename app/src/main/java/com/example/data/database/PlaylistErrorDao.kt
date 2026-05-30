package com.example.data.database

import androidx.room.*
import com.example.data.model.PlaylistError
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistErrorDao {
    @Query("SELECT * FROM playlist_errors WHERE playlistId = :playlistId ORDER BY id ASC")
    fun getErrorsByPlaylist(playlistId: Int): Flow<List<PlaylistError>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertErrors(errors: List<PlaylistError>)

    @Transaction
    suspend fun insertErrorsInChunks(errors: List<PlaylistError>) {
        errors.chunked(150).forEach { chunk ->
            insertErrors(chunk)
        }
    }

    @Query("DELETE FROM playlist_errors WHERE playlistId = :playlistId")
    suspend fun deleteErrorsByPlaylist(playlistId: Int)
}
