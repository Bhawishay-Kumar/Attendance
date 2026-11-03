package com.example.ui.countdown

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin as OutlinedPushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.milestone.CountdownStatus
import com.example.data.milestone.MilestoneNotifications
import com.example.data.milestone.MilestonePresets
import com.example.data.milestone.ResolvedMilestone
import com.example.data.model.MilestoneDateSource
import com.example.data.model.MilestoneRecurrence
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.MilestoneSheetRequest
import com.example.ui.viewmodel.MilestoneViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.absoluteValue

/**
 * Academic Milestones on Home. Empty -> compact "add" card; otherwise a one-card-at-a-time swipeable
 * carousel. Replaces the old demo CountdownSection (which used made-up relative dates).
 */
@Composable
fun CountdownSection(
    dateOfBirth: String,
    onOpenProfile: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MilestoneViewModel = viewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val focusId by viewModel.focusId.collectAsStateWithLifecycle()
    val celebrateId by viewModel.celebrateId.collectAsStateWithLifecycle()
    val colors = rememberMilestoneColors()
    val motion = rememberMotionEnabled()

    LaunchedEffect(dateOfBirth) { viewModel.onProfileDobChanged(dateOfBirth) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { onMessage(it) } }
    TodayTicker(onChange = viewModel::refreshToday)

    val panelShape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(panelShape)
            .auroraBackground(colors, motion)
            .border(1.dp, Brush.verticalGradient(listOf(colors.sheen, colors.border)), panelShape)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        when {
            ui.loading -> {
                SectionHeader(colors = colors, actions = null)
                LoadingCard(colors)
            }
            ui.items.isEmpty() -> {
                SectionHeader(colors = colors, actions = null)
                EmptyMilestoneCard(colors = colors, onAdd = viewModel::openAdd)
            }
            else -> MilestoneCarousel(
                items = ui.items,
                focusId = focusId,
                celebrateId = celebrateId,
                onCelebrationDone = viewModel::clearCelebration,
                colors = colors,
                motion = motion,
                onFocusConsumed = viewModel::consumeFocus,
                onAdd = viewModel::openAdd,
                onTogglePin = viewModel::togglePin,
                onEdit = viewModel::openEdit,
                onDelete = viewModel::requestDelete,
                onToggleCompleted = viewModel::toggleCompleted
            )
        }
    }

    MilestoneDialogs(
        viewModel = viewModel,
        dateOfBirth = dateOfBirth,
        onOpenProfile = onOpenProfile,
        onMessage = onMessage
    )
}

// ------------------------------------------------------------------ host: sheet + dialogs

@Composable
private fun MilestoneDialogs(
    viewModel: MilestoneViewModel,
    dateOfBirth: String,
    onOpenProfile: () -> Unit,
    onMessage: (String) -> Unit
) {
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val showSuccess by viewModel.showSuccess.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) onMessage("Reminders stay silent until notifications are allowed")
    }
    val ensureNotificationAccess = {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!MilestoneNotifications.canPost(context)) {
            onMessage("Notifications are turned off for this app, so reminders won't show")
        }
    }

    val request = sheet
    if (request != null) {
        val editing = (request as? MilestoneSheetRequest.Edit)?.let { viewModel.find(it.id) }
        if (request is MilestoneSheetRequest.Edit && editing == null) {
            LaunchedEffect(request) { viewModel.closeSheet() }
        } else {
            AddMilestoneSheet(
                editing = editing,
                dateOfBirth = dateOfBirth,
                birthdayExists = viewModel.hasActive(MilestonePresets.BIRTHDAY),
                onDismiss = viewModel::closeSheet,
                // The sheet animates itself closed after these return, then calls onDismiss.
                onSave = { draft ->
                    val error = viewModel.save(draft)
                    if (error == null && draft.reminderDays.isNotEmpty()) ensureNotificationAccess()
                    error
                },
                onAddBirthday = { viewModel.addBirthdayFromProfile(dateOfBirth) },
                onOpenProfile = {
                    viewModel.closeSheet()
                    onOpenProfile()
                }
            )
        }
    }

    // Show the popup only after the sheet has finished sliding away, so the two never overlap.
    if (showSuccess && sheet == null) {
        MilestoneSuccessDialog(onDismiss = viewModel::dismissSuccess)
    }

    pendingDelete?.let { id ->
        val title = viewModel.find(id)?.title ?: "this milestone"
        ConfirmDeleteDialog(title = title, onConfirm = viewModel::confirmDelete, onCancel = viewModel::cancelDelete)
    }
}

