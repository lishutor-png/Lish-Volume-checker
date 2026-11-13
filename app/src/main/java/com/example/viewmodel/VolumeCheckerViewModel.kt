package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioAnalysisResult
import com.example.audio.AudioDecoder
import com.example.audio.AudioProcessorExporter
import com.example.audio.ExportResult
import com.example.data.AnalysisHistoryEntity
import com.example.data.AppDatabase
import com.example.data.ReferenceTrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

enum class LoudnessStatus {
    LOUDER,   // Lebih Keras
    QUIETER,  // Lebih Pelan
    MATCHED   // Seimbang
}

enum class ProtectionMode {
    TRUE_PEAK_LIMITER, // Gunakan limiter True Peak agar target LUFS tercapai tanpa clipping
    SAFE_GAIN_CAP      // Batasi kenaikan gain secara ketat sesuai sisa headroom True Peak
}

data class GainSafetyPlan(
    val requestedGainDb: Double,
    val effectiveGainDb: Double,
    val availableHeadroomDb: Double,
    val wasGainCapped: Boolean,
    val estimatedReductionDb: Double,
    val isAggressiveBoost: Boolean,
    val projectedLufs: Double,
    val projectedTruePeak: Double
)

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
    val testUri: Uri? = null,
    val errorMessage: String? = null,
    // Gain Adjustment & Protection settings
    val useAutomaticGain: Boolean = true,
    val customGainDb: Float = 0f,
    val protectionMode: ProtectionMode = ProtectionMode.TRUE_PEAK_LIMITER,
    val limiterCeilingDbtp: Float = -1.0f,
    // Export state
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportStatusMessage: String = "",
    val exportResult: ExportResult? = null,
    val exportError: String? = null
) {
    val isBusy: Boolean
        get() = isAnalyzingRef || isAnalyzingTest || isExporting
}

class VolumeCheckerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val referenceDao = database.referenceDao()
    private val historyDao = database.historyDao()
    private val audioDecoder = AudioDecoder(application)
    private val audioExporter = AudioProcessorExporter(application)

    val savedReference: StateFlow<ReferenceTrackEntity?> = referenceDao.getReferenceTrack()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val historyList: StateFlow<List<AnalysisHistoryEntity>> = historyDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    fun analyzeReference(uri: Uri) {
        if (_uiState.value.isBusy) return
        activeJob = viewModelScope.launch {
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

                // If test result is already present, update recommended gain and record comparison
                val currentTest = _uiState.value.testResult
                if (currentTest != null) {
                    val diff = round((currentTest.integratedLufs - entity.integratedLufs) * 10.0) / 10.0
                    val updatedRec = round((-diff) * 10.0).toFloat() / 10f
                    _uiState.value = _uiState.value.copy(customGainDb = updatedRec)
                    recordComparison(currentTest, entity)
                }

                _uiState.value = _uiState.value.copy(
                    isAnalyzingRef = false,
                    refProgress = 1f
                )
            } catch (_: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzingRef = false,
                    refProgress = 0f
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzingRef = false,
                    errorMessage = e.localizedMessage ?: "Terjadi kesalahan saat memproses file referensi."
                )
            }
        }
    }

    fun cancelReferenceAnalysis() {
        if (_uiState.value.isAnalyzingRef) {
            activeJob?.cancel()
            _uiState.value = _uiState.value.copy(
                isAnalyzingRef = false,
                refProgress = 0f
            )
        }
    }

    fun deleteReference() {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            referenceDao.deleteReferenceTrack()
        }
    }

    fun analyzeTestTrack(uri: Uri) {
        if (_uiState.value.isBusy) return
        activeJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnalyzingTest = true,
                testProgress = 0f,
                errorMessage = null,
                testUri = uri
            )
            try {
                val result = audioDecoder.analyzeAudio(uri) { progress ->
                    _uiState.value = _uiState.value.copy(testProgress = progress)
                }

                val ref = savedReference.value
                val initialGain = if (ref != null) {
                    val diff = round((result.integratedLufs - ref.integratedLufs) * 10.0) / 10.0
                    round((-diff) * 10.0).toFloat() / 10f
                } else {
                    0f
                }

                _uiState.value = _uiState.value.copy(
                    isAnalyzingTest = false,
                    testProgress = 1f,
                    testResult = result,
                    testUri = uri,
                    customGainDb = initialGain
                )

                if (ref != null) {
                    recordComparison(result, ref)
                }
            } catch (_: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzingTest = false,
                    testProgress = 0f
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzingTest = false,
                    errorMessage = e.localizedMessage ?: "Terjadi kesalahan saat memproses file pengujian."
                )
            }
        }
    }

    fun cancelTestAnalysis() {
        if (_uiState.value.isAnalyzingTest) {
            activeJob?.cancel()
            _uiState.value = _uiState.value.copy(
                isAnalyzingTest = false,
                testProgress = 0f
            )
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
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(
            testResult = null,
            testUri = null,
            testProgress = 0f,
            isAnalyzingTest = false
        )
    }

    fun setUseAutomaticGain(useAuto: Boolean) {
        _uiState.value = _uiState.value.copy(useAutomaticGain = useAuto)
    }

    fun setCustomGainDb(gain: Float) {
        _uiState.value = _uiState.value.copy(customGainDb = gain)
    }

    fun setProtectionMode(mode: ProtectionMode) {
        _uiState.value = _uiState.value.copy(protectionMode = mode)
    }

    fun setLimiterCeiling(ceiling: Float) {
        _uiState.value = _uiState.value.copy(limiterCeilingDbtp = ceiling)
    }

    fun startExport(plan: GainSafetyPlan) {
        if (_uiState.value.isBusy) return
        val testUri = _uiState.value.testUri
        val testResult = _uiState.value.testResult
        if (testUri == null || testResult == null) {
            _uiState.value = _uiState.value.copy(exportError = "Pilih dan analisis lagu pengujian terlebih dahulu.")
            return
        }

        val useLimiter = _uiState.value.protectionMode == ProtectionMode.TRUE_PEAK_LIMITER
        val ceiling = _uiState.value.limiterCeilingDbtp.toDouble()

        activeJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExporting = true,
                exportProgress = 0f,
                exportStatusMessage = "Menyiapkan pemrosesan audio...",
                exportError = null,
                exportResult = null
            )
            try {
                val result = audioExporter.processAndExport(
                    sourceUri = testUri,
                    requestedGainDb = plan.requestedGainDb,
                    appliedGainDb = plan.effectiveGainDb,
                    wasGainCapped = plan.wasGainCapped,
                    useLimiter = useLimiter,
                    ceilingDbtp = ceiling,
                    originalLufs = testResult.integratedLufs,
                    originalTruePeak = testResult.truePeakDbtp
                ) { progress, message ->
                    _uiState.value = _uiState.value.copy(
                        exportProgress = progress,
                        exportStatusMessage = message
                    )
                }

                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportProgress = 1f,
                    exportResult = result
                )
            } catch (_: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportProgress = 0f,
                    exportStatusMessage = "Proses dibatalkan."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportError = e.localizedMessage ?: "Gagal memproses dan mengekspor file MP3."
                )
            }
        }
    }

    fun cancelExport() {
        if (_uiState.value.isExporting) {
            activeJob?.cancel()
            _uiState.value = _uiState.value.copy(
                isExporting = false,
                exportProgress = 0f,
                exportStatusMessage = "Dibatalkan oleh pengguna."
            )
        }
    }

    fun dismissExportResult() {
        _uiState.value = _uiState.value.copy(exportResult = null)
    }

    fun dismissExportError() {
        _uiState.value = _uiState.value.copy(exportError = null)
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

    companion object {
        /**
         * Calculates safe gain plan based on True Peak headroom and chosen protection mode.
         * Never blindly boosts past True Peak ceiling without limiting or capping.
         */
        fun computeGainSafetyPlan(
            originalLufs: Double,
            originalTruePeak: Double,
            requestedGainDb: Double,
            protectionMode: ProtectionMode,
            ceilingDbtp: Double
        ): GainSafetyPlan {
            val rawHeadroom = ceilingDbtp - originalTruePeak
            val headroomRounded = round(rawHeadroom * 10.0) / 10.0

            val effectiveGain: Double
            val wasCapped: Boolean
            val estimatedReduction: Double

            when (protectionMode) {
                ProtectionMode.SAFE_GAIN_CAP -> {
                    // Strictly cap gain so True Peak never exceeds ceilingDbtp
                    if (requestedGainDb > headroomRounded) {
                        effectiveGain = headroomRounded
                        wasCapped = true
                    } else {
                        effectiveGain = requestedGainDb
                        wasCapped = false
                    }
                    estimatedReduction = 0.0
                }
                ProtectionMode.TRUE_PEAK_LIMITER -> {
                    effectiveGain = requestedGainDb
                    wasCapped = false
                    estimatedReduction = max(0.0, round((requestedGainDb - headroomRounded) * 10.0) / 10.0)
                }
            }

            val projectedTruePeak = min(ceilingDbtp, originalTruePeak + effectiveGain)
            // If limiter reduces peaks heavily, integrated LUFS increases slightly less than nominal gain
            val estimatedCompressedLoss = if (estimatedReduction > 0.0) min(estimatedReduction * 0.25, 1.5) else 0.0
            val projectedLufs = originalLufs + effectiveGain - estimatedCompressedLoss

            return GainSafetyPlan(
                requestedGainDb = requestedGainDb,
                effectiveGainDb = effectiveGain,
                availableHeadroomDb = headroomRounded,
                wasGainCapped = wasCapped,
                estimatedReductionDb = estimatedReduction,
                isAggressiveBoost = estimatedReduction > 4.0,
                projectedLufs = projectedLufs,
                projectedTruePeak = projectedTruePeak
            )
        }
    }
}
