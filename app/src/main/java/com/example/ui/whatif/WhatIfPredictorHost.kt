package com.example.ui.whatif

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.whatif.WhatIfCalculator
import com.example.ui.upgrade.Aurora
import com.example.ui.upgrade.AuroraBackground
import com.example.ui.upgrade.Txt
import com.example.ui.upgrade.WhatIfPredictorScreen
import com.example.ui.upgrade.pressScale

/**
 * Upgraded What-If Predictor Host.
 * Connects real attendance data and subjects to the Aurora Glass WhatIfPredictorScreen.
 */
@Composable
fun WhatIfPredictorHost(controller: WhatIfController) {
    val ui by controller.ui.collectAsState()
    val ctx = remember { controller.context() }
    val profile = ctx.profile

    // Real baseline attendance data
    val baseline = ui.baseline ?: remember(ui) { WhatIfCalculator.buildBaseline(ctx) }

    // Multi-subject selector list: "Overall" + each individual subject
    val subjectList = remember(baseline) {
        val items = mutableListOf<Triple<String, Int, Int>>() // Name, Attended, Total
        if (baseline != null) {
            items.add(Triple("Overall Attendance", baseline.attended, baseline.conducted))
            baseline.subjects.forEach { s ->
                val label = s.shortName.ifBlank { s.code.ifBlank { s.fullName } }
                items.add(Triple(label, s.attended, s.conducted))
            }
        }
        if (items.isEmpty()) {
            items.add(Triple("Overall Attendance", 0, 0))
        }
        items
    }

    var selectedSubjectIndex by rememberSaveable { mutableIntStateOf(0) }
    val safeIndex = selectedSubjectIndex.coerceIn(0, (subjectList.size - 1).coerceAtLeast(0))
    val currentSubject = subjectList[safeIndex]

    val requiredTarget = remember(profile) {
        (profile.examTarget.takeIf { it > 0 } ?: 75).toFloat()
    }

    BackHandler(enabled = true) {
        controller.close()
    }

    AuroraBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.08f))
                        .border(1.dp, Color.White.copy(0.18f), CircleShape)
                        .pressScale(haptic = true) { controller.close() },
                    contentAlignment = Alignment.Center
                ) {
                    Txt("←", 20, Aurora.TextHi, FontWeight.Bold)
                }

                Txt("Simulation Mode", 13, Aurora.TextLo, FontWeight.SemiBold)

                // Close button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(0.08f))
                        .border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(50))
                        .pressScale(haptic = true) { controller.close() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Txt("Close", 13, Aurora.TextHi, FontWeight.SemiBold)
                }
            }

            // Horizontal Subject Selector Chips
            if (subjectList.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    subjectList.forEachIndexed { index, (name, att, tot) ->
                        val isSelected = index == safeIndex
                        val chipBg by animateColorAsState(
                            if (isSelected) Aurora.Violet else Color.White.copy(0.08f),
                            animationSpec = tween(300),
                            label = "subChipBg"
                        )
                        val chipScale by animateFloatAsState(
                            if (isSelected) 1.06f else 1f,
                            animationSpec = spring(Spring.DampingRatioMediumBouncy),
                            label = "subChipScale"
                        )
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = chipScale
                                    scaleY = chipScale
                                }
                                .clip(RoundedCornerShape(50))
                                .background(chipBg)
                                .then(
                                    if (isSelected) {
                                        Modifier.background(
                                            Brush.horizontalGradient(listOf(Aurora.Violet, Aurora.Cyan))
                                        )
                                    } else {
                                        Modifier.border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(50))
                                    }
                                )
                                .pressScale(haptic = true) {
                                    selectedSubjectIndex = index
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Txt(
                                text = if (index == 0) "Overall" else name,
                                size = 13,
                                color = if (isSelected) Color.White else Aurora.TextLo,
                                weight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Upgraded What-If Predictor Screen
            WhatIfPredictorScreen(
                subject = currentSubject.first,
                attended = currentSubject.second,
                total = currentSubject.third,
                required = requiredTarget,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Preview(name = "What-If Predictor Dark", showBackground = true, backgroundColor = 0xFF080816, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun WhatIfPredictorScreenDarkPreview() {
    AuroraBackground {
        WhatIfPredictorScreen(
            subject = "Data Structures & Algorithms",
            attended = 28,
            total = 36,
            required = 75f
        )
    }
}