// ------------------------------------------------------------------ header

@Composable
private fun SectionHeader(
    colors: MilestoneColors,
    actions: (@Composable () -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp).height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = colors.accent(com.example.data.model.MilestoneAccent.AMBER), modifier = Modifier.size(16.dp))
            Text(
                text = "Academic Milestones",
                fontFamily = OutfitFontFamily,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        actions?.invoke()
    }
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .pressableScale(pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

// ------------------------------------------------------------------ cards: loading / empty

@Composable
private fun LoadingCard(colors: MilestoneColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(22.dp))
            .semantics { contentDescription = "Loading milestones" }
    )
}

@Composable
private fun EmptyMilestoneCard(colors: MilestoneColors, onAdd: () -> Unit) {
    val violet = colors.accent(com.example.data.model.MilestoneAccent.VIOLET)
    val blue = colors.accent(com.example.data.model.MilestoneAccent.BLUE)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(colors.surface, lerp(colors.surface, violet, 0.10f))))
            .border(1.dp, colors.border, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Your important dates, all in one place.",
                    fontFamily = PlusJakartaSansFontFamily,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Text(
                    text = "0 Milestones Added",
                    fontFamily = SpaceGroteskFontFamily,
                    color = violet,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(violet.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
            Canvas(modifier = Modifier.size(width = 76.dp, height = 56.dp)) {
                // Lightweight static illustration: two hills and a flag.
                val hill = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width * 0.34f, size.height * 0.30f)
                    lineTo(size.width * 0.62f, size.height)
                    close()
                }
                drawPath(hill, Brush.verticalGradient(listOf(violet.copy(alpha = 0.55f), violet.copy(alpha = 0.12f))))
                val hill2 = Path().apply {
                    moveTo(size.width * 0.35f, size.height)
                    lineTo(size.width * 0.70f, size.height * 0.48f)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(hill2, Brush.verticalGradient(listOf(blue.copy(alpha = 0.50f), blue.copy(alpha = 0.10f))))
                val poleX = size.width * 0.34f
                drawLine(violet, Offset(poleX, size.height * 0.30f), Offset(poleX, size.height * 0.02f), strokeWidth = 2.5f, cap = StrokeCap.Round)
                val flag = Path().apply {
                    moveTo(poleX, size.height * 0.02f)
                    lineTo(poleX + size.width * 0.18f, size.height * 0.10f)
                    lineTo(poleX, size.height * 0.18f)
                    close()
                }
                drawPath(flag, colors.accent(com.example.data.model.MilestoneAccent.PINK))
            }
        }
        GradientButton(text = "Add Milestone", icon = Icons.Rounded.Add, onClick = onAdd, pulse = true)
    }
}

// ------------------------------------------------------------------ carousel

