package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.Preset
import com.example.data.SleepLog
import com.example.data.SoundRepository
import com.example.data.SoundTrackState
import com.example.data.TrackType
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundSlumberApp(
    viewModel: MainViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedCategory by remember { mutableStateOf("All") }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }

    val categories = listOf("All", "Nature", "Noise", "Home", "Ambient")

    Scaffold(
        bottomBar = {
            if (uiState.currentTab != 2) { // Hide footer in OLED Clock mode
                BottomControlSheet(
                    uiState = uiState,
                    onTogglePlayback = { viewModel.togglePlayback() },
                    onMasterVolumeChange = { viewModel.setMasterVolume(it) },
                    onAddTimerMinutes = { viewModel.addTimerMinutes(it) },
                    onResetTimer = { viewModel.resetTimer() },
                    onOpenTimerDialog = { showTimerDialog = true }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
            // Header bar
            if (uiState.currentTab != 2) {
                TopHeaderBar(
                    activeCount = uiState.activeSoundCount,
                    selectedTab = uiState.currentTab,
                    onSelectTab = { viewModel.setTab(it) }
                )
            }

            // Main Tab Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (uiState.currentTab) {
                    0 -> MixerTabContent(
                        tracks = uiState.tracks,
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategory = it },
                        onVolumeChange = { type, vol -> viewModel.updateTrackVolume(type, vol) },
                        onToggleMute = { viewModel.toggleMuteTrack(it) },
                        onLongPressFade = { viewModel.fadeTrackOutOver3Seconds(it) },
                        isPowerSaveEnabled = uiState.isPowerSaveEnabled
                    )
                    1 -> PresetsTabContent(
                        uiState = uiState,
                        onApplyPreset = { viewModel.applyPreset(it) },
                        onOpenSaveDialog = { showSavePresetDialog = true },
                        onDeletePreset = { viewModel.deleteCustomPreset(it) },
                        onExportPreset = { viewModel.exportPresetAsLink(it) },
                        onImportLink = { viewModel.importPresetFromLink(it) },
                        onClearNotices = { viewModel.clearNotices() }
                    )
                    2 -> OledClockScreen(
                        uiState = uiState,
                        onExitClockMode = { viewModel.setTab(0) },
                        onTogglePlayback = { viewModel.togglePlayback() }
                    )
                    3 -> SleepHistoryTabContent(
                        uiState = uiState,
                        onClearHistory = { viewModel.clearHistory() },
                        onRefreshBattery = { viewModel.refreshBatteryStatus() }
                    )
                    4 -> SettingsTabContent(
                        uiState = uiState,
                        onThemeChanged = { viewModel.setThemeStyle(it) },
                        onToggleAutoCap = { viewModel.toggleAutoCapVolume() },
                        onTogglePowerSave = { viewModel.togglePowerSave() },
                        onToggleNormalization = { viewModel.toggleNormalization() },
                        onToggleGentleWake = { viewModel.toggleGentleWake() }
                    )
                    5 -> QuickStartTabContent(
                        uiState = uiState,
                        onPlayRandom = { viewModel.playRandomMix() },
                        onTogglePlayback = { viewModel.togglePlayback() }
                    )
                }
                val suggestionToShow = uiState.suggestionToShow
                if (suggestionToShow != null) {
                    SuggestionDialog(
                        type = suggestionToShow,
                        onDismiss = { viewModel.dismissSuggestion() },
                        onConfirm = { viewModel.dismissSuggestion() }
                    )
                }
            }

            if (uiState.isGentleWaking) {
                GentleWakeOverlay(
                    progress = uiState.gentleWakeProgress,
                    onDismiss = { viewModel.dismissGentleWake() }
                )
            }

            if (uiState.showOnboarding) {
                OnboardingOverlay(
                    onComplete = { viewModel.completeOnboarding() }
                )
            }
        }
    }
}

    if (showSavePresetDialog) {
        SavePresetDialog(
            onDismiss = { showSavePresetDialog = false },
            onSave = { name ->
                viewModel.saveCurrentMixAsPreset(name)
                showSavePresetDialog = false
            }
        )
    }

    if (showTimerDialog) {
        SleepTimerDialog(
            uiState = uiState,
            onDismiss = { showTimerDialog = false },
            onSetTimer = { mins -> viewModel.setTimerMinutes(mins) },
            onAddTimerMinutes = { mins -> viewModel.addTimerMinutes(mins) },
            onResetTimer = { viewModel.resetTimer() }
        )
    }
}

