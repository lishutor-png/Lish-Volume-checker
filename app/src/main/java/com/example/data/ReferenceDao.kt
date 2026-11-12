package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReferenceDao {
    @Query("SELECT * FROM reference_track WHERE id = 1 LIMIT 1")
    fun getReferenceTrack(): Flow<ReferenceTrackEntity?>

    @Query("SELECT * FROM reference_track WHERE id = 1 LIMIT 1")
    suspend fun getReferenceTrackOnce(): ReferenceTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReferenceTrack(track: ReferenceTrackEntity)

    @Query("DELETE FROM reference_track WHERE id = 1")
    suspend fun deleteReferenceTrack()
}
