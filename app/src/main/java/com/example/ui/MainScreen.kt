package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.RecordingItem
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import java.util.Locale

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recordings by viewModel.recordings.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDurationSeconds by viewModel.recordingDurationSeconds.collectAsState()
    val amplitudes by viewModel.amplitudes.collectAsState()

    // Playback state
    val currentlyPlayingFile by viewModel.currentlyPlayingFile.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()
    val playbackPositionMs by viewModel.playbackPositionMs.collectAsState()
    val playbackDurationMs by viewModel.playbackDurationMs.collectAsState()

    // Dialog holding states
    var recordingToDelete by remember { mutableStateOf<RecordingItem?>(null) }
    var recordingToRename by remember { mutableStateOf<RecordingItem?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    // Dynamic Permission requesting status
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasMicPermission = granted
        }
    )

    // Force RTL for the layout representing Arabic engineering guidelines
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .background(DeepDarkBackground),
            containerColor = DeepDarkBackground,
            topBar = {
                HeaderSection()
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (!hasMicPermission) {
                    PermissionRequestScreen(
                        onRequestPermission = {
                            launcher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                    ) {
                        // Amplitude visualizer or beautiful idle greeting card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(CardSurfaceColor)
                                .border(1.dp, CardBorderColor, RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isRecording) {
                                WaveformVisualizer(amplitudes = amplitudes)
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = stringResource(id = R.string.recording_state_idle),
                                        color = TextSecondary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Live Timer or Status indicator while recording
                        if (isRecording) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = formatSeconds(recordingDurationSeconds),
                                    color = Color.White,
                                    fontSize = 56.sp,
                                    fontWeight = FontWeight.Light,
                                    style = LocalTextStyle.current.copy(
                                        fontFeatureSettings = "tnum",
                                        letterSpacing = 2.sp
                                    ),
                                    modifier = Modifier.testTag("timer_label")
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    val pulseScale by animateFloatAsState(
                                        targetValue = if (recordingDurationSeconds % 2 == 0) 1.1f else 0.9f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(500, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "pulse"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .scale(pulseScale)
                                            .clip(CircleShape)
                                            .background(RecordingRed)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(id = R.string.recording_state_recording),
                                        color = AccentLightBlue.copy(alpha = 0.9f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }

                        if (isRecording) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Center action triggers - Glow dynamic button
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            RecordTriggerButton(
                                isRecording = isRecording,
                                onClick = {
                                    if (isRecording) {
                                        viewModel.stopRecording()
                                    } else {
                                        viewModel.startRecording()
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Section Title: "التسجيلات المحفوظة"
                        Text(
                            text = "تسجيلاتك الصوتية",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                        )

                        // Recordings List View
                        if (recordings.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = TextHint,
                                        modifier = Modifier.size(54.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = stringResource(id = R.string.no_recordings),
                                        color = TextSecondary,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .testTag("recordings_list"),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 20.dp)
                            ) {
                                items(
                                    items = recordings,
                                    key = { it.file.absolutePath }
                                ) { item ->
                                    RecordingItemCard(
                                        item = item,
                                        onPlayToggle = { viewModel.togglePlayPause(item) },
                                        onSeek = { progress -> viewModel.seekTo(progress) },
                                        onDeleteRequest = { recordingToDelete = item },
                                        onRenameRequest = {
                                            recordingToRename = item
                                            renameInputText = item.name
                                        },
                                        onShare = { viewModel.shareRecording(item, context) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Renaming Dialog
        recordingToRename?.let { recording ->
            Dialog(onDismissRequest = { recordingToRename = null }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                    border = BorderStroke(1.dp, CardBorderColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.rename_title),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = renameInputText,
                            onValueChange = { renameInputText = it },
                            placeholder = { Text(text = stringResource(id = R.string.rename_placeholder), color = TextHint) },
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 15.sp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RecordingRed,
                                unfocusedBorderColor = CardBorderColor,
                                cursorColor = RecordingRed
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rename_input")
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { recordingToRename = null }
                            ) {
                                Text(text = stringResource(id = R.string.cancel_button), color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    viewModel.renameRecording(recording, renameInputText)
                                    recordingToRename = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RecordingRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(text = stringResource(id = R.string.rename_button), color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Deletion confirmation dialog
        recordingToDelete?.let { recording ->
            Dialog(onDismissRequest = { recordingToDelete = null }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                    border = BorderStroke(1.dp, CardBorderColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.delete_title),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(id = R.string.delete_confirm),
                            color = TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Right
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { recordingToDelete = null }
                            ) {
                                Text(text = stringResource(id = R.string.cancel_button), color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    viewModel.deleteRecording(recording)
                                    recordingToDelete = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RecordingRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(text = stringResource(id = R.string.delete_button), color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// App Header Section
@Composable
fun HeaderSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(id = R.string.app_name),
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "مسجل الصوت الاحترافي",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        // Aesthetic Brand Badge or Micro-indicator
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(CardSurfaceColor)
                .border(1.dp, CardBorderColor, RoundedCornerShape(30.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(PlaybackGold)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "سلسلة ذكية",
                    color = PlaybackGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Permission layout
@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(RecordingRedGlow),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = RecordingRed,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(id = R.string.permission_title),
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(id = R.string.permission_desc),
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(30.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = RecordingRed),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = stringResource(id = R.string.permission_button),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Glowing recording red button
@Composable
fun RecordTriggerButton(
    isRecording: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else if (isRecording) 1.05f else 1.0f,
        label = "clickScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(140.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .scale(scale)
    ) {
        // Outer glowing pulse ring
        if (isRecording) {
            val pulseGlowVal by rememberInfiniteTransition(label = "").animateFloat(
                initialValue = 135.dp.value,
                targetValue = 175.dp.value,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "sizePulse"
            )
            val alphaVal by rememberInfiniteTransition(label = "").animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "alphaPulse"
            )

            Box(
                Modifier
                    .size(pulseGlowVal.dp)
                    .clip(CircleShape)
                    .background(RecordingRed.copy(alpha = alphaVal * 0.35f))
            )
        }

        // Main colored circular shell
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(RecordingRed)
                .shadow(
                    elevation = if (isRecording) 16.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = RecordingRed,
                    spotColor = RecordingRed
                )
                .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isRecording) {
                // Centered rounded square block representing stop (styled in deep maroon #690005)
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(RecordingRedDeep)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = stringResource(id = R.string.record_btn_desc),
                    tint = RecordingRedDeep,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
    }
}

// Waveform visualizer Canvas element scanning max Amplitudes
@Composable
fun WaveformVisualizer(amplitudes: List<Float>) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        val width = size.width
        val height = size.height
        val barCount = 45
        val barSpacing = 4.dp.toPx()
        val totalBarsWidth = (barCount - 1) * barSpacing
        val barWidth = (width - totalBarsWidth) / barCount

        val sampleList = if (amplitudes.size >= barCount) {
            amplitudes.takeLast(barCount)
        } else {
            List(barCount - amplitudes.size) { 0.04f } + amplitudes
        }

        for (i in 0 until barCount) {
            val amp = sampleList.getOrElse(i) { 0.04f }
            // Amplitude height scales elegantly relative to bounds
            val barHeightVal = (amp * height).coerceIn(4.dp.toPx(), height)
            val x = i * (barWidth + barSpacing)
            val centerY = height / 2f
            val top = centerY - (barHeightVal / 2f)
            val bottom = centerY + (barHeightVal / 2f)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        RecordingRed,
                        PlaybackGold
                    )
                ),
                topLeft = androidx.compose.ui.geometry.Offset(x, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeightVal),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

// Detailed row cards displaying saved recordings
@Composable
fun RecordingItemCard(
    item: RecordingItem,
    onPlayToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onDeleteRequest: () -> Unit,
    onRenameRequest: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
        border = BorderStroke(1.dp, CardBorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Play action button
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (item.isPlaying) RecordingRedGlow else CardBorderColor)
                        .clickable(onClick = onPlayToggle)
                        .testTag("play_pause_button_${item.name}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (item.isPlaying) stringResource(id = R.string.pause_btn_desc) else stringResource(id = R.string.play_btn_desc),
                        tint = if (item.isPlaying) RecordingRed else PlaybackWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Detail and Name Section
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.name,
                        color = if (item.isPlaying) RecordingRed else TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = item.dateString,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(TextHint)
                        )
                        Text(
                            text = formatFileSize(item.fileSizeBytes),
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Metadata duration display
                Text(
                    text = if (item.isPlaying) {
                        "${formatMillis(item.playPositionMs.toLong())} / ${formatMillis(item.durationMs)}"
                    } else {
                        formatMillis(item.durationMs)
                    },
                    color = if (item.isPlaying) RecordingRed else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Seek progress bar (Animated visibility)
            AnimatedVisibility(
                visible = item.isPlaying,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Slider(
                        value = item.playProgress,
                        onValueChange = onSeek,
                        colors = SliderDefaults.colors(
                            thumbColor = RecordingRed,
                            activeTrackColor = RecordingRed,
                            inactiveTrackColor = CardBorderColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer Quick actions line representing sharing and renaming features
            Divider(color = CardBorderColor.copy(alpha = 0.5f), thickness = 1.dp)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Actions (Share, Edit name)
                    ActionTextButton(
                        icon = Icons.Default.Share,
                        label = stringResource(id = R.string.share_btn_desc),
                        onClick = onShare
                    )
                    ActionTextButton(
                        icon = Icons.Default.Edit,
                        label = stringResource(id = R.string.rename_btn_desc),
                        onClick = onRenameRequest,
                        tag = "rename_button_${item.name}"
                    )
                }

                ActionTextButton(
                    icon = Icons.Default.Delete,
                    label = stringResource(id = R.string.delete_btn_desc),
                    onClick = onDeleteRequest,
                    color = RecordingRed.copy(alpha = 0.85f),
                    tag = "delete_button_${item.name}"
                )
            }
        }
    }
}

@Composable
fun ActionTextButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color = TextSecondary,
    tag: String? = null
) {
    val modifier = if (tag != null) Modifier.testTag(tag) else Modifier
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// Utility formatting duration helpers
fun formatSeconds(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}

fun formatMillis(millis: Long): String {
    val totalSecs = millis / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 ب"
    val units = arrayOf("ب", "ك.ب", "م.ب", "ج.ب")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale("ar"), "%.1f %s", value, units[digitGroups])
}