@Composable
private fun MilestoneCarousel(
    items: List<ResolvedMilestone>,
    focusId: String?,
    celebrateId: String?,
    onCelebrationDone: (String) -> Unit,
    colors: MilestoneColors,
    motion: Boolean,
    onFocusConsumed: () -> Unit,
    onAdd: () -> Unit,
    onTogglePin: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onToggleCompleted: (String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    val latestItems by rememberUpdatedState(items)
    var trackedId by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Remember which milestone is on screen so pinning/reordering/deleting keeps the user on it.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page -> trackedId = latestItems.getOrNull(page)?.id }
    }
    val idKey = items.joinToString(",") { it.id }
    LaunchedEffect(idKey, focusId) {
        val wanted = focusId ?: trackedId
        val index = items.indexOfFirst { it.id == wanted }
        if (index >= 0 && index != pagerState.currentPage) {
            if (focusId != null && motion) pagerState.animateScrollToPage(index) else pagerState.scrollToPage(index)
        }
        // Only consume the request once the milestone is actually in the list; otherwise a save that
        // reaches the UI a moment late would silently lose its "scroll to the new card" request.
        if (focusId != null && index >= 0) onFocusConsumed()
    }

    val current = items.getOrNull(pagerState.currentPage.coerceIn(0, items.lastIndex)) ?: return
    val m = current.milestone

    SectionHeader(colors = colors) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderIconButton(Icons.Rounded.Add, "Add milestone", colors.textSecondary, onClick = onAdd)
            PinToggle(pinned = m.pinned, colors = colors, motion = motion) { onTogglePin(m.id) }
            Box {
                HeaderIconButton(Icons.Rounded.MoreVert, "More options", colors.textSecondary) { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (m.pinned) "Unpin" else "Pin") },
                        leadingIcon = { Icon(if (m.pinned) Icons.Outlined.OutlinedPushPin else Icons.Rounded.PushPin, null) },
                        onClick = { menuOpen = false; onTogglePin(m.id) }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menuOpen = false; onEdit(m.id) }
                    )
                    if (m.recurrence == MilestoneRecurrence.NONE) {
                        DropdownMenuItem(
                            text = { Text(if (m.completed) "Reopen" else "Mark completed") },
                            leadingIcon = { Icon(if (m.completed) Icons.Rounded.Replay else Icons.Rounded.CheckCircle, null) },
                            onClick = { menuOpen = false; onToggleCompleted(m.id) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete", color = colors.danger) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = colors.danger) },
                        onClick = { menuOpen = false; onDelete(m.id) }
                    )
                }
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth(),
        pageSpacing = 12.dp,
        userScrollEnabled = !menuOpen && items.size > 1,
        key = { index -> items.getOrNull(index)?.id ?: index },
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            pagerSnapDistance = PagerSnapDistance.atMost(1), // one card per swipe
            snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        )
    ) { page ->
        val item = items.getOrNull(page)
        if (item != null) {
            MilestoneCard(
                item = item,
                colors = colors,
                motion = motion,
                celebrate = celebrateId == item.id,
                onCelebrationDone = { onCelebrationDone(item.id) },
                modifier = Modifier.graphicsLayer {
                    val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        .absoluteValue.coerceIn(0f, 1f)
                    val s = 1f - 0.06f * offset
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.45f * offset
                }
            )
        }
    }

    if (items.size > 1) {
        Spacer(Modifier.height(3.dp))
        PagerDots(
            count = items.size,
            current = pagerState.currentPage,
            accentAt = { colors.accent(items[it].milestone.accent) },
            inactive = colors.textMuted.copy(alpha = 0.35f),
            motion = motion,
            onSelect = { index ->
                scope.launch {
                    if (motion) pagerState.animateScrollToPage(index) else pagerState.scrollToPage(index)
                }
            }
        )
        Text(
            text = "Swipe left or right",
            fontFamily = PlusJakartaSansFontFamily,
            color = colors.textMuted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        )
    }
}

// ------------------------------------------------------------------ pin toggle + dots

@Composable
private fun PinToggle(pinned: Boolean, colors: MilestoneColors, motion: Boolean, onClick: () -> Unit) {
    val tilt by animateFloatAsState(
        targetValue = if (pinned) 0f else 35f,
        animationSpec = if (motion) spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium) else tween(0),
        label = "pinTilt"
    )
    val grow by animateFloatAsState(
        targetValue = if (pinned) 1.12f else 1f,
        animationSpec = if (motion) spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium) else tween(0),
        label = "pinGrow"
    )
    val tint by animateColorAsState(
        targetValue = if (pinned) colors.accent(com.example.data.model.MilestoneAccent.VIOLET) else colors.textSecondary,
        animationSpec = tween(if (motion) 250 else 0),
        label = "pinTint"
    )
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .pressableScale(pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (pinned) Icons.Rounded.PushPin else Icons.Outlined.OutlinedPushPin,
            contentDescription = if (pinned) "Unpin milestone" else "Pin milestone",
            tint = tint,
            modifier = Modifier.size(20.dp).rotate(tilt).scale(grow)
        )
    }
}

