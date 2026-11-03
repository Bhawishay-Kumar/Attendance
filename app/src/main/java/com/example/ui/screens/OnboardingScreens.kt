package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Batch
import com.example.data.model.CourseSelection
import com.example.data.model.StudentProfile
import com.example.ui.components.BottomEdgeBlur
import com.example.ui.components.ScenicSkyToggle
import com.example.ui.components.StarfieldCanvas
import com.example.ui.components.TopEdgeBlur
import com.example.ui.theme.CaveatFontFamily
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.AttendanceViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun OnboardingFlow(
    viewModel: AttendanceViewModel,
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    var currentStep by remember { mutableStateOf(1) } // 1 to 5
    var tempProfile by remember { mutableStateOf(viewModel.profile.value) }
    var showBackfillModal by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // Celestial Starfield Canvas (Active in Dark Mode or on Screens 1 & 5)
        StarfieldCanvas(
            enabled = glass.isDark || currentStep == 1 || currentStep == 5 || tempProfile.starfieldAnimationEnabled,
            modifier = Modifier.fillMaxSize()
        )

        // Top Edge Blur
        TopEdgeBlur(
            modifier = Modifier.align(Alignment.TopCenter),
            enabled = tempProfile.progressiveBlurEnabled
        )

        // Bottom Edge Blur
        BottomEdgeBlur(
            modifier = Modifier.align(Alignment.BottomCenter),
            enabled = tempProfile.progressiveBlurEnabled
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Animated Screen-to-Screen Transition
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(
                            animationSpec = tween(420, easing = FastOutSlowInEasing),
                            initialOffsetX = { it }
                        ) + fadeIn()).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(420, easing = FastOutSlowInEasing),
                                targetOffsetX = { -it }
                            ) + fadeOut()
                        )
                    } else {
                        (slideInHorizontally(
                            animationSpec = tween(420, easing = FastOutSlowInEasing),
                            initialOffsetX = { -it }
                        ) + fadeIn()).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(420, easing = FastOutSlowInEasing),
                                targetOffsetX = { it }
                            ) + fadeOut()
                        )
                    }
                },
                label = "onboarding_step_anim",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { step ->
                when (step) {
                    1 -> OnboardingScreen1(
                        onNext = { currentStep = 2 }
                    )
                    2 -> OnboardingScreen2(
                        profile = tempProfile,
                        onProfileChanged = {
                            tempProfile = it
                            viewModel.updateProfile(it)
                        },
                        onBack = { currentStep = 1 },
                        onNext = { currentStep = 3 }
                    )
                    3 -> OnboardingScreen3(
                        profile = tempProfile,
                        onProfileChanged = {
                            tempProfile = it
                            viewModel.updateProfile(it)
                        },
                        onBack = { currentStep = 2 },
                        onNext = { currentStep = 4 }
                    )
                    4 -> OnboardingScreen4(
                        profile = tempProfile,
                        onProfileChanged = {
                            tempProfile = it
                            viewModel.updateProfile(it)
                        },
                        onBack = { currentStep = 3 },
                        onNext = { currentStep = 5 }
                    )
                    5 -> OnboardingScreen5(
                        profile = tempProfile,
                        onBack = { currentStep = 4 },
                        onOpenBackfill = { showBackfillModal = true },
                        onComplete = {
                            val finalProfile = tempProfile.copy(isOnboardingCompleted = true)
                            viewModel.updateProfile(finalProfile)
                            onFinishOnboarding()
                        }
                    )
                }
            }
        }

        // Mid-Month Backfill Bottom Sheet Modal
        if (showBackfillModal) {
            MidMonthBackfillBottomSheet(
                onDismiss = { showBackfillModal = false },
                onApply = { fromDate, toDate, markAllPresent ->
                    viewModel.performBackfill(fromDate, toDate, markAllPresent)
                    showBackfillModal = false
                }
            )
        }
    }
}

