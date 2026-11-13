package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioAnalysisResult
import com.example.ui.AudioFormatUtils
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoSecondary
import com.example.viewmodel.AnalysisUiState
import com.example.viewmodel.ComparisonData
import com.example.viewmodel.ProtectionMode
import com.example.viewmodel.VolumeCheckerViewModel
import java.util.Locale
import kotlin.math.round

@Composable
fun VolumeAdjustmentCard(
    uiState: AnalysisUiState,
    comparison: ComparisonData,
    testResult: AudioAnalysisResult,
    viewModel: VolumeCheckerViewModel,
    modifier: Modifier = Modifier
) {
    val recommendedGain = comparison.gainRecommendationDb
    val requestedGain = if (uiState.useAutomaticGain) recommendedGain else uiState.customGainDb.toDouble()

    val safetyPlan = VolumeCheckerViewModel.computeGainSafetyPlan(
        originalLufs = testResult.integratedLufs,
        originalTruePeak = testResult.truePeakDbtp,
        requestedGainDb = requestedGain,
        protectionMode = uiState.protectionMode,
        ceilingDbtp = uiState.limiterCeilingDbtp.toDouble()
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("volume_adjustment_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Penyesuaian & Ekspor MP3",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Buat file MP3 baru tanpa mengubah file asli",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Choice: Rekomendasi Otomatis vs Atur Manual
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.useAutomaticGain,
                    onClick = { viewModel.setUseAutomaticGain(true) },
                    label = {
                        Text(
                            text = "Rekomendasi (${AudioFormatUtils.formatGain(recommendedGain)})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (uiState.useAutomaticGain) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = CyanPrimary,
                        selectedLeadingIconColor = CyanPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chip_auto_gain")
                )

                FilterChip(
                    selected = !uiState.useAutomaticGain,
                    onClick = { viewModel.setUseAutomaticGain(false) },
                    label = {
                        Text(
                            text = "Atur Manual",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (!uiState.useAutomaticGain) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = IndigoSecondary.copy(alpha = 0.2f),
                        selectedLabelColor = IndigoSecondary,
                        selectedLeadingIconColor = IndigoSecondary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chip_manual_gain")
                )
            }

            // Manual Slider controls if manual mode selected
            AnimatedVisibility(visible = !uiState.useAutomaticGain) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nilai Gain Manual:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = { viewModel.setCustomGainDb(recommendedGain.toFloat()) }
                            ) {
                                Text("Reset ke Rekomendasi", fontSize = 11.sp, color = CyanPrimary)
                            }
                            Text(
                                text = AudioFormatUtils.formatGain(uiState.customGainDb.toDouble()),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = IndigoSecondary
                            )
                        }
                    }

                    Slider(
                        value = uiState.customGainDb,
                        onValueChange = { raw ->
                            val rounded = (round(raw * 10f) / 10f)
                            viewModel.setCustomGainDb(rounded)
                        },
                        valueRange = -12f..12f,
                        steps = 239,
                        colors = SliderDefaults.colors(
                            thumbColor = IndigoSecondary,
                            activeTrackColor = IndigoSecondary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("manual_gain_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("-12 dB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("0 dB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("+12 dB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Audio Protection Mode Section
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Proteksi True Peak (Anti-Clipping)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Headroom: ${AudioFormatUtils.formatGain(safetyPlan.availableHeadroomDb)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (safetyPlan.availableHeadroomDb > 0.5) EmeraldSuccess else AmberAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Protection Mode Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.protectionMode == ProtectionMode.TRUE_PEAK_LIMITER,
                            onClick = { viewModel.setProtectionMode(ProtectionMode.TRUE_PEAK_LIMITER) },
                            label = {
                                Text(
                                    text = "Limiter True Peak",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldSuccess.copy(alpha = 0.2f),
                                selectedLabelColor = EmeraldSuccess,
                                selectedLeadingIconColor = EmeraldSuccess
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chip_mode_limiter")
                        )

                        FilterChip(
                            selected = uiState.protectionMode == ProtectionMode.SAFE_GAIN_CAP,
                            onClick = { viewModel.setProtectionMode(ProtectionMode.SAFE_GAIN_CAP) },
                            label = {
                                Text(
                                    text = "Batas Gain Aman",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = CyanPrimary,
                                selectedLeadingIconColor = CyanPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chip_mode_safe_cap")
                        )
                    }

                    // Ceiling selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Batas Ceiling True Peak:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = uiState.limiterCeilingDbtp == -1.0f,
                                onClick = { viewModel.setLimiterCeiling(-1.0f) },
                                label = { Text("-1.0 dBTP", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldSuccess.copy(alpha = 0.2f),
                                    selectedLabelColor = EmeraldSuccess
                                ),
                                modifier = Modifier.testTag("chip_ceiling_10")
                            )
                            FilterChip(
                                selected = uiState.limiterCeilingDbtp == -0.5f,
                                onClick = { viewModel.setLimiterCeiling(-0.5f) },
                                label = { Text("-0.5 dBTP", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldSuccess.copy(alpha = 0.2f),
                                    selectedLabelColor = EmeraldSuccess
                                ),
                                modifier = Modifier.testTag("chip_ceiling_05")
                            )
                        }
                    }

                    Text(
                        text = if (uiState.protectionMode == ProtectionMode.TRUE_PEAK_LIMITER) {
                            "Lookahead True Peak Limiter meredam puncak berlebih secara halus di bawah ${uiState.limiterCeilingDbtp} dBTP untuk mencegah clipping pasca-encoding MP3."
                        } else {
                            "Tanpa kompresi dinamika: kenaikan gain otomatis dibatasi maksimal ${AudioFormatUtils.formatGain(safetyPlan.availableHeadroomDb)} agar puncak lagu tidak menyentuh batas ceiling."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            // Notice if Safe Gain Cap restricted the boost
            AnimatedVisibility(visible = safetyPlan.wasGainCapped) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = CyanPrimary.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Gain dibatasi otomatis dari ${AudioFormatUtils.formatGain(safetyPlan.requestedGainDb)} menjadi ${AudioFormatUtils.formatGain(safetyPlan.effectiveGainDb)} karena sisa headroom True Peak hanya ${AudioFormatUtils.formatGain(safetyPlan.availableHeadroomDb)}. Ini mencegah clipping tanpa merusak dinamika.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Warning if Limiter is doing heavy peak reduction (> 4 dB)
            AnimatedVisibility(visible = safetyPlan.isAggressiveBoost) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AmberAccent.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AmberAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Peringatan Headroom True Peak Terbatas",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AmberAccent
                            )
                            Text(
                                text = "Kenaikan ${AudioFormatUtils.formatGain(safetyPlan.requestedGainDb)} melebihi headroom aman sebesar ${String.format(Locale.US, "%.1f", safetyPlan.estimatedReductionDb)} dB. Limiter tetap menjaga bebas clipping, namun disarankan beralih ke mode 'Batas Gain Aman' atau kurangi gain agar dinamika lagu tetap natural.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Projection Preview Summary Box (Before Processing)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "GAIN EFEKTIF",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = AudioFormatUtils.formatGain(safetyPlan.effectiveGainDb),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "ESTIMASI LUFS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = AudioFormatUtils.formatLufs(safetyPlan.projectedLufs),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
                        Text(
                            text = "TRUE PEAK MAKS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = AudioFormatUtils.formatDbtp(safetyPlan.projectedTruePeak),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSuccess
                        )
                    }
                }
            }

            // Export Action Button
            Button(
                onClick = { viewModel.startExport(safetyPlan) },
                enabled = !uiState.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("export_mp3_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Proses & Ekspor MP3 Baru",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