@Composable
private fun PagerDots(
    count: Int,
    current: Int,
    accentAt: (Int) -> Color,
    inactive: Color,
    motion: Boolean,
    onSelect: (Int) -> Unit
) {
    val maxDots = 7
    val start = if (count <= maxDots) 0 else (current - maxDots / 2).coerceIn(0, count - maxDots)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in start until minOf(count, start + maxDots)) {
            val active = i == current
            val width by animateDpAsState(
                targetValue = if (active) 18.dp else 6.dp,
                animationSpec = if (motion) spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium) else tween(0),
                label = "dotWidth"
            )
            val color by animateColorAsState(
                targetValue = if (active) accentAt(i) else inactive,
                animationSpec = tween(if (motion) 250 else 0),
                label = "dotColor"
            )
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 3.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Go to milestone ${i + 1}"
                    ) { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.height(6.dp).width(width).clip(CircleShape).background(color))
            }
        }
    }
}

// ------------------------------------------------------------------ the milestone card

/**
 * Wraps the card with its "just created" celebration: a spring pop-in, an expanding accent ring and
 * a one-shot confetti burst. Cards that were not just created render with no extra animation state.
 */
@Composable
private fun MilestoneCard(
    item: ResolvedMilestone,
    colors: MilestoneColors,
    motion: Boolean,
    celebrate: Boolean,
    onCelebrationDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = colors.accent(item.milestone.accent)
    val pop = remember { Animatable(1f) }
    val ring = remember { Animatable(1f) }
    var showConfetti by remember { mutableStateOf(false) }
    LaunchedEffect(celebrate) {
        if (celebrate && motion) {
            showConfetti = true
            pop.snapTo(0.86f)
            ring.snapTo(0f)
            launch { pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 280f)) }
            ring.animateTo(1f, tween(900, easing = LinearOutSlowInEasing))
        } else if (celebrate) {
            onCelebrationDone()
        }
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .drawBehind {
                val r = ring.value
                if (r in 0.001f..0.999f) {
                    val grow = 18.dp.toPx() * r
                    drawRoundRect(
                        color = accent.copy(alpha = 0.75f * (1f - r)),
                        topLeft = Offset(-grow, -grow),
                        size = androidx.compose.ui.geometry.Size(size.width + grow * 2, size.height + grow * 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx() + grow),
                        style = Stroke(width = 2.5.dp.toPx() * (1f - r) + 0.5f)
                    )
                }
            }
    ) {
        MilestoneCardBody(item = item, colors = colors, motion = motion)
        if (showConfetti) {
            ConfettiBurst(
                palette = listOf(
                    accent,
                    colors.accent(com.example.data.model.MilestoneAccent.PINK),
                    colors.accent(com.example.data.model.MilestoneAccent.AMBER),
                    colors.accent(com.example.data.model.MilestoneAccent.BLUE),
                    colors.accent(com.example.data.model.MilestoneAccent.GREEN)
                ),
                modifier = Modifier.matchParentSize(),
                onFinished = {
                    showConfetti = false
                    onCelebrationDone()
                }
            )
        }
    }
}