@Composable
fun OnboardingOverlay(
    onComplete: () -> Unit
) {
    var currentPage by remember { mutableStateOf(0) }

    val onboardingGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1B182B), // Very deep violet
            Color(0xFF0A090F)  // Midnight space black
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(onboardingGradient)
            .testTag("onboarding_screen")
    ) {
        // Decorative background stars/nebula
        Canvas(modifier = Modifier.fillMaxSize()) {
            val random = java.util.Random(42)
            for (i in 0 until 40) {
                val x = random.nextFloat() * size.width
                val y = random.nextFloat() * size.height
                val radius = random.nextFloat() * 2f + 1f
                val alpha = random.nextFloat() * 0.5f + 0.3f
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(x, y)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Title / App Name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SoundSlumber",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp
                    )
                )
                
                if (currentPage < 2) {
                    TextButton(
                        onClick = onComplete,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.6f)),
                        modifier = Modifier.testTag("onboarding_skip_button")
                    ) {
                        Text(
                            text = stringResource(id = R.string.onboarding_skip),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            // Slide content with transition animation
            Crossfade(
                targetState = currentPage,
                animationSpec = tween(durationMillis = 400),
                label = "onboarding_fade",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (page) {
                        0 -> {
                            // Page 1: Welcome & Value Prop
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(160.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(72.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(40.dp))

                            Text(
                                text = stringResource(id = R.string.onboarding_title_1),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(id = R.string.onboarding_desc_1),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color.White.copy(alpha = 0.7f),
                                    lineHeight = 24.sp
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                        1 -> {
                            // Page 2: Power Save & Normalization
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(vertical = 16.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BatteryChargingFull,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(40.dp))

                            Text(
                                text = stringResource(id = R.string.onboarding_title_2),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(id = R.string.onboarding_desc_2),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color.White.copy(alpha = 0.7f),
                                    lineHeight = 24.sp
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                        2 -> {
                            // Page 3: Gentle Wake Mode
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(160.dp)
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFFFFB74D).copy(alpha = 0.3f),
                                                Color.Transparent
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(110.dp)
                                        .background(
                                            color = Color(0xFFFFB74D).copy(alpha = 0.2f),
                                            shape = CircleShape
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = Color(0xFFFFB74D).copy(alpha = 0.5f),
                                            shape = CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WbSunny,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB74D),
                                        modifier = Modifier.size(54.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(40.dp))

                            Text(
                                text = stringResource(id = R.string.onboarding_title_3),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(id = R.string.onboarding_desc_3),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color.White.copy(alpha = 0.7f),
                                    lineHeight = 24.sp
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }

            // Footer: Pager Indicators & Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Indicator dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 24.dp)
                ) {
                    repeat(3) { index ->
                        val isSelected = currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 8.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "dot_width"
                        )
                        Box(
                            modifier = Modifier
                                .size(height = 8.dp, width = width)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.White.copy(alpha = 0.3f)
                                )
                                .testTag("onboarding_indicator_${index}")
                        )
                    }
                }

                // CTA Button
                Button(
                    onClick = {
                        if (currentPage < 2) {
                            currentPage++
                        } else {
                            onComplete()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag(if (currentPage < 2) "onboarding_next_button" else "onboarding_start_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentPage == 2) Color(0xFFFFB74D) else MaterialTheme.colorScheme.primary,
                        contentColor = if (currentPage == 2) Color(0xFF1E1E2C) else MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = if (currentPage < 2) {
                            stringResource(id = R.string.onboarding_next)
                        } else {
                            stringResource(id = R.string.onboarding_start)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun TopHeaderBar(
    activeCount: Int,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Sound",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 24.sp
                        )
                    )
                    Text(
                        text = "Slumber",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 24.sp
                        )
                    )
                }
                Text(
                    text = "$activeCount SOUNDS ACTIVE • OFFLINE DSP",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // OLED Clock Mode quick toggle button
            IconButton(
                onClick = { onSelectTab(2) },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .testTag("oled_clock_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = "OLED Clock Mode",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val tabs = listOf(
                "Mixer" to Icons.Default.Tune,
                "Presets" to Icons.Default.BookmarkBorder,
                "Clock" to Icons.Default.AccessTime,
                "Stats" to Icons.Default.BarChart,
                "Settings" to Icons.Default.Settings,
                "Quick" to Icons.Default.PlayArrow
            )

            tabs.forEachIndexed { index, pair ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .clickable { onSelectTab(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = pair.second,
                            contentDescription = pair.first,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = pair.first,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MixerTabContent(
    tracks: List<SoundTrackState>,
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onVolumeChange: (TrackType, Float) -> Unit,
    onToggleMute: (TrackType) -> Unit,
    onLongPressFade: (TrackType) -> Unit = {},
    isPowerSaveEnabled: Boolean = false
) {
    val filteredTracks = remember(selectedCategory, tracks) {
        if (selectedCategory == "All") {
            tracks
        } else {
            tracks.filter { it.type.category.equals(selectedCategory, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Category Pills
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                val isSelected = selectedCategory == category
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape
                        )
                        .clickable { onCategorySelected(category) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tracks List
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredTracks, key = { it.type.id }) { trackState ->
                TrackVolumeCard(
                    trackState = trackState,
                    onVolumeChange = { vol -> onVolumeChange(trackState.type, vol) },
                    onToggleMute = { onToggleMute(trackState.type) },
                    onLongPressFade = { onLongPressFade(trackState.type) },
                    isPowerSaveEnabled = isPowerSaveEnabled
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackVolumeCard(
    trackState: SoundTrackState,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onLongPressFade: () -> Unit = {},
    isPowerSaveEnabled: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val isActive = trackState.effectiveVolume > 0.01f

    val pulseAlpha = if (isPowerSaveEnabled) {
        1.0f
    } else {
        val transition = rememberInfiniteTransition(label = "pulse")
        val alpha by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
        alpha
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isActive) MaterialTheme.colorScheme.outline else Color.Transparent,
                RoundedCornerShape(20.dp)
            )
            .testTag("track_card_${trackState.type.id}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pulsing active dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f).copy(alpha = 0.3f)
                            )
                            .then(
                                if (isActive) Modifier.shadow(
                                    4.dp,
                                    CircleShape,
                                    ambientColor = MaterialTheme.colorScheme.primary,
                                    spotColor = MaterialTheme.colorScheme.primary
                                ) else Modifier
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))

                    // Tactile icon supporting tap-to-mute and hold-to-fade
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
                            .combinedClickable(
                                onClick = { onToggleMute() },
                                onLongClick = {
                                    if (isActive) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onLongPressFade()
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = trackState.type.icon,
                            contentDescription = stringResource(id = R.string.track_card_icon_desc),
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = trackState.type.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isActive) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${(trackState.effectiveVolume * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleMute()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (trackState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Mute track",
                            tint = if (trackState.isMuted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Custom Slider with subtle haptic feedback
            Slider(
                value = trackState.volume,
                onValueChange = { vol ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onVolumeChange(vol)
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    activeTrackColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f).copy(alpha = 0.3f),
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .testTag("slider_${trackState.type.id}")
            )
        }
    }
}

@Composable
fun PresetsTabContent(
    uiState: MainUiState,
    onApplyPreset: (Preset) -> Unit,
    onOpenSaveDialog: () -> Unit,
    onDeletePreset: (Preset) -> Unit,
    onExportPreset: (Preset) -> Unit,
    onImportLink: (String) -> Unit,
    onClearNotices: () -> Unit
) {
    var linkInputText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Import & Export Status Banner
        if (uiState.exportShareText != null || uiState.importNoticeMessage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (uiState.exportShareText != null) "PRESET LINK COPIED!" else "IMPORT NOTICE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.5.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            IconButton(
                                onClick = onClearNotices,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.exportShareText ?: uiState.importNoticeMessage ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }
        }

        // Save Current Mix Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenSaveDialog()
                    }
                    .testTag("save_preset_card")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = "Save Mix",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Save Current Soundscape",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Store your favorite multi-track volume balance",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Import Preset Link Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "IMPORT PRESET FROM LINK",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = linkInputText,
                        onValueChange = { linkInputText = it },
                        placeholder = { Text("Paste preset URL here...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                val clip = clipboardManager.getText()?.text?.toString()
                                if (!clip.isNullOrEmpty()) {
                                    linkInputText = clip
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste Clipboard",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (linkInputText.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onImportLink(linkInputText)
                                linkInputText = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import & Play Preset")
                    }
                }
            }
        }

        // Custom Saved Presets
        if (uiState.customPresets.isNotEmpty()) {
            item {
                Text(
                    text = "MY SAVED SOUNDSCAPES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(uiState.customPresets, key = { it.id }) { preset ->
                PresetCard(
                    preset = preset,
                    isSelected = uiState.selectedPresetName == preset.name,
                    isCustom = true,
                    onApply = { onApplyPreset(preset) },
                    onDelete = { onDeletePreset(preset) },
                    onShare = { onExportPreset(preset) }
                )
            }
        }

        // Built-in Presets
        item {
            Text(
                text = "CURATED SLEEP PRESETS",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        items(SoundRepository.BUILT_IN_PRESETS, key = { it.id }) { preset ->
            PresetCard(
                preset = preset,
                isSelected = uiState.selectedPresetName == preset.name,
                isCustom = false,
                onApply = { onApplyPreset(preset) },
                onDelete = {},
                onShare = { onExportPreset(preset) }
            )
        }
    }
}

@Composable
fun PresetCard(
    preset: Preset,
    isSelected: Boolean,
    isCustom: Boolean,
    onApply: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val gradientColors = remember(preset.id, preset.name) {
        when {
            preset.name.contains("Rain", ignoreCase = true) -> listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0284C7))
            preset.name.contains("Sleep", ignoreCase = true) || preset.name.contains("Brown", ignoreCase = true) -> listOf(Color(0xFF181024), Color(0xFF2E1065), Color(0xFF5B21B6))
            preset.name.contains("Hearth", ignoreCase = true) || preset.name.contains("Fire", ignoreCase = true) -> listOf(Color(0xFF1C0A00), Color(0xFF451A03), Color(0xFF9A3412))
            preset.name.contains("Nature", ignoreCase = true) || preset.name.contains("Forest", ignoreCase = true) -> listOf(Color(0xFF022C22), Color(0xFF065F46), Color(0xFF047857))
            else -> listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF312E81))
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                2.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(22.dp)
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onApply()
            }
            .testTag("preset_card_${preset.id}")
    ) {
        Column {
            // Visual Banner Header for Preset
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(Brush.horizontalGradient(gradientColors))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        moveTo(0f, h * 0.7f)
                        cubicTo(w * 0.25f, h * 0.2f, w * 0.5f, h * 0.9f, w * 0.75f, h * 0.3f)
                        lineTo(w, h * 0.6f)
                    }
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.2f),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = if (isCustom) "Personal Mix Preset" else "Curated Soundscape",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onShare()
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.3f))
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share preset link",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (isCustom) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.3f))
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete preset",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Track volume summary chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                preset.volumes.filter { it.value > 0f }.forEach { (trackType, vol) ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = trackType.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${trackType.title} ${(vol * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OledClockScreen(
    uiState: MainUiState,
    onExitClockMode: () -> Unit,
    onTogglePlayback: () -> Unit
) {
    var currentTimeString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            currentTimeString = formatter.format(Date())
            delay(1000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable { onExitClockMode() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Faint Ambient Pulsing Circle
            val transition = rememberInfiniteTransition(label = "glow")
            val glowScale by transition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.03f * glowScale))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = currentTimeString.ifEmpty { "22:45" },
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 54.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraLight,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            letterSpacing = (-2).sp
                        )
                    )

                    if (uiState.isTimerActive) {
                        val mins = uiState.timerRemainingSeconds / 60
                        val secs = uiState.timerRemainingSeconds % 60
                        val timerStr = String.format(Locale.US, "%02d:%02d", mins, secs)

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "OFF IN $timerStr",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                                letterSpacing = 2.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = if (uiState.isPlaying) "Playing • Tap anywhere to return" else "Paused • Tap anywhere to return",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f).copy(alpha = 0.5f),
                    letterSpacing = 1.sp
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            IconButton(
                onClick = onTogglePlayback,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Toggle play",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun RechartsStyleWeeklyTrendChart(
    sleepLogs: List<SleepLog>,
    totalMinutesPlayed: Long
) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    
    val weeklyData = remember(sleepLogs, totalMinutesPlayed) {
        val hoursPerDay = mutableMapOf(
            "Mon" to 6.5f,
            "Tue" to 7.2f,
            "Wed" to 8.0f,
            "Thu" to 6.8f,
            "Fri" to 7.5f,
            "Sat" to 8.5f,
            "Sun" to 7.0f
        )
        if (sleepLogs.isNotEmpty()) {
            val cal = Calendar.getInstance()
            sleepLogs.forEach { log ->
                cal.timeInMillis = log.timestamp
                val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "Mon"
                    Calendar.TUESDAY -> "Tue"
                    Calendar.WEDNESDAY -> "Wed"
                    Calendar.THURSDAY -> "Thu"
                    Calendar.FRIDAY -> "Fri"
                    Calendar.SATURDAY -> "Sat"
                    Calendar.SUNDAY -> "Sun"
                    else -> "Wed"
                }
                val current = hoursPerDay[dayOfWeek] ?: 0f
                hoursPerDay[dayOfWeek] = (current + (log.durationMinutes / 60f)).coerceAtMost(12f)
            }
        }
        days.map { day -> day to (hoursPerDay[day] ?: 0f) }
    }

    var selectedDayIndex by remember { mutableIntStateOf(2) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "WEEKLY SLEEP TRENDS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.8.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Room DB Analytics",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Avg 7.3h/night",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val primaryColor = MaterialTheme.colorScheme.primary
            val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            val secondaryColor = MaterialTheme.colorScheme.secondary

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val barWidth = size.width / days.size
                                val index = (offset.x / barWidth).toInt().coerceIn(0, days.size - 1)
                                selectedDayIndex = index
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val barWidth = (w / days.size) * 0.45f
                    val spacing = w / days.size
                    val maxVal = 10f

                    for (i in 1..4) {
                        val y = h - (h * (i * 2f / maxVal))
                        drawLine(
                            color = outlineColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        )
                    }

                    val targetY = h - (h * (7.5f / maxVal))
                    drawLine(
                        color = secondaryColor.copy(alpha = 0.8f),
                        start = Offset(0f, targetY),
                        end = Offset(w, targetY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))
                    )

                    weeklyData.forEachIndexed { i, (_, hours) ->
                        val x = (i * spacing) + (spacing - barWidth) / 2f
                        val barHeight = (hours / maxVal) * h
                        val top = h - barHeight
                        val isSelected = i == selectedDayIndex

                        drawRoundRect(
                            color = if (isSelected) primaryColor else primaryColor.copy(alpha = 0.4f),
                            topLeft = Offset(x, top),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                weeklyData.forEachIndexed { i, (day, _) ->
                    val isSelected = i == selectedDayIndex
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.clickable { selectedDayIndex = i }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val selectedData = weeklyData.getOrNull(selectedDayIndex) ?: ("Wed" to 8.0f)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${selectedData.first}: ${"%.1f".format(selectedData.second)} hours listened",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Text(
                        text = if (selectedData.second >= 7.5f) "Optimal Rest" else "Moderate Rest",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (selectedData.second >= 7.5f) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun BatteryUsageDashboardCard(
    batteryLevel: Int,
    isCharging: Boolean,
    estimatedDrainPerHour: Float,
    activeSoundCount: Int,
    isPlaying: Boolean,
    onRefreshBattery: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                        contentDescription = "Battery Status",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "BATTERY & POWER MONITOR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.5.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = if (isCharging) "Charging ($batteryLevel%)" else "Battery ($batteryLevel%)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }
                }

                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRefreshBattery()
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Battery Status",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val est8hDrain = (estimatedDrainPerHour * 8f).coerceAtMost(100f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "ESTIMATED DRAIN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${"%.1f".format(estimatedDrainPerHour)}% / hr",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "8-HR SLEEP IMPACT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "~${"%.0f".format(est8hDrain)}% total",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val progress = (est8hDrain / 100f).coerceIn(0.05f, 1.0f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = if (est8hDrain > 30f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isPlaying) "Active playback (${activeSoundCount} tracks). Tip: Using OLED Clock mode saves ~40% battery energy." else "Playback idle. Minimal background drain.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            )
        }
    }
}

@Composable
fun SleepHistoryTabContent(
    uiState: MainUiState,
    onClearHistory: () -> Unit,
    onRefreshBattery: () -> Unit
) {
    val totalLogMinutes = remember(uiState.sleepLogs) { uiState.sleepLogs.sumOf { it.durationMinutes } }
    val combinedMinutes = (totalLogMinutes + uiState.totalMinutesPlayed).toInt()
    val hoursPlayed = combinedMinutes / 60
    val minsPlayed = combinedMinutes % 60
    val totalSessions = uiState.sleepLogs.size

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Dashboard Header
        item {
            Text(
                text = "SLEEP SOUND DASHBOARD",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        // Summary Metric Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "TOTAL HOURS PLAYED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 9.sp,
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${hoursPlayed}h ${minsPlayed}m",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "COMPLETED SESSIONS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                                letterSpacing = 1.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$totalSessions",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }
        }

        // Real-time Battery & Energy Impact Dashboard Card
        item {
            BatteryUsageDashboardCard(
                batteryLevel = uiState.batteryLevel,
                isCharging = uiState.isCharging,
                estimatedDrainPerHour = uiState.estimatedDrainPerHour,
                activeSoundCount = uiState.activeSoundCount,
                isPlaying = uiState.isPlaying,
                onRefreshBattery = onRefreshBattery
            )
        }

        // Recharts Style Weekly Sleep Trend Data Visualization (Room Database History)
        item {
            RechartsStyleWeeklyTrendChart(
                sleepLogs = uiState.sleepLogs,
                totalMinutesPlayed = uiState.totalMinutesPlayed
            )
        }

        // Smart Sound Suggestion Based on Calculation
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Smart Suggestion",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "SMART SLEEP INSIGHT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.5.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = uiState.smartRecommendation,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }

        // Firebase Remote Config Sleep Tip & Sync Status
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Firebase Sync",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "FIREBASE CLOUD BACKUP",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.5.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = uiState.firebaseState.cloudSyncStatus,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "SLEEP TIP OF THE DAY (REMOTE CONFIG)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.firebaseState.sleepTipOfTheDay,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }

        // History list header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT SLEEP SESSIONS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                )

                if (uiState.sleepLogs.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        )
                    }
                }
            }
        }

        if (uiState.sleepLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No sleep logs yet. Set a sleep timer to automatically log your sleep sessions!",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            items(uiState.sleepLogs, key = { it.id }) { log ->
                val dateStr = remember(log.timestamp) {
                    SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(log.timestamp))
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = log.presetUsed,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${log.durationMinutes} mins",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomControlSheet(
    uiState: MainUiState,
    onTogglePlayback: () -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onAddTimerMinutes: (Int) -> Unit,
    onResetTimer: () -> Unit,
    onOpenTimerDialog: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            )
            .shadow(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            // Sleep Timer Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenTimerDialog()
                        }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                        .testTag("sleep_timer_display")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "SLEEP TIMER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))

                    val mins = uiState.timerRemainingSeconds / 60
                    val secs = uiState.timerRemainingSeconds % 60
                    val timerStr = if (uiState.isTimerActive) {
                        String.format(Locale.US, "%02d:%02d", mins, secs)
                    } else {
                        "Off"
                    }

                    Text(
                        text = timerStr,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = if (uiState.isTimerActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAddTimerMinutes(15)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("add_15m_button")
                    ) {
                        Text("+15m", style = MaterialTheme.typography.labelSmall)
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAddTimerMinutes(30)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("add_30m_button")
                    ) {
                        Text("+30m", style = MaterialTheme.typography.labelSmall)
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenTimerDialog()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .testTag("open_sleep_timer_modal")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Set Custom Sleep Timer",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (uiState.isTimerActive) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onResetTimer()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .testTag("reset_timer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Reset Timer",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Master Volume + Play / Pause Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "MASTER VOLUME",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        )
                        Text(
                            text = "${(uiState.masterVolume * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Slider(
                        value = uiState.masterVolume,
                        onValueChange = { vol ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMasterVolumeChange(vol)
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.onBackground,
                            activeTrackColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                    )
                }

                // Floating Action Play Button with subtle transition animations
                val fabScale by animateFloatAsState(
                    targetValue = if (uiState.isPlaying) 1.08f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "fabScale"
                )

                FloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTogglePlayback()
                    },
                    containerColor = if (uiState.isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (uiState.isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier
                        .size((58 * fabScale).dp)
                        .testTag("main_play_button")
                ) {
                    AnimatedContent(
                        targetState = uiState.isPlaying,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.8f))
                                .togetherWith(fadeOut(animationSpec = tween(220)) + scaleOut(targetScale = 0.8f))
                        },
                        label = "playIconTransition"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SavePresetDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var presetName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Save Soundscape Preset",
                style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onBackground)
            )
        },
        text = {
            Column {
                Text(
                    text = "Give your custom ambient sound mix a name:",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    placeholder = { Text("e.g. Midnight Reading", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preset_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(presetName) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.background)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
    )
}

@Composable
fun SettingsTabContent(
    uiState: MainUiState,
    onThemeChanged: (ThemeStyle) -> Unit,
    onToggleAutoCap: () -> Unit,
    onTogglePowerSave: () -> Unit = {},
    onToggleNormalization: () -> Unit = {},
    onToggleGentleWake: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Audio & Protection Settings",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }

        // Anti-Spike Volume Protection Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Anti-Spike Volume Protection",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Caps new tracks at 65% when 2+ sound tracks are active to prevent audio volume spikes.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = uiState.isAutoCapEnabled,
                        onCheckedChange = { onToggleAutoCap() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        // Power Save Toggle Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(id = R.string.power_save_mode),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(id = R.string.power_save_desc),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = uiState.isPowerSaveEnabled,
                        onCheckedChange = { onTogglePowerSave() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                            checkedTrackColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }
        }

        // Loudness Normalization Toggle Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Hearing,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(id = R.string.loudness_normalization),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(id = R.string.loudness_normalization_desc),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = uiState.isNormalizationEnabled,
                        onCheckedChange = { onToggleNormalization() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onTertiary,
                            checkedTrackColor = MaterialTheme.colorScheme.tertiary
                        )
                    )
                }
            }
        }

        // Gentle Wake Toggle Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.WbSunny,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(id = R.string.gentle_wake_mode),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(id = R.string.gentle_wake_desc),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = uiState.isGentleWakeEnabled,
                        onCheckedChange = { onToggleGentleWake() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        item {
            Text(
                text = "Theme Preferences",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ThemeOptionRow(
                        title = "OLED Dark (Midnight Blue)",
                        isSelected = uiState.themeStyle == ThemeStyle.DARK,
                        onClick = { onThemeChanged(ThemeStyle.DARK) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 8.dp))
                    ThemeOptionRow(
                        title = "Night Amber (Low-Light Bedroom)",
                        isSelected = uiState.themeStyle == ThemeStyle.NIGHT_AMBER,
                        onClick = { onThemeChanged(ThemeStyle.NIGHT_AMBER) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 8.dp))
                    ThemeOptionRow(
                        title = "Sunset/Sunrise Auto-Schedule",
                        isSelected = uiState.themeStyle == ThemeStyle.SUNSET_AUTO,
                        onClick = { onThemeChanged(ThemeStyle.SUNSET_AUTO) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 8.dp))
                    ThemeOptionRow(
                        title = "Soft Light Theme",
                        isSelected = uiState.themeStyle == ThemeStyle.LIGHT,
                        onClick = { onThemeChanged(ThemeStyle.LIGHT) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 8.dp))
                    ThemeOptionRow(
                        title = "System Default",
                        isSelected = uiState.themeStyle == ThemeStyle.AUTO,
                        onClick = { onThemeChanged(ThemeStyle.AUTO) }
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeOptionRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun QuickStartTabContent(
    uiState: MainUiState,
    onPlayRandom: () -> Unit,
    onTogglePlayback: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Quick Start",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clickable { onPlayRandom() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "Random Mix", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(text = "Discover a new ambient soundscape", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            }
        }
        
        Button(
            onClick = onTogglePlayback,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (uiState.isPlaying) "Stop Playback" else "Start Quick Start")
        }
    }
}

@Composable
fun GentleWakeOverlay(
    progress: Float,
    onDismiss: () -> Unit
) {
    // Elegant sunrise color gradient moving from warm midnight indigo to soft orange/gold as progress increases
    val color1 = Color(0xFF1E1B4B).valuableBlend(Color(0xFFFEF3C7), progress)
    val color2 = Color(0xFF311042).valuableBlend(Color(0xFFFDE047), progress)
    val color3 = Color(0xFF0F172A).valuableBlend(Color(0xFFF97316), progress)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(color1, color2, color3)))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = (0.15f + 0.1f * kotlin.math.sin(progress * Math.PI.toFloat())).coerceIn(0f, 1f)),
                modifier = Modifier.size(120.dp + (40.dp * progress))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = "Sunrise",
                        tint = Color(0xFFFDE047),
                        modifier = Modifier.size(60.dp + (20.dp * progress))
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(id = R.string.rise_and_shine),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.5.sp
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(id = R.string.rise_and_shine_desc),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFFFDE047),
                trackColor = Color.White.copy(alpha = 0.2f),
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF7C2D12)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .height(54.dp)
                    .fillMaxWidth(0.6f)
                    .testTag("dismiss_gentle_wake")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.good_morning),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

private fun Color.valuableBlend(to: Color, amount: Float): Color {
    val r = this.red + (to.red - this.red) * amount
    val g = this.green + (to.green - this.green) * amount
    val b = this.blue + (to.blue - this.blue) * amount
    return Color(r, g, b, 1f)
}

