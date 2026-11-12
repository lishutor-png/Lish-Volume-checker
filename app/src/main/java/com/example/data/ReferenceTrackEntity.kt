package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reference_track")
data class ReferenceTrackEntity(
    @PrimaryKey
    val id: Int = 1,
    val fileName: String,
    val fileUri: String,
    val durationMs: Long,
    val sampleRate: Int,
    val channelCount: Int,
    val integratedLufs: Double,
    val truePeakDbtp: Double,
    val timestamp: Long = System.currentTimeMillis()
)
