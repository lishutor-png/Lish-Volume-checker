package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analysis_history")
data class AnalysisHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val testFileName: String,
    val testDurationMs: Long,
    val testSampleRate: Int,
    val testChannels: Int,
    val testLufs: Double,
    val testTruePeak: Double,
    val refFileName: String,
    val refLufs: Double,
    val refTruePeak: Double,
    val diffLufs: Double,
    val gainRecommendationDb: Double,
    val status: String, // "LEBIH_KERAS", "LEBIH_PELAN", "SEIMBANG"
    val timestamp: Long = System.currentTimeMillis()
)
