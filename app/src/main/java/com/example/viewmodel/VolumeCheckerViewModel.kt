package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioAnalysisResult
import com.example.audio.AudioDecoder
import com.example.data.AnalysisHistoryEntity
import com.example.data.AppDatabase
import com.example.data.ReferenceTrackEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.round

enum class LoudnessStatus {
    LOUDER,   // Lebih Keras
    QUIETER,  // Lebih Pelan
    MATCHED   // Seimbang
}

data class ComparisonData(
    val diffLufs: Double,
    val gainRecommendationDb: Double,
    val status: LoudnessStatus,
    val testTruePeak: Double,
    val refTruePeak: Double
)

data class AnalysisUiState(
    val isAnalyzingRef: Boolean = false,
    val refProgress: Float = 0f,
    val isAnalyzingTest: Boolean = false,
    val testProgress: Float = 0f,
    val testResult: AudioAnalysisResult? = null,
    val errorMessage: String? = null
)

class VolumeCheckerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val referenceDao = database.referenceDao()
    private val historyDao = database.historyDao()
    private val audioDecoder = AudioDecoder(application)

    val savedReference: StateFlow<ReferenceTrackEntity?> = referenceDao.getReferenceTrack()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val historyList: StateFlow<List<AnalysisHistoryEntity>> = historyDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private var refJob: Job? = null
    private var testJob: Job? = null

    fun analyzeReference(uri: Uri) {
        refJob?.cancel()
        refJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnalyzingRef = true,
                refProgress = 0f,
                errorMessage = null
            )
            try {
                val result = audioDecoder.analyzeAudio(uri) { progress ->
                    _uiState.value = _uiState.value.copy(refProgress = progress)
                }

                val entity = ReferenceTrackEntity(
                    id = 1,
                    fileName = result.fileName,
                    fileUri = uri.toString(),
                    durationMs = result.durationMs,
                    sampleRate = result.sampleRate,
                    channelCount = result.channelCount,
                    integratedLufs = result.integratedLufs,
                    truePeakDbtp = result.truePeakDbtp
                )
                referenceDao.saveReferenceTrack(entity)

                // If a test result is already present, record new comparison to history
                val currentTest = _uiState.value.testResult
                if (currentTest != null) {
                    recordComparison(currentTest, entity)
                }

                _uiState.value = _uiState.value.copy(
                    isAnalyzingRef = false,
                    refProgress = 1f
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzingRef = false,
                    errorMessage = e.localizedMessage ?: "Terjadi kesalahan saat memproses file referensi."
                )
            }
        }
    }

    fun deleteReference() {
        viewModelScope.launch {
            referenceDao.deleteReferenceTrack()
        }
    }

    fun analyzeTestTrack(uri: Uri) {
        testJob?.cancel()
        testJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnalyzingTest = true,
                testProgress = 0f,
                errorMessage = null
            )
            try {
                val result = audioDecoder.analyzeAudio(uri) { progress ->
                    _uiState.value = _uiState.value.copy(testProgress = progress)
                }

                _uiState.value = _uiState.value.copy(
                    isAnalyzingTest = false,
                    testProgress = 1f,
                    testResult = result
                )

                val ref = savedReference.value
                if (ref != null) {
                    recordComparison(result, ref)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzingTest = false,
                    errorMessage = e.localizedMessage ?: "Terjadi kesalahan saat memproses file pengujian."
                )
            }
        }
    }

    private suspend fun recordComparison(test: AudioAnalysisResult, ref: ReferenceTrackEntity) {
        val diff = round((test.integratedLufs - ref.integratedLufs) * 10.0) / 10.0
        val gainRec = round((-diff) * 10.0) / 10.0
        val status = when {
            diff > 0.1 -> "LEBIH_KERAS"
            diff < -0.1 -> "LEBIH_PELAN"
            else -> "SEIMBANG"
        }

        val historyItem = AnalysisHistoryEntity(
            testFileName = test.fileName,
            testDurationMs = test.durationMs,
            testSampleRate = test.sampleRate,
            testChannels = test.channelCount,
            testLufs = test.integratedLufs,
            testTruePeak = test.truePeakDbtp,
            refFileName = ref.fileName,
            refLufs = ref.integratedLufs,
            refTruePeak = ref.truePeakDbtp,
            diffLufs = diff,
            gainRecommendationDb = gainRec,
            status = status
        )
        historyDao.insertHistory(historyItem)
    }

    fun clearTestTrack() {
        _uiState.value = _uiState.value.copy(
            testResult = null,
            testProgress = 0f,
            isAnalyzingTest = false
        )
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            historyDao.deleteHistoryById(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyDao.clearAllHistory()
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun calculateComparison(test: AudioAnalysisResult, ref: ReferenceTrackEntity): ComparisonData {
        val diff = round((test.integratedLufs - ref.integratedLufs) * 10.0) / 10.0
        val gainRec = round((-diff) * 10.0) / 10.0
        val status = when {
            diff > 0.1 -> LoudnessStatus.LOUDER
            diff < -0.1 -> LoudnessStatus.QUIETER
            else -> LoudnessStatus.MATCHED
        }
        return ComparisonData(
            diffLufs = diff,
            gainRecommendationDb = gainRec,
            status = status,
            testTruePeak = test.truePeakDbtp,
            refTruePeak = ref.truePeakDbtp
        )
    }
}
