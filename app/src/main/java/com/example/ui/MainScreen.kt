package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.R
import com.example.model.RecordingItem
import com.example.ui.theme.LocalAppColors
import com.example.viewmodel.MainViewModel
import java.util.Locale

/**
 * Pure UI state of the main screen. Decoupled from [MainViewModel] so the
 * interface can be rendered/tested in any combination of states.
 */
data class MainUiState(
    val hasMicPermission: Boolean = true,
    val recordings: List<RecordingItem> = emptyList(),
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val recordingDurationSeconds: Int = 0,
    val amplitudes: List<Float> = emptyList(),
    val searchQuery: String = "",
    val selectedFolder: String? = null
)

/**
 * Stateful entry point: collects ViewModel state and mic permission flow,
 * then delegates all rendering to [MainScreenContent].
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val recordings by viewModel.recordings.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val recordingDurationSeconds by viewModel.recordingDurationSeconds.collectAsState()
    val amplitudes by viewModel.amplitudes.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val folders by viewModel.folders.collectAsState()

    // Permission state with rationale / permanent-denial handling (Phase 1.2)
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isPermanentlyDenied by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }
    var hasRequestedBefore by remember { mutableStateOf(false) }

    fun refreshPermissionState() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasMicPermission = granted
        if (!granted) {
            val activity = context as? ComponentActivity
            val shouldShow = activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true
            showRationale = shouldShow
            // After at least one request, shouldShow==false means permanently denied (Don't ask again)
            isPermanentlyDenied = hasRequestedBefore && !shouldShow
        } else {
            isPermanentlyDenied = false
            showRationale = false
        }
    }

    // Re-check onResume — covers user returning from Settings (1.2)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasRequestedBefore = true
            hasMicPermission = granted
            if (!granted) {
                val activity = context as? ComponentActivity
                val shouldShow = activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true
                showRationale = shouldShow
                isPermanentlyDenied = !shouldShow
            } else {
                isPermanentlyDenied = false
                showRationale = false
            }
        }
    )

    MainScreenContent(
        uiState = MainUiState(
            hasMicPermission = hasMicPermission,
            recordings = recordings,
            isRecording = isRecording,
            isPaused = isPaused,
            recordingDurationSeconds = recordingDurationSeconds,
            amplitudes = amplitudes,
            searchQuery = searchQuery,
            selectedFolder = selectedFolder
        ),
        modifier = modifier,
        operationError = operationError,
        onConsumeError = { viewModel.consumeOperationError() },
        isPermanentlyDenied = isPermanentlyDenied,
        showRationale = showRationale,
        folders = folders,
        onOpenSettings = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        },
        onRequestPermission = {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        },
        onRecordToggle = {
            if (isRecording) {
                viewModel.stopRecording()
            } else {
                viewModel.startRecording()
            }
        },
        onPauseToggle = {
            if (isPaused) viewModel.resumeRecording() else viewModel.pauseRecording()
        },
        onSearchQueryChange = { q -> viewModel.setSearchQuery(q) },
        onSelectFolder = { f -> viewModel.setSelectedFolder(f) },
        onShareMultiple = { items -> viewModel.shareMultipleRecordings(items, context) },
        onPlayToggle = { item -> viewModel.togglePlayPause(item) },
        onSeek = { progress -> viewModel.seekTo(progress) },
        onDeleteConfirm = { item -> viewModel.deleteRecording(item) },
        onRenameConfirm = { item, newName -> viewModel.renameRecording(item, newName) },
        onTrimConfirm = { item, start, end -> viewModel.trimRecording(item, start, end) },
        onShare = { item -> viewModel.shareRecording(item, context) }
    )
}

/**
 * Stateless renderer of the main screen (RTL layout representing Arabic
 * engineering guidelines). Safe to preview and screenshot-test in isolation.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreenContent(
    uiState: MainUiState,
    modifier: Modifier = Modifier,
    operationError: String? = null,
    onConsumeError: () -> Unit = {},
    isPermanentlyDenied: Boolean = false,
    showRationale: Boolean = false,
    folders: List<String> = emptyList(),
    onOpenSettings: () -> Unit = {},
    onRequestPermission: () -> Unit = {},
    onRecordToggle: () -> Unit = {},
    onPauseToggle: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSelectFolder: (String?) -> Unit = {},
    onShareMultiple: (List<RecordingItem>) -> Unit = {},
    onPlayToggle: (RecordingItem) -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onDeleteConfirm: (RecordingItem) -> Unit = {},
    onRenameConfirm: (RecordingItem, String) -> Unit = { _, _ -> },
    onTrimConfirm: (RecordingItem, Float, Float) -> Unit = { _, _, _ -> },
    onShare: (RecordingItem) -> Unit = {}
) {
    val colors = LocalAppColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog holding states
    var recordingToDelete by remember { mutableStateOf<RecordingItem?>(null) }
    var recordingToRename by remember { mutableStateOf<RecordingItem?>(null) }
    var renameInputText by remember { mutableStateOf("") }
    var recordingToTrim by remember { mutableStateOf<RecordingItem?>(null) }
    var trimStart by remember { mutableStateOf(0f) }
    var trimEnd by remember { mutableStateOf(1f) }

    LaunchedEffect(operationError) {
        if (operationError != null) {
            snackbarHostState.showSnackbar(operationError)
            onConsumeError()
        }
    }

    // Conditional RTL: only force RTL for Arabic locales (Phase 1.2), else respect system LTR for en/
    val isRtl = remember { Locale.getDefault().language in setOf("ar", "fa", "he", "ur") }
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background),
            containerColor = colors.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                HeaderSection()
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (!uiState.hasMicPermission) {
                    PermissionRequestScreen(
                        onRequestPermission = onRequestPermission,
                        onOpenSettings = onOpenSettings,
                        isPermanentlyDenied = isPermanentlyDenied,
                        showRationale = showRationale
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
                                .background(colors.cardSurface)
                                .border(1.dp, colors.cardBorder, RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isRecording) {
                                WaveformVisualizer(amplitudes = uiState.amplitudes)
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = stringResource(id = R.string.recording_state_idle),
                                        color = colors.textSecondary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Live Timer or Status indicator while recording — includes pause state (Phase 2)
                        if (uiState.isRecording) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = formatSeconds(uiState.recordingDurationSeconds),
                                    color = if (uiState.isPaused) colors.playbackGold else Color.White,
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
                                    val pulseTransition = rememberInfiniteTransition(label = "pulseTransition")
                                    val pulseScale by pulseTransition.animateFloat(
                                        initialValue = if (uiState.isPaused) 1f else 0.9f,
                                        targetValue = if (uiState.isPaused) 1f else 1.1f,
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
                                            .background(if (uiState.isPaused) colors.playbackGold else colors.recordingRed)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(
                                            id = if (uiState.isPaused) R.string.recording_state_paused else R.string.recording_state_recording
                                        ),
                                        color = if (uiState.isPaused) colors.playbackGold.copy(alpha = 0.9f) else colors.accentLightBlue.copy(alpha = 0.9f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }

                        if (uiState.isRecording) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Center action triggers - Glow dynamic button + Pause/Resume (Phase 2)
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isRecording) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    // Pause / Resume pill
                                    FilledTonalIconButton(
                                        onClick = onPauseToggle,
                                        modifier = Modifier.size(64.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = colors.cardSurface,
                                            contentColor = if (uiState.isPaused) colors.playbackGold else colors.textPrimary
                                        ),
                                        shape = CircleShape
                                    ) {
                                        Icon(
                                            imageVector = if (uiState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                            contentDescription = if (uiState.isPaused) "استئناف" else "إيقاف مؤقت",
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(20.dp))
                                    RecordTriggerButton(
                                        isRecording = uiState.isRecording,
                                        onClick = onRecordToggle
                                    )
                                }
                            } else {
                                RecordTriggerButton(
                                    isRecording = false,
                                    onClick = onRecordToggle
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Search + Folder filter (Phase 2)
                        if (!uiState.isRecording) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { Text(text = "بحث في التسجيلات...", color = colors.textHint, fontSize = 14.sp) },
                                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onSearchQueryChange("") }) {
                                            Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.cardBorder,
                                    unfocusedBorderColor = colors.cardBorder,
                                    focusedContainerColor = colors.cardSurface,
                                    unfocusedContainerColor = colors.cardSurface
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_field")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            if (folders.isNotEmpty()) {
                                // Folder chips — synthetic based on parent folder name
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = uiState.selectedFolder == null,
                                        onClick = { onSelectFolder(null) },
                                        label = { Text(text = "الكل", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = colors.accentActivePill,
                                            selectedLabelColor = colors.accentOnActiveText
                                        ),
                                        modifier = Modifier.testTag("chip_all")
                                    )
                                    folders.forEach { folder ->
                                        FilterChip(
                                            selected = uiState.selectedFolder == folder,
                                            onClick = { onSelectFolder(if (uiState.selectedFolder == folder) null else folder) },
                                            label = { Text(text = folder, fontSize = 12.sp, maxLines = 1) },
                                            modifier = Modifier.testTag("chip_$folder")
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }

                        // Section Title: "التسجيلات المحفوظة" + Share all (Phase 2 export)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.recordings_section_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            if (uiState.recordings.isNotEmpty()) {
                                TextButton(
                                    onClick = { onShareMultiple(uiState.recordings) },
                                    modifier = Modifier.testTag("share_all_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "مشاركة الكل", color = colors.textSecondary, fontSize = 12.sp)
                                }
                            }
                        }

                        // Recordings List View
                        if (uiState.recordings.isEmpty()) {
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
                                        tint = colors.textHint,
                                        modifier = Modifier.size(54.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = stringResource(id = R.string.no_recordings),
                                        color = colors.textSecondary,
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
                                    items = uiState.recordings,
                                    key = { it.file.absolutePath }
                                ) { item ->
                                    RecordingItemCard(
                                        item = item,
                                        onPlayToggle = { onPlayToggle(item) },
                                        onSeek = onSeek,
                                        onDeleteRequest = { recordingToDelete = item },
                                        onRenameRequest = {
                                            recordingToRename = item
                                            renameInputText = item.name
                                        },
                                        onTrimRequest = {
                                            recordingToTrim = item
                                            trimStart = 0f
                                            trimEnd = 1f
                                        },
                                        onShare = { onShare(item) }
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
                    colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.rename_title),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = renameInputText,
                            onValueChange = { renameInputText = it },
                            placeholder = { Text(text = stringResource(id = R.string.rename_placeholder), color = colors.textHint) },
                            textStyle = LocalTextStyle.current.copy(color = colors.textPrimary, fontSize = 15.sp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.recordingRed,
                                unfocusedBorderColor = colors.cardBorder,
                                cursorColor = colors.recordingRed
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
                                Text(text = stringResource(id = R.string.cancel_button), color = colors.textSecondary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    onRenameConfirm(recording, renameInputText)
                                    recordingToRename = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.recordingRed),
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
                    colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.delete_title),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(id = R.string.delete_confirm),
                            color = colors.textSecondary,
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
                                Text(text = stringResource(id = R.string.cancel_button), color = colors.textSecondary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    onDeleteConfirm(recording)
                                    recordingToDelete = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.recordingRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(text = stringResource(id = R.string.delete_button), color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Trim dialog — scrub preview with dual sliders (Phase 2)
        recordingToTrim?.let { recording ->
            Dialog(onDismissRequest = { recordingToTrim = null }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "قص التسجيل",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = recording.name,
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        MiniWaveformPreview(
                            item = recording,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.cardBorder.copy(alpha = 0.3f))
                                .padding(8.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val durationMs = recording.durationMs
                        val selectedMs = ((trimEnd - trimStart) * durationMs).toLong().coerceAtLeast(0L)
                        Text(
                            text = "${formatMillis((trimStart * durationMs).toLong())} → ${formatMillis((trimEnd * durationMs).toLong())}  (${formatMillis(selectedMs)})",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "البداية", color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
                        Slider(
                            value = trimStart,
                            onValueChange = { v -> trimStart = v.coerceIn(0f, (trimEnd - 0.05f).coerceAtLeast(0f)) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(activeTrackColor = colors.recordingRed, thumbColor = colors.recordingRed)
                        )
                        Text(text = "النهاية", color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
                        Slider(
                            value = trimEnd,
                            onValueChange = { v -> trimEnd = v.coerceIn((trimStart + 0.05f).coerceAtMost(1f), 1f) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(activeTrackColor = colors.playbackGold, thumbColor = colors.playbackGold)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { recordingToTrim = null }) {
                                Text(text = stringResource(id = R.string.cancel_button), color = colors.textSecondary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    onTrimConfirm(recording, trimStart, trimEnd)
                                    recordingToTrim = null
                                },
                                enabled = (trimEnd - trimStart) >= 0.05f && trimEnd > trimStart,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.recordingRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(text = "قص وحفظ", color = Color.White)
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
    val colors = LocalAppColors.current
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
                color = colors.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = stringResource(id = R.string.header_subtitle),
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        // Aesthetic Brand Badge or Micro-indicator
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(colors.cardSurface)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(30.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.playbackGold)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(id = R.string.smart_badge_label),
                    color = colors.playbackGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Permission layout — handles rationale and permanent-denial → Settings (Phase 1.2)
@Composable
fun PermissionRequestScreen(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit = {},
    isPermanentlyDenied: Boolean = false,
    showRationale: Boolean = false,
) {
    val colors = LocalAppColors.current
    val titleRes = if (isPermanentlyDenied) R.string.permission_permanently_denied_title else R.string.permission_title
    val descRes = when {
        isPermanentlyDenied -> R.string.permission_permanently_denied_desc
        showRationale -> R.string.permission_rationale_desc
        else -> R.string.permission_desc
    }
    val buttonTextRes = if (isPermanentlyDenied) R.string.permission_open_settings else R.string.permission_button
    val buttonAction = if (isPermanentlyDenied) onOpenSettings else onRequestPermission
    val buttonIcon: ImageVector = if (isPermanentlyDenied) Icons.Default.Settings else Icons.Default.Mic

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
                .background(colors.recordingRedGlow),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = colors.recordingRed,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(id = titleRes),
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(id = descRes),
            color = colors.textSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(30.dp))
        Button(
            onClick = buttonAction,
            colors = ButtonDefaults.buttonColors(containerColor = colors.recordingRed),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Icon(
                imageVector = buttonIcon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(id = buttonTextRes),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (isPermanentlyDenied) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onRequestPermission) {
                Text(text = stringResource(id = R.string.permission_button), color = colors.textSecondary, fontSize = 13.sp)
            }
        }
    }
}

// Glowing recording red button
@Composable
fun RecordTriggerButton(
    isRecording: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
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
        // Outer glowing pulse ring — single InfiniteTransition merged (0.3)
        if (isRecording) {
            val glowTransition = rememberInfiniteTransition(label = "glowTransition")
            val pulseGlowVal by glowTransition.animateFloat(
                initialValue = 135.dp.value,
                targetValue = 175.dp.value,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "sizePulse"
            )
            val alphaVal by glowTransition.animateFloat(
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
                    .background(colors.recordingRed.copy(alpha = alphaVal * 0.35f))
            )
        }

        // Main colored circular shell
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(colors.recordingRed)
                .shadow(
                    elevation = if (isRecording) 16.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = colors.recordingRed,
                    spotColor = colors.recordingRed
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
                        .background(colors.recordingRedDeep)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = stringResource(id = R.string.record_btn_desc),
                    tint = colors.recordingRedDeep,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
    }
}

// Waveform visualizer Canvas element scanning max Amplitudes
@Composable
fun WaveformVisualizer(amplitudes: List<Float>) {
    val colors = LocalAppColors.current
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
            List(barCount - amplitudes.size) { 0f } + amplitudes
        }

        for (i in 0 until barCount) {
            val amp = sampleList.getOrElse(i) { 0f }
            // Silence (0f) → minimal hint bar, not fake 0.04f floor (Phase 1.3)
            val barHeightVal = if (amp == 0f) 2.dp.toPx() else (amp * height).coerceIn(4.dp.toPx(), height)
            val x = i * (barWidth + barSpacing)
            val centerY = height / 2f
            val top = centerY - (barHeightVal / 2f)
            val bottom = centerY + (barHeightVal / 2f)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.recordingRed,
                        colors.playbackGold
                    )
                ),
                topLeft = androidx.compose.ui.geometry.Offset(x, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeightVal),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

// Mini scrub waveform derived from file hash (Phase 2 trim preview)
@Composable
fun MiniWaveformPreview(
    item: RecordingItem,
    modifier: Modifier = Modifier,
    progress: Float = 0f
) {
    val colors = LocalAppColors.current
    // Deterministic pseudo waveform from file name hash — 20 bars
    val waveform = remember(item.file.absolutePath) {
        val seed = item.file.name.hashCode()
        List(20) { i ->
            val v = kotlin.math.abs(kotlin.math.sin((seed + i * 373) * 0.017 + i * 0.9))
            v.toFloat().coerceIn(0.25f, 0.95f)
        }
    }
    Canvas(modifier = modifier) {
        val barCount = waveform.size
        val barSpacing = 3.dp.toPx()
        val barWidth = (size.width - (barCount - 1) * barSpacing) / barCount
        val centerY = size.height / 2f
        for (i in waveform.indices) {
            val amp = waveform[i]
            val h = (amp * size.height).coerceIn(2.dp.toPx(), size.height)
            val x = i * (barWidth + barSpacing)
            val isPlayed = if (item.isPlaying) (i.toFloat() / barCount) <= progress else false
            drawRoundRect(
                color = if (isPlayed) colors.recordingRed else colors.cardBorder,
                topLeft = androidx.compose.ui.geometry.Offset(x, centerY - h / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

// Detailed row cards displaying saved recordings — with mini waveform & trim (Phase 2)
@Composable
fun RecordingItemCard(
    item: RecordingItem,
    onPlayToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onDeleteRequest: () -> Unit,
    onRenameRequest: () -> Unit,
    onTrimRequest: () -> Unit = {},
    onShare: () -> Unit
) {
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        border = BorderStroke(1.dp, colors.cardBorder)
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
                        .background(if (item.isPlaying) colors.recordingRedGlow else colors.cardBorder)
                        .clickable(onClick = onPlayToggle)
                        .testTag("play_pause_button_${item.name}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (item.isPlaying) stringResource(id = R.string.pause_btn_desc) else stringResource(id = R.string.play_btn_desc),
                        tint = if (item.isPlaying) colors.recordingRed else colors.playbackOnSurface,
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
                        color = if (item.isPlaying) colors.recordingRed else colors.textPrimary,
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
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(colors.textHint)
                        )
                        Text(
                            text = formatFileSize(item.fileSizeBytes),
                            color = colors.textSecondary,
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
                    color = if (item.isPlaying) colors.recordingRed else colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Mini waveform scrub preview (Phase 2) — always visible, highlights progress when playing
            MiniWaveformPreview(
                item = item,
                progress = item.playProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(top = 8.dp)
            )

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
                            thumbColor = colors.recordingRed,
                            activeTrackColor = colors.recordingRed,
                            inactiveTrackColor = colors.cardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer Quick actions line representing sharing and renaming features
            HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.5f), thickness = 1.dp)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Actions (Share, Edit name, Trim)
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
                    ActionTextButton(
                        icon = Icons.Default.ContentCut,
                        label = "قص",
                        onClick = onTrimRequest,
                        tag = "trim_button_${item.name}"
                    )
                }

                ActionTextButton(
                    icon = Icons.Default.Delete,
                    label = stringResource(id = R.string.delete_btn_desc),
                    onClick = onDeleteRequest,
                    color = colors.recordingRed.copy(alpha = 0.85f),
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
    color: Color = LocalAppColors.current.textSecondary,
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
    val isArabic = Locale.getDefault().language == "ar"
    if (bytes <= 0) return if (isArabic) "0 ب" else "0 B"
    val unitsAr = arrayOf("ب", "ك.ب", "م.ب", "ج.ب")
    val unitsEn = arrayOf("B", "KB", "MB", "GB")
    val units = if (isArabic) unitsAr else unitsEn
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.getDefault(), "%.1f %s", value, units[digitGroups])
}
