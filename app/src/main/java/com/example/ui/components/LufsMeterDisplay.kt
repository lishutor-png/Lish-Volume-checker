package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AudioFormatUtils
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseWarning
import com.example.ui.theme.AmberAccent

@Composable
fun LufsMeterCard(
    lufs: Double,
    modifier: Modifier = Modifier,
    testTag: String = "lufs_meter_card"
) {
    // Map -36 LUFS to -6 LUFS into 0f..1f range for progress bar
    val normalized = ((lufs - (-36.0)) / ((-6.0) - (-36.0))).toFloat().coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = normalized, label = "lufs_meter")

    val levelColor = when {
        lufs > -9.0 -> RoseWarning       // Sangat Keras / Compressed
        lufs > -12.0 -> AmberAccent      // Keras (Club/EDM master)
        lufs in -16.0..-12.0 -> EmeraldSuccess // Target Streaming Ideal (-14 LUFS)
        else -> CyanPrimary             // Dinamis / Santai
    }

    Surface(
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INTEGRATED LUFS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold
                )

                // Category tag
                val targetDesc = when {
                    lufs > -9.0 -> "Sangat Keras"
                    lufs > -12.0 -> "Keras (EDM/Club)"
                    lufs in -15.5..-13.0 -> "Standar Streaming"
                    lufs in -18.0..-15.6 -> "Dinamis / Akustik"
                    else -> "Pelan"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(levelColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = targetDesc,
                        color = levelColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Big readout
            Text(
                text = AudioFormatUtils.formatLufs(lufs),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = levelColor
            )

            // Horizontal meter bar with gradient
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(CyanPrimary, EmeraldSuccess, AmberAccent, RoseWarning)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("-36", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 10.sp)
                    Text("-24", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 10.sp)
                    Text("-14 (Target)", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("-9", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 10.sp)
                    Text("-6", style = MaterialTheme.typography.labelSmall, color = RoseWarning, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun TruePeakMeterCard(
    truePeak: Double,
    modifier: Modifier = Modifier,
    testTag: String = "true_peak_meter_card"
) {
    // Map -12 dBTP to +3 dBTP into 0f..1f range for progress bar
    val normalized = ((truePeak - (-12.0)) / (3.0 - (-12.0))).toFloat().coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = normalized, label = "true_peak_meter")

    val isClipping = truePeak > 0.0
    val isNearClip = truePeak > -1.0 && truePeak <= 0.0

    val peakColor = when {
        isClipping -> RoseWarning
        isNearClip -> AmberAccent
        else -> EmeraldSuccess
    }

    Surface(
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRUE PEAK (MAX)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold
                )

                val peakStatusText = when {
                    isClipping -> "Kliping Terdeteksi!"
                    isNearClip -> "Mendekati Batas"
                    else -> "Headroom Aman"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(peakColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = peakStatusText,
                        color = peakColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = AudioFormatUtils.formatDbtp(truePeak),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = peakColor
            )

            // Horizontal peak meter
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(CyanPrimary, EmeraldSuccess, AmberAccent, RoseWarning)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("-12", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 10.sp)
                    Text("-6", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 10.sp)
                    Text("-1.0 (Batas Aman)", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("0.0 (Kliping)", style = MaterialTheme.typography.labelSmall, color = RoseWarning, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("+3", style = MaterialTheme.typography.labelSmall, color = RoseWarning, fontSize = 10.sp)
                }
            }
        }
    }
}