@Composable
private fun MilestoneCardBody(
    item: ResolvedMilestone,
    colors: MilestoneColors,
    motion: Boolean,
    modifier: Modifier = Modifier
) {
    val m = item.milestone
    val accent = colors.accent(m.accent)
    val shape = RoundedCornerShape(22.dp)
    val preset = MilestonePresets.byKey(m.presetKey)

    // One-shot light sweep each time a card is first drawn.
    val sheen = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motion) {
            delay(200)
            sheen.animateTo(1f, tween(1000, easing = LinearEasing))
        }
    }

    val target = (item.status as? CountdownStatus.DaysLeft)?.days?.toInt()
        ?: (item.status as? CountdownStatus.DaysPassed)?.days?.toInt()
    val shown = if (target != null) rememberCountUp(target, motion) else 0
    val bigText = if (target != null) shown.toString() else item.status.bigText()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.surface, lerp(colors.surface, accent, 0.12f))))
            .border(
                1.dp,
                Brush.linearGradient(listOf(colors.sheen, accent.copy(alpha = 0.45f), colors.border)),
                shape
            )
            .drawWithContent {
                drawContent()
                val p = sheen.value
                if (p in 0.001f..0.999f) {
                    val x = -size.width * 0.3f + size.width * 1.6f * p
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = if (colors.isDark) 0.10f else 0.35f), Color.Transparent),
                            startX = x,
                            endX = x + size.width * 0.3f
                        )
                    )
                }
            }
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = if (colors.isDark) 0.20f else 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.30f),
                        radius = 110.dp.toPx()
                    ),
                    radius = 110.dp.toPx(),
                    center = Offset(size.width * 0.88f, size.height * 0.30f)
                )
            }
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(m.title).append(", ").append(item.status.spokenText())
                    append(", ").append(item.date.cardText())
                    if (m.pinned) append(", pinned")
                }
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.10f))))
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(m.category.icon(), contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = m.title,
                    fontFamily = OutfitFontFamily,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = m.description.ifBlank { m.category.label },
                    fontFamily = PlusJakartaSansFontFamily,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = m.category.label.uppercase(),
                    fontFamily = SpaceGroteskFontFamily,
                    color = accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.07f))))
                    .border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (item.status is CountdownStatus.Completed) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
                    } else {
                        Text(
                            text = bigText,
                            fontFamily = SpaceGroteskFontFamily,
                            color = colors.textPrimary,
                            fontSize = if (item.status is CountdownStatus.Today) 17.sp else 26.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                    }
                    val caption = item.status.caption()
                    if (caption.isNotEmpty()) {
                        Text(
                            text = caption,
                            fontFamily = SpaceGroteskFontFamily,
                            color = accent,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(15.dp))
            Text(
                text = item.date.cardText(),
                fontFamily = PlusJakartaSansFontFamily,
                color = colors.textSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (m.recurrence != MilestoneRecurrence.NONE) {
                Icon(Icons.Rounded.Repeat, contentDescription = m.recurrence.label, tint = colors.textMuted, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.weight(1f))
            if (preset?.officialCapable == true) {
                val official = m.dateSource == MilestoneDateSource.OFFICIAL
                SourcePill(
                    text = if (official) "Official date" else "Manual date",
                    tint = if (official) colors.success else colors.textMuted,
                    verified = official
                )
            } else if (m.dateSource == MilestoneDateSource.PROFILE) {
                SourcePill(text = "From profile", tint = colors.textMuted, verified = false)
            }
            if (m.pinned) {
                Icon(Icons.Rounded.PushPin, contentDescription = "Pinned", tint = accent, modifier = Modifier.size(15.dp))
            }
        }

        item.progress?.let { p ->
            val animated by animateFloatAsState(
                targetValue = p,
                animationSpec = tween(if (motion) 700 else 0),
                label = "milestoneProgress"
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape).background(colors.track)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animated.coerceAtLeast(0.02f))
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.6f))))
                    )
                }
                Text(
                    text = "${(p * 100).toInt()}%",
                    fontFamily = SpaceGroteskFontFamily,
                    color = colors.textSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SourcePill(text: String, tint: Color, verified: Boolean) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (verified) Icon(Icons.Rounded.Verified, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
        Text(text = text, fontFamily = PlusJakartaSansFontFamily, color = tint, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Counts up to [target] when the card first appears and animates between values after edits. */
@Composable
private fun rememberCountUp(target: Int, motion: Boolean): Int {
    var goal by remember { mutableIntStateOf(if (motion) 0 else target) }
    LaunchedEffect(target) { goal = target }
    val value by animateIntAsState(
        targetValue = goal,
        animationSpec = tween(if (motion) 700 else 0),
        label = "countUp"
    )
    return value
}

// ------------------------------------------------------------------ keeps "days left" correct

/** Re-evaluates today's date at midnight, on resume, and when the clock/time zone changes. */
@Composable
private fun TodayTicker(onChange: () -> Unit) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onChange)

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { latest() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    LifecycleResumeEffect(Unit) {
        latest()
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
            delay(Duration.between(now, nextMidnight).toMillis() + 1_000L)
            latest()
        }
    }
}