// ============================================================================
// SCREEN 1: APP INTRODUCTION & KEY FEATURES
// ============================================================================
@Composable
fun OnboardingScreen1(onNext: () -> Unit) {
    val glass = LocalGlassTheme.current
    val scrollState = rememberScrollState()

    // Floating animation for mortarboard badge
    val infiniteTransition = rememberInfiniteTransition(label = "badge_float")
    val badgeOffsetY by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset_y"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 76x76 Floating App Icon Badge with Outer Glow
            Box(
                modifier = Modifier
                    .offset(y = badgeOffsetY.dp)
                    .size(76.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color(0xFF7C3AED),
                        spotColor = Color(0xFF4F46E5)
                    )
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFF9333EA))
                        )
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = "College Mortarboard",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Hero Title
            Text(
                text = "College Attendance Tracker",
                fontFamily = OutfitFontFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (glass.isDark) Color.White else glass.textPrimary,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "Track your attendance, maintain your target and plan your academic journey intelligently.",
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (glass.isDark) Color(0xFFCBD5E1) else glass.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section Divider with Fading Lines
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, glass.cardBorder)
                            )
                        )
                )
                Text(
                    text = "Key Features",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (glass.isDark) Color(0xFFE0E7FF) else glass.royalIndigo,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(glass.cardBorder, Color.Transparent)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2x2 Frosted Glass Bento Grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BentoFeatureItem(
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        iconBg = Color(0xFF2563EB),
                        title = "What-If Attendance Simulator",
                        body = "See how your attendance changes with different scenarios.",
                        modifier = Modifier.weight(1f)
                    )
                    BentoFeatureItem(
                        icon = Icons.Default.CalendarMonth,
                        iconBg = Color(0xFF10B981),
                        title = "Smart Bunk Planner",
                        body = "Plan your leaves and bunk days wisely and safely.",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BentoFeatureItem(
                        icon = Icons.Default.Assessment,
                        iconBg = Color(0xFFF59E0B),
                        title = "Detailed Attendance Reports",
                        body = "Track your progress with comprehensive reports.",
                        modifier = Modifier.weight(1f)
                    )
                    BentoFeatureItem(
                        icon = Icons.Default.PictureAsPdf,
                        iconBg = Color(0xFFEF4444),
                        title = "PDF Summaries",
                        body = "Download and share your attendance summaries.",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Motivational Handwritten Quote with SVG Underline Swoosh
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Smarter Attendance\nBrighter Future",
                    fontFamily = CaveatFontFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (glass.isDark) com.example.ui.theme.LightSurfaceAlt else glass.royalIndigo,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp,
                    modifier = Modifier.rotate(-2f)
                )

                // Curved underline swoosh
                Canvas(modifier = Modifier.size(width = 140.dp, height = 12.dp)) {
                    val path = Path().apply {
                        moveTo(10f, 4f)
                        quadraticTo(
                            size.width / 2, size.height + 4f,
                            size.width - 10f, 4f
                        )
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF818CF8),
                        style = Stroke(width = 2.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Bottom Primary CTA Button
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(16.dp, RoundedCornerShape(9999.dp), spotColor = Color(0xFF7C3AED))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(9999.dp))
                .testTag("onboarding_get_started_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4F46E5)
            ),
            shape = RoundedCornerShape(9999.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Get Started",
                    fontFamily = OutfitFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun BentoFeatureItem(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    Box(
        modifier = modifier
            .height(132.dp)
            .shadow(6.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(glass.cardSurface)
            .border(1.dp, glass.cardBorder, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg.copy(alpha = 0.2f))
                    .border(1.dp, iconBg.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconBg,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = glass.textPrimary,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = body,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 11.sp,
                    color = glass.textMuted,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

// ============================================================================
// SCREEN 2: STUDENT PROFILE & LAB BATCH
// ============================================================================
@Composable
fun OnboardingScreen2(
    profile: StudentProfile,
    onProfileChanged: (StudentProfile) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val glass = LocalGlassTheme.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var nameInput by remember { mutableStateOf(profile.name) }
    var dobInput by remember { mutableStateOf(profile.dateOfBirth) }
    var selectedBatch by remember { mutableStateOf(profile.batchEnum) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Circular Glass Back Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(glass.cardSurface)
                        .border(1.dp, glass.cardBorder, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = glass.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Progress Pill & Progress Bar (25%)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "Step 1 of 4",
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = glass.textMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { 0.25f },
                        modifier = Modifier
                            .width(88.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = glass.royalIndigo,
                        trackColor = glass.cardBorder
                    )
                }

                // Scenic Day/Night Toggle
                ScenicSkyToggle(
                    isDarkMode = profile.isDarkMode,
                    onToggle = { onProfileChanged(profile.copy(isDarkMode = it)) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Header
            Text(
                text = "Let's set up your profile",
                fontFamily = OutfitFontFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = glass.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tell us about your academic details so we can personalize your experience.",
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 14.sp,
                color = glass.textSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Main Form Container Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(glass.cardSurface)
                    .border(1.dp, glass.cardBorder, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Full Name Input
                    Column {
                        Text(
                            text = "Full Name",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = glass.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = {
                                nameInput = it
                                onProfileChanged(profile.copy(name = it))
                            },
                            placeholder = { Text("Enter your full name") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = glass.royalIndigo,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (nameInput.isNotEmpty()) {
                                    IconButton(onClick = {
                                        nameInput = ""
                                        onProfileChanged(profile.copy(name = ""))
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = glass.textMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = glass.royalIndigo,
                                unfocusedBorderColor = glass.cardBorder,
                                focusedTextColor = glass.textPrimary,
                                unfocusedTextColor = glass.textPrimary
                            )
                        )
                    }

                    // Date of Birth Row & Picker
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Date of Birth (Optional)",
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = glass.textPrimary
                            )
                            // Glowing Birthday Magic Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "✨ Unlocks Birthday Magic 🎂",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (glass.isDark) Color(0xFF0F172A).copy(alpha = 0.7f) else com.example.ui.theme.LightPearl)
                                .border(1.dp, glass.cardBorder, RoundedCornerShape(16.dp))
                                .clickable {
                                    // Open DatePickerDialog
                                    val cal = Calendar.getInstance()
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val formatted = String.format("%04d-%02d-%02d", y, m + 1, d)
                                            dobInput = formatted
                                            onProfileChanged(profile.copy(dateOfBirth = formatted))
                                        },
                                        2005, 9, 26
                                    ).show()
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cake,
                                        contentDescription = null,
                                        tint = Color(0xFFF472B6),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = dobInput.ifEmpty { "Select Date of Birth" },
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = glass.textPrimary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Today,
                                    contentDescription = null,
                                    tint = glass.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Lab Batch Selection (Batch A vs Batch B)
                    Column {
                        Text(
                            text = "Batch",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = glass.textPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 2-Column Segmented Pill
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (glass.isDark) Color(0xFF0F172A) else Color(0xFFE2E8F0))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(Batch.BATCH_A to "Batch A", Batch.BATCH_B to "Batch B").forEach { (batchOption, label) ->
                                val isSelected = selectedBatch == batchOption
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) glass.royalIndigo else Color.Transparent)
                                        .clickable {
                                            selectedBatch = batchOption
                                            onProfileChanged(profile.copy(batch = batchOption.name))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else glass.textMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Live Batch Lab Schedule Preview Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(glass.royalIndigo.copy(alpha = 0.12f))
                                .border(1.dp, glass.royalIndigo.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = glass.royalIndigo,
                                    modifier = Modifier.size(16.dp).offset(y = 2.dp)
                                )
                                Text(
                                    text = if (selectedBatch == Batch.BATCH_A) {
                                        "Wed: Quality Lab (2x) • Thu: Woven Lab (2x) & Seminar (2x Auto) • Fri: CPT-II Lab (2x)"
                                    } else {
                                        "Wed: Seminar (2x Auto) • Thu: CPT-II Lab (2x) & Quality Lab (2x) • Fri: Woven Lab (2x)"
                                    },
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = glass.textPrimary,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Bottom Primary CTA Button
        Button(
            onClick = {
                if (nameInput.isBlank()) {
                    Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
                } else {
                    onProfileChanged(profile.copy(name = nameInput.trim()))
                    onNext()
                }
            },
            enabled = nameInput.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(12.dp, RoundedCornerShape(9999.dp), spotColor = glass.royalIndigo)
                .testTag("onboarding_continue_screen_2"),
            colors = ButtonDefaults.buttonColors(
                containerColor = glass.royalIndigo,
                disabledContainerColor = glass.cardBorder.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(9999.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Continue",
                    fontFamily = OutfitFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ============================================================================
// SCREEN 3: ACADEMICS & SUBJECT MAPPING
// ============================================================================
@Composable
fun OnboardingScreen3(
    profile: StudentProfile,
    onProfileChanged: (StudentProfile) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val glass = LocalGlassTheme.current
    val scrollState = rememberScrollState()

    var selectedCourse by remember { mutableStateOf(profile.courseSelectionEnum) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(glass.cardSurface)
                        .border(1.dp, glass.cardBorder, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = glass.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "Step 2 of 4",
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = glass.textMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { 0.50f },
                        modifier = Modifier
                            .width(88.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = glass.royalIndigo,
                        trackColor = glass.cardBorder
                    )
                }

                ScenicSkyToggle(
                    isDarkMode = profile.isDarkMode,
                    onToggle = { onProfileChanged(profile.copy(isDarkMode = it)) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Header
            Text(
                text = "Academic Track & Subject",
                fontFamily = OutfitFontFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = glass.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select your BHTT 5th Sem elective slot. This configures your timetable, filters, and auto-present rules.",
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 14.sp,
                color = glass.textSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Main Container Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(glass.cardSurface)
                    .border(1.dp, glass.cardBorder, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 3-Column Segmented Pill Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (glass.isDark) Color(0xFF0F172A) else Color(0xFFE2E8F0))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            CourseSelection.MAJOR_HONOURS to "Major",
                            CourseSelection.MINOR_ELECTIVE to "Minor",
                            CourseSelection.DIGITAL_LIBRARY to "Library"
                        ).forEach { (csOption, label) ->
                            val isSelected = selectedCourse == csOption
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) glass.royalIndigo else Color.Transparent)
                                    .clickable {
                                        selectedCourse = csOption
                                        onProfileChanged(profile.copy(courseSelection = csOption.name))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = OutfitFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else glass.textMuted
                                )
                            }
                        }
                    }

                    // Dynamic Subject Dropdown / Display Card
                    AnimatedContent(
                        targetState = selectedCourse,
                        label = "course_card_morph"
                    ) { cs ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (glass.isDark) Color(0xFF1E1B4B).copy(alpha = 0.4f) else Color(0xFFEEF2FF))
                                .border(1.5.dp, glass.royalIndigo.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val iconBg = when (cs) {
                                        CourseSelection.MAJOR_HONOURS -> Color(0xFF7C3AED)
                                        CourseSelection.MINOR_ELECTIVE -> Color(0xFF4F46E5)
                                        CourseSelection.DIGITAL_LIBRARY -> Color(0xFF06B6D4)
                                    }
                                    val iconVec = when (cs) {
                                        CourseSelection.MAJOR_HONOURS -> Icons.Default.Shield
                                        CourseSelection.MINOR_ELECTIVE -> Icons.Default.Tune
                                        CourseSelection.DIGITAL_LIBRARY -> Icons.Default.LocalLibrary
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(iconBg.copy(alpha = 0.2f))
                                            .border(1.dp, iconBg.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = iconVec,
                                            contentDescription = null,
                                            tint = iconBg,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Column {
                                        val codeText = when (cs) {
                                            CourseSelection.MAJOR_HONOURS -> "HTFT 501"
                                            CourseSelection.MINOR_ELECTIVE -> "MNPM 501"
                                            CourseSelection.DIGITAL_LIBRARY -> "Digital Library"
                                        }
                                        val titleText = when (cs) {
                                            CourseSelection.MAJOR_HONOURS -> "(Clothing Comfort)"
                                            CourseSelection.MINOR_ELECTIVE -> "(Production Planning & Control)"
                                            CourseSelection.DIGITAL_LIBRARY -> "(Self-Study / E-Resources Slot)"
                                        }
                                        Text(
                                            text = codeText,
                                            fontFamily = OutfitFontFamily,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = glass.textPrimary
                                        )
                                        Text(
                                            text = titleText,
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontSize = 11.5.sp,
                                            color = glass.textMuted
                                        )
                                    }
                                }

                                // Right Tag
                                if (cs == CourseSelection.DIGITAL_LIBRARY) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF06B6D4).copy(alpha = 0.2f))
                                            .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "🔒 AUTO-PRESENT 100%",
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF06B6D4)
                                        )
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "1 Hr Lecture/Tut",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = glass.textMuted
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = glass.textMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Info Callout Box 1 (Dynamic per selection)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selectedCourse == CourseSelection.DIGITAL_LIBRARY)
                                    Color(0xFF06B6D4).copy(alpha = 0.12f)
                                else
                                    glass.royalIndigo.copy(alpha = 0.12f)
                            )
                            .border(
                                1.dp,
                                if (selectedCourse == CourseSelection.DIGITAL_LIBRARY)
                                    Color(0xFF06B6D4).copy(alpha = 0.35f)
                                else
                                    glass.royalIndigo.copy(alpha = 0.35f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = if (selectedCourse == CourseSelection.DIGITAL_LIBRARY) Color(0xFF06B6D4) else glass.royalIndigo,
                                modifier = Modifier.size(16.dp).offset(y = 2.dp)
                            )
                            val calloutMsg = when (selectedCourse) {
                                CourseSelection.MAJOR_HONOURS -> "Major Honours code: HTFT 501. Includes Clothing Comfort (Mon P5, Tue P3, Thu P2, Fri P2)."
                                CourseSelection.MINOR_ELECTIVE -> "Minor Degree code: MNPM 501. Includes Production Planning & Control (Mon P5, Tue P3, Thu P2, Fri P2)."
                                CourseSelection.DIGITAL_LIBRARY -> "Digital Library Immunity: All 4 weekly elective slots automatically count as Present (Auto-Present), even if you tap Mark All Absent!"
                            }
                            Text(
                                text = calloutMsg,
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 11.5.sp,
                                color = glass.textPrimary,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Info Callout Box 2 (5 Blue Checkmarks Benefits Card)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (glass.isDark) Color(0xFF0F172A).copy(alpha = 0.6f) else com.example.ui.theme.LightPearl)
                            .border(1.dp, glass.cardBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Your selection will be used across the app",
                                    fontFamily = OutfitFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = glass.textPrimary
                                )
                            }

                            listOf(
                                "Home page & quick actions",
                                "Attendance marking",
                                "Timetable and class filtering",
                                "Reports & PDF exports",
                                "Smart Predictor & Bunk Planner"
                            ).forEach { itemText ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = itemText,
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontSize = 11.sp,
                                        color = glass.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Bottom Primary CTA Button
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(12.dp, RoundedCornerShape(9999.dp), spotColor = glass.royalIndigo)
                .testTag("onboarding_continue_screen_3"),
            colors = ButtonDefaults.buttonColors(containerColor = glass.royalIndigo),
            shape = RoundedCornerShape(9999.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Continue",
                    fontFamily = OutfitFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ============================================================================
// SCREEN 4: PREFERENCES, TARGETS & REMINDERS
// ============================================================================
@Composable
fun OnboardingScreen4(
    profile: StudentProfile,
    onProfileChanged: (StudentProfile) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val glass = LocalGlassTheme.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var examCutoff by remember { mutableStateOf(profile.examTarget) }
    var stipendCutoff by remember { mutableStateOf(profile.stipendTarget) }
    var safetyBuffer by remember { mutableStateOf(profile.safetyBuffer) }
    var dailyReminder by remember { mutableStateOf(profile.dailyReminderEnabled) }
    var dailyTime by remember { mutableStateOf("04:45 PM") }
    var weeklyReminder by remember { mutableStateOf(profile.weeklyReminderEnabled) }
    var weeklyDayTime by remember { mutableStateOf("Friday • 06:00 PM") }
    var mainObjective by remember { mutableStateOf(profile.mainObjective) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(glass.cardSurface)
                        .border(1.dp, glass.cardBorder, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = glass.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "Step 3 of 4",
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = glass.textMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { 0.75f },
                        modifier = Modifier
                            .width(88.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = glass.royalIndigo,
                        trackColor = glass.cardBorder
                    )
                }

                ScenicSkyToggle(
                    isDarkMode = profile.isDarkMode,
                    onToggle = { onProfileChanged(profile.copy(isDarkMode = it)) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Header
            Text(
                text = "Make it yours",
                fontFamily = OutfitFontFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = glass.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose your preferences to get the best experience and helpful reminders.",
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 14.sp,
                color = glass.textSecondary
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Stack of 5 Frosted Glass Cards
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                // Card 1 — Theme Preference
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(glass.cardSurface)
                        .border(1.dp, glass.cardBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text("Theme", fontFamily = OutfitFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                                Text("Choose your preferred app theme", fontSize = 10.5.sp, color = glass.textMuted)
                            }
                        }

                        // 3 Equal Segmented Cards (64.dp height)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple(false, "☀️", "Light"),
                                Triple(true, "🌙", "Dark")
                            ).forEach { (isDarkOption, emoji, label) ->
                                val isSelected = profile.isDarkMode == isDarkOption
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(64.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSelected) glass.royalIndigo else Color(0xFF0F172A).copy(alpha = 0.5f))
                                        .border(1.dp, if (isSelected) Color.White.copy(alpha = 0.3f) else glass.cardBorder, RoundedCornerShape(14.dp))
                                        .clickable {
                                            onProfileChanged(profile.copy(isDarkMode = isDarkOption))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(emoji, fontSize = 18.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            label,
                                            fontFamily = OutfitFontFamily,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else glass.textMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 2 — Attendance Cutoffs & Safety Buffer (Interactive Steppers & Slider)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(glass.cardSurface)
                        .border(1.dp, glass.cardBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = glass.royalIndigo,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Eligibility Cutoffs & Safety Buffer",
                                fontFamily = OutfitFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = glass.textPrimary
                            )
                        }

                        // Exam Eligibility Cutoff Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Exam Cutoff (Default 75%)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = glass.textPrimary)
                                Text("AKTU Examination Requirement", fontSize = 10.sp, color = glass.textMuted)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StepperButton(text = "-") {
                                    if (examCutoff > 65) {
                                        examCutoff--
                                        onProfileChanged(profile.copy(examTarget = examCutoff))
                                    }
                                }
                                Text(
                                    text = "$examCutoff%",
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = glass.royalIndigo,
                                    modifier = Modifier.width(36.dp),
                                    textAlign = TextAlign.Center
                                )
                                StepperButton(text = "+") {
                                    if (examCutoff < 90) {
                                        examCutoff++
                                        onProfileChanged(profile.copy(examTarget = examCutoff))
                                    }
                                }
                            }
                        }

                        // Stipend Eligibility Cutoff Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Stipend Cutoff (Default 80%)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = glass.textPrimary)
                                Text("IIHT Monthly Award Requirement", fontSize = 10.sp, color = glass.textMuted)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StepperButton(text = "-") {
                                    if (stipendCutoff > 70) {
                                        stipendCutoff--
                                        onProfileChanged(profile.copy(stipendTarget = stipendCutoff))
                                    }
                                }
                                Text(
                                    text = "$stipendCutoff%",
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = glass.emeraldNeon,
                                    modifier = Modifier.width(36.dp),
                                    textAlign = TextAlign.Center
                                )
                                StepperButton(text = "+") {
                                    if (stipendCutoff < 95) {
                                        stipendCutoff++
                                        onProfileChanged(profile.copy(stipendTarget = stipendCutoff))
                                    }
                                }
                            }
                        }

                        // Safety Buffer Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Forgetfulness & Error Buffer: +$safetyBuffer%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = glass.textPrimary
                                )
                                Text(
                                    "3% to 5%",
                                    fontSize = 10.sp,
                                    color = glass.textMuted
                                )
                            }
                            Slider(
                                value = safetyBuffer.toFloat(),
                                onValueChange = {
                                    safetyBuffer = it.toInt()
                                    onProfileChanged(profile.copy(safetyBuffer = safetyBuffer))
                                },
                                valueRange = 3f..5f,
                                steps = 1,
                                colors = SliderDefaults.colors(
                                    thumbColor = glass.amberGold,
                                    activeTrackColor = glass.amberGold
                                )
                            )

                            // Live 3-Tier Status Sign Preview Row
                            val safeThreshold = stipendCutoff + safetyBuffer
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                StatusSignChip(
                                    label = "Danger (< $stipendCutoff%)",
                                    color = glass.crimsonRose,
                                    modifier = Modifier.weight(1f)
                                )
                                StatusSignChip(
                                    label = "Safe ($stipendCutoff%–${safeThreshold - 0.1f}%)",
                                    color = glass.amberGold,
                                    modifier = Modifier.weight(1f)
                                )
                                StatusSignChip(
                                    label = "Completely Safe (≥ $safeThreshold%)",
                                    color = glass.emeraldNeon,
                                    modifier = Modifier.weight(1.2f)
                                )
                            }
                        }
                    }
                }

                // Card 3 — Daily Reminder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(glass.cardSurface)
                        .border(1.dp, glass.cardBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = glass.royalIndigo,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text("Daily Reminder", fontFamily = OutfitFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                                    Text("Get notified to mark your attendance", fontSize = 10.5.sp, color = glass.textMuted)
                                }
                            }
                            Switch(
                                checked = dailyReminder,
                                onCheckedChange = {
                                    dailyReminder = it
                                    onProfileChanged(profile.copy(dailyReminderEnabled = it))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = glass.royalIndigo
                                )
                            )
                        }

                        // Inner Clickable Time Pill Row
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (glass.isDark) Color(0xFF0F172A).copy(alpha = 0.6f) else com.example.ui.theme.LightSurfaceAlt)
                                .clickable {
                                    TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            val amPm = if (h >= 12) "PM" else "AM"
                                            val h12 = if (h % 12 == 0) 12 else h % 12
                                            dailyTime = String.format("%02d:%02d %s", h12, m, amPm)
                                        },
                                        16, 45, false
                                    ).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Time", fontSize = 11.5.sp, color = glass.textMuted)
                                Text("$dailyTime  ›", fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = glass.royalIndigo)
                            }
                        }
                    }
                }

                // Card 4 — Weekly Reminder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(glass.cardSurface)
                        .border(1.dp, glass.cardBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = glass.royalIndigo,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text("Weekly Reminder", fontFamily = OutfitFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                                    Text("Get a weekly summary of your attendance", fontSize = 10.5.sp, color = glass.textMuted)
                                }
                            }
                            Switch(
                                checked = weeklyReminder,
                                onCheckedChange = {
                                    weeklyReminder = it
                                    onProfileChanged(profile.copy(weeklyReminderEnabled = it))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = glass.royalIndigo
                                )
                            )
                        }

                        // Inner Clickable Day/Time Pill Row
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (glass.isDark) Color(0xFF0F172A).copy(alpha = 0.6f) else com.example.ui.theme.LightSurfaceAlt)
                                .clickable {
                                    // Cycles timing for demonstration
                                    weeklyDayTime = if (weeklyDayTime.startsWith("Friday")) "Saturday • 10:00 AM" else "Friday • 06:00 PM"
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Day & Time", fontSize = 11.5.sp, color = glass.textMuted)
                                Text("$weeklyDayTime  ›", fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = glass.royalIndigo)
                            }
                        }
                    }
                }

                // Card 5 — Main Objective
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(glass.cardSurface)
                        .border(1.dp, glass.cardBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = glass.royalIndigo,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text("Main Objective", fontFamily = OutfitFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                                Text("What do you want to achieve with this app?", fontSize = 10.5.sp, color = glass.textMuted)
                            }
                        }

                        listOf(
                            "Maintain my attendance target",
                            "Improve my attendance percentage",
                            "Plan leaves and bunk days",
                            "Understand my academic progress"
                        ).forEach { objText ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        mainObjective = objText
                                        onProfileChanged(profile.copy(mainObjective = objText))
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = mainObjective == objText,
                                    onClick = {
                                        mainObjective = objText
                                        onProfileChanged(profile.copy(mainObjective = objText))
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = glass.royalIndigo)
                                )
                                Text(
                                    text = objText,
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 12.sp,
                                    color = glass.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Bottom Primary CTA Button
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(12.dp, RoundedCornerShape(9999.dp), spotColor = glass.royalIndigo)
                .testTag("onboarding_finish_setup_button"),
            colors = ButtonDefaults.buttonColors(containerColor = glass.royalIndigo),
            shape = RoundedCornerShape(9999.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Finish Set-up",
                    fontFamily = OutfitFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit) {
    val glass = LocalGlassTheme.current
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(glass.royalIndigo.copy(alpha = 0.2f))
            .border(1.dp, glass.royalIndigo.copy(alpha = 0.4f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = glass.royalIndigo)
    }
}

@Composable
private fun StatusSignChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// ============================================================================
// SCREEN 5: WELCOME ABOARD, GOOGLE BACKUP & MID-MONTH BACKFILL
// ============================================================================
@Composable
fun OnboardingScreen5(
    profile: StudentProfile,
    onBack: () -> Unit,
    onOpenBackfill: () -> Unit,
    onComplete: () -> Unit
) {
    val glass = LocalGlassTheme.current
    val scrollState = rememberScrollState()

    // Floating mortarboard animation
    val infiniteTransition = rememberInfiniteTransition(label = "mortarboard_confetti")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(glass.cardSurface)
                        .border(1.dp, glass.cardBorder, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = glass.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Floating Mortarboard Icon (72.dp)
            Box(
                modifier = Modifier
                    .offset(y = floatOffset.dp)
                    .size(72.dp)
                    .shadow(16.dp, RoundedCornerShape(22.dp), spotColor = Color(0xFF7C3AED))
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFFEC4899))
                        )
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Greeting Line 1
            Text(
                text = "Welcome aboard,",
                fontFamily = OutfitFontFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            // Greeting Line 2: Dynamic Name with Lavender-Pink Gradient
            Text(
                text = "${profile.name.ifEmpty { "Bhawishya Kumar" }}! 👋",
                fontFamily = OutfitFontFamily,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFA78BFA),
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "Your college journey just got a little smarter. Track your attendance, understand your progress, plan your leaves wisely, and stay one step ahead.",
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 14.sp,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Handwritten Script Quote
            Text(
                text = "\"Every class counts.\nEvery day is a fresh opportunity.\"",
                fontFamily = CaveatFontFamily,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE0E7FF),
                textAlign = TextAlign.Center,
                modifier = Modifier.rotate(-2f)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Mid-Month Bulk Attendance Backfill Trigger Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(9999.dp), spotColor = Color(0xFFF59E0B))
                    .clip(RoundedCornerShape(9999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFF59E0B).copy(alpha = 0.25f), Color(0xFFD97706).copy(alpha = 0.25f))
                        )
                    )
                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f), RoundedCornerShape(9999.dp))
                    .clickable { onOpenBackfill() }
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⚡ Joined Mid-Month? Mark Past Days at Once",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFDE68A)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Frosted Glass Card: "Keep Your Data Safe"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(glass.cardSurface)
                    .border(1.dp, glass.cardBorder, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Keep Your Data Safe",
                        fontFamily = OutfitFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = "Sign in with Google to backup your data and access it across devices.",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )

                    // Button 1: Continue with Google (White Pill)
                    Button(
                        onClick = onComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(8.dp, RoundedCornerShape(9999.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(9999.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "G",
                                fontFamily = OutfitFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF4285F4)
                            )
                            Text(
                                text = "Continue with Google",
                                fontFamily = OutfitFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }

                    // Button 2: Skip for now (Outlined Glass Pill)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(9999.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(9999.dp))
                            .clickable { onComplete() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Skip for now",
                            fontFamily = OutfitFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    // Footer Micro-Label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Your data is secure and private",
                            fontSize = 11.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ============================================================================
// MID-MONTH BACKFILL BOTTOM SHEET MODAL
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MidMonthBackfillBottomSheet(
    onDismiss: () -> Unit,
    onApply: (fromDate: String, toDate: String, markAllPresent: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val glass = LocalGlassTheme.current

    // Current month, Monday-Friday, up to yesterday (or today on the 1st); never a hardcoded month.
    val backfillToday = remember { com.example.ui.viewmodel.AttendanceViewModel.liveToday() }
    val backfillFrom = remember(backfillToday) { backfillToday.withDayOfMonth(1) }
    val backfillTo = remember(backfillToday) {
        backfillToday.minusDays(1).let { if (it.isBefore(backfillFrom)) backfillToday else it }
    }
    val backfillMonthTitle = remember(backfillToday) {
        com.example.data.reports.ReportAnalyticsEngine.monthLabel(java.time.YearMonth.from(backfillToday))
    }
    val pastDays = remember(backfillFrom, backfillTo) {
        generateSequence(backfillFrom) { d -> d.plusDays(1).takeIf { !it.isAfter(backfillTo) } }
            .filter { it.dayOfWeek.value in 1..5 }
            .map { it.toString() to (if (com.example.data.holidays.AktuHolidays.isHoliday(it)) "H" else "P") }
            .toList()
    }

    val dayStatusMap = remember {
        mutableStateMapOf<String, String>().apply {
            pastDays.forEach { (d, s) -> put(d, s) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mid-Month Attendance Backfill",
                    fontFamily = OutfitFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = backfillMonthTitle,
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 12.sp,
                    color = Color(0xFFA78BFA)
                )
            }

            Text(
                text = "Mark past working days in $backfillMonthTitle up to $backfillTo. Official AKTU holidays are pre-locked as Holiday.",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 16.sp
            )

            // 2 Bulk Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        dayStatusMap.keys.forEach { d ->
                            if (!com.example.data.holidays.AktuHolidays.isHoliday(d)) {
                                dayStatusMap[d] = "P"
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981).copy(alpha = 0.2f)),
                    border = borderBrush(Color(0xFF10B981).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("✓ Mark All Present", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                }

                Button(
                    onClick = {
                        dayStatusMap.keys.forEach { d ->
                            if (!com.example.data.holidays.AktuHolidays.isHoliday(d)) {
                                dayStatusMap[d] = "A"
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)),
                    border = borderBrush(Color(0xFFEF4444).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("✕ Mark All Absent", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                }
            }

            // Grid of past weekday pills
            Text(
                text = "Tap any date to flip status (Present / Absent / Holiday):",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dayStatusMap.keys.chunked(5).forEach { rowDates ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowDates.forEach { dStr ->
                            val status = dayStatusMap[dStr] ?: "P"
                            val dayNum = dStr.split("-").last()
                            val (bg, textColor) = when (status) {
                                "P" -> Color(0xFF10B981).copy(alpha = 0.25f) to Color(0xFF10B981)
                                "A" -> Color(0xFFEF4444).copy(alpha = 0.25f) to Color(0xFFEF4444)
                                else -> Color(0xFF06B6D4).copy(alpha = 0.25f) to Color(0xFF06B6D4) // Holiday
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg)
                                    .border(1.dp, textColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        dayStatusMap[dStr] = when (status) {
                                            "P" -> "A"
                                            "A" -> "H"
                                            else -> "P"
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(dayNum, fontFamily = SpaceGroteskFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                                    Text(status, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save Backfill Action
            Button(
                onClick = {
                    onApply(backfillFrom.toString(), backfillTo.toString(), true)
                    scope.launch { sheetState.hide() }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .shadow(8.dp, RoundedCornerShape(9999.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = glass.royalIndigo),
                shape = RoundedCornerShape(9999.dp)
            ) {
                Text("Save Backfill Attendance", fontFamily = OutfitFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun borderBrush(color: Color): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(1.dp, color)
}
