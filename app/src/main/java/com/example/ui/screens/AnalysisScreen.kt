package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ReferenceTrackEntity
import com.example.ui.components.ComparisonCard
import com.example.ui.components.ExportProgressDialog
import com.example.ui.components.ExportSuccessDialog
import com.example.ui.components.ReferenceTrackCard
import com.example.ui.components.TestTrackCard
import com.example.ui.components.VolumeAdjustmentCard
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.RoseWarning
import com.example.viewmodel.AnalysisUiState
import com.example.viewmodel.VolumeCheckerViewModel

@Composable
fun AnalysisScreen(
    uiState: AnalysisUiState,
    referenceTrack: ReferenceTrackEntity?,
    viewModel: VolumeCheckerViewModel,
    onPickReference: () -> Unit,
    onPickTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Exporting Progress Dialog
    if (uiState.isExporting) {
        ExportProgressDialog(
            progress = uiState.exportProgress,
            statusMessage = uiState.exportStatusMessage,
            onCancel = { viewModel.cancelExport() }
        )
    }

    // Export Success Dialog
    uiState.exportResult?.let { result ->
        ExportSuccessDialog(
            result = result,
            onDismiss = { viewModel.dismissExportResult() }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("analysis_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Analysis error banner
        if (uiState.errorMessage != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = RoseWarning.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, RoseWarning.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = RoseWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup Error",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Export error banner
        if (uiState.exportError != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = RoseWarning.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, RoseWarning.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error Ekspor",
                            tint = RoseWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = uiState.exportError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissExportError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup Error",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Section 1: Reference Track Card
        item {
            ReferenceTrackCard(
                reference = referenceTrack,
                isAnalyzing = uiState.isAnalyzingRef,
                isBusy = uiState.isBusy,
                progress = uiState.refProgress,
                onPickFile = onPickReference,
                onCancelAnalysis = { viewModel.cancelReferenceAnalysis() },
                onDeleteReference = { viewModel.deleteReference() }
            )
        }

        // Section 2: Test Track Card
        item {
            TestTrackCard(
                testResult = uiState.testResult,
                isAnalyzing = uiState.isAnalyzingTest,
                isBusy = uiState.isBusy,
                progress = uiState.testProgress,
                onPickFile = onPickTest,
                onCancelAnalysis = { viewModel.cancelTestAnalysis() }
            )
        }

        // Section 3: Comparison Result & Automatic/Manual Volume Adjustment
        if (referenceTrack != null && uiState.testResult != null && !uiState.isAnalyzingRef && !uiState.isAnalyzingTest) {
            val comparison = viewModel.calculateComparison(uiState.testResult, referenceTrack)

            item {
                ComparisonCard(
                    comparison = comparison,
                    testFileName = uiState.testResult.fileName,
                    refFileName = referenceTrack.fileName
                )
            }

            // Section 4: Volume Adjustment & MP3 Export
            item {
                VolumeAdjustmentCard(
                    uiState = uiState,
                    comparison = comparison,
                    testResult = uiState.testResult,
                    viewModel = viewModel
                )
            }
        }

        // Educational Standard Reference Box
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Standar & Proteksi Audio",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "• Penyesuaian Volume: Menerapkan rekomendasi gain dari perbandingan LUFS agar kenyaringan lagu seimbang dengan lagu referensi.\n• Proteksi True Peak: Limiter lookahead menjaga True Peak tetap di bawah batas aman (-1.0 dBTP) agar tidak clipping.\n• Ekspor MP3: Menghasilkan file MP3 baru berkualitas tinggi di folder Musik perangkat tanpa menimpa file asli.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
