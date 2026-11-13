package com.example.ui.components

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
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AudioFormatUtils
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseWarning
import com.example.viewmodel.ComparisonData
import com.example.viewmodel.LoudnessStatus
import kotlin.math.abs

@Composable
fun ComparisonCard(
    comparison: ComparisonData,
    testFileName: String,
    refFileName: String,
    modifier: Modifier = Modifier
) {
    val statusColor = when (comparison.status) {
        LoudnessStatus.LOUDER -> AmberAccent
        LoudnessStatus.QUIETER -> CyanPrimary
        LoudnessStatus.MATCHED -> EmeraldSuccess
    }

    val statusIcon = when (comparison.status) {
        LoudnessStatus.LOUDER -> Icons.AutoMirrored.Filled.VolumeUp
        LoudnessStatus.QUIETER -> Icons.AutoMirrored.Filled.VolumeDown
        LoudnessStatus.MATCHED -> Icons.Default.CheckCircle
    }

    val statusText = when (comparison.status) {
        LoudnessStatus.LOUDER -> "Lebih Keras dari Referensi"
        LoudnessStatus.QUIETER -> "Lebih Pelan dari Referensi"
        LoudnessStatus.MATCHED -> "Tingkat Serasi (Seimbang)"
    }

    // Projected True Peak if gain recommendation is applied
    val projectedPeak = comparison.testTruePeak + comparison.gainRecommendationDb
    val isProjectedClipping = projectedPeak > 0.0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("comparison_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.5.dp, statusColor.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with status badge
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
                            .background(statusColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Balance,
                            contentDescription = "Ikon Perbandingan",
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Hasil Perbandingan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Lagu Pengujian vs Referensi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when (comparison.status) {
                                LoudnessStatus.LOUDER -> "LEBIH KERAS"
                                LoudnessStatus.QUIETER -> "LEBIH PELAN"
                                LoudnessStatus.MATCHED -> "SEIMBANG"
                            },
                            color = statusColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Two main metric display blocks: Selisih LUFS & Rekomendasi Gain
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Selisih LUFS
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "SELISIH LUFS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        val sign = if (comparison.diffLufs > 0.0) "+" else ""
                        Text(
                            text = "$sign${String.format(java.util.Locale.US, "%.1f", comparison.diffLufs)} LU",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = statusColor
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                // Rekomendasi Gain
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "REKOMENDASI GAIN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = AudioFormatUtils.formatGain(comparison.gainRecommendationDb),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (abs(comparison.gainRecommendationDb) < 0.1) EmeraldSuccess else CyanPrimary
                        )
                        Text(
                            text = when {
                                comparison.gainRecommendationDb < -0.1 -> "Turunkan volume master"
                                comparison.gainRecommendationDb > 0.1 -> "Naikkan volume master"
                                else -> "Pertahankan volume saat ini"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Explanation guide box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Panduan Penyesuaian:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val explanationText = when (comparison.status) {
                            LoudnessStatus.LOUDER -> {
                                "Lagu pengujian '${testFileName}' memiliki kenyaringan ${String.format(java.util.Locale.US, "%.1f", abs(comparison.diffLufs))} LUFS lebih tinggi dibanding referensi '${refFileName}'. Untuk mencapai kenyaringan yang setara, kurangi fader master atau input limiter Anda sebesar ${String.format(java.util.Locale.US, "%.1f", abs(comparison.gainRecommendationDb))} dB."
                            }
                            LoudnessStatus.QUIETER -> {
                                "Lagu pengujian '${testFileName}' memiliki kenyaringan ${String.format(java.util.Locale.US, "%.1f", abs(comparison.diffLufs))} LUFS lebih rendah dibanding referensi '${refFileName}'. Untuk menyamakan kenyaringan, Anda dapat menaikkan gain master sebesar +${String.format(java.util.Locale.US, "%.1f", comparison.gainRecommendationDb)} dB."
                            }
                            LoudnessStatus.MATCHED -> {
                                "Kenyaringan lagu pengujian '${testFileName}' sudah selaras sempurna dengan lagu referensi '${refFileName}' dalam rentang toleransi standar industri audio."
                            }
                        }
                        Text(
                            text = explanationText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // True Peak Headroom Check Note
            if (comparison.gainRecommendationDb > 0.1) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isProjectedClipping) RoseWarning.copy(alpha = 0.15f) else EmeraldSuccess.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isProjectedClipping) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isProjectedClipping) RoseWarning else EmeraldSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = if (isProjectedClipping) "Peringatan True Peak Saat Menaikkan Gain" else "Headroom True Peak Aman",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isProjectedClipping) RoseWarning else EmeraldSuccess
                            )
                            val peakNote = if (isProjectedClipping) {
                                "Jika gain dinaikkan +${String.format(java.util.Locale.US, "%.1f", comparison.gainRecommendationDb)} dB, True Peak diperkirakan mencapai ${AudioFormatUtils.formatDbtp(projectedPeak)} (> 0 dBTP). Gunakan True Peak limiter untuk mencegah distorsi inter-sample."
                            } else {
                                "Setelah gain dinaikkan +${String.format(java.util.Locale.US, "%.1f", comparison.gainRecommendationDb)} dB, True Peak baru diperkirakan ${AudioFormatUtils.formatDbtp(projectedPeak)} (di bawah batas 0 dBTP)."
                            }
                            Text(
                                text = peakNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
