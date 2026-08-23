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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.example.data.TranscriptEntity
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
    val aiOptIn by viewModel.aiOptIn.collectAsState()
    val aiProcessing by viewModel.aiProcessing.collectAsState()
    val transcripts by viewModel.transcripts.collectAsState()
    val selectedTranscript by viewModel.selectedTranscript.collectAsState()

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
        onShare = { item -> viewModel.shareRecording(item, context) },
        aiOptIn = aiOptIn,
        aiProcessing = aiProcessing,
        transcripts = transcripts,
        selectedTranscript = selectedTranscript,
        onToggleAiOptIn = { viewModel.setAiOptIn(it) },
        onRequestTranscription = { item -> viewModel.requestTranscription(item) },
        onDismissTranscript = { viewModel.clearSelectedTranscript() },
        onCopyTranscript = { text ->
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("transcript", text))
        },
        onShareTranscript = { text ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة النسخ").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    )
}

/**
 * Stateless renderer of the main screen (RTL layout representing Arabic
 * engineering guidelines). Safe to preview and screenshot-test in isolation.
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
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
    onShare: (RecordingItem) -> Unit = {},
    aiOptIn: Boolean = false,
    aiProcessing: Set<String> = emptySet(),
    transcripts: Map<String, TranscriptEntity> = emptyMap(),
    selectedTranscript: TranscriptEntity? = null,
    onToggleAiOptIn: (Boolean) -> Unit = {},
    onRequestTranscription: (RecordingItem) -> Unit = {},
    onDismissTranscript: () -> Unit = {},
    onCopyTranscript: (String) -> Unit = {},
    onShareTranscript: (String) -> Unit = {}
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
    var showAiConsent by remember { mutableStateOf(false) }

    LaunchedEffect(operationError) {
        if (operationError != null) {
            snackbarHostState.showSnackbar(operationError)
            onConsumeError()
        }
    }

    // Conditional RTL: only force RTL for Arabic locales (Phase 1.2), else respect system LTR for en/ — no stale remember (Phase 4 a11y)
    val isRtl = Locale.getDefault().language in setOf("ar", "fa", "he", "ur")
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background),
            containerColor = colors.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                HeaderSection(
                    aiOptIn = aiOptIn,
                    aiProcessingCount = aiProcessing.size,
                    onAiClick = {
                        if (!aiOptIn) showAiConsent = true else onToggleAiOptIn(false)
                    }
                )
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
                        // AI opt-in banner (Phase 3) — consent, status, processing
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (aiOptIn) colors.accentActivePill.copy(alpha = 0.15f) else colors.cardSurface),
                            border = BorderStroke(1.dp, if (aiOptIn) colors.accentActivePill else colors.cardBorder),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (aiOptIn) "الذكاء الاصطناعي مفعّل ✓" else "فعّل الذكاء الاصطناعي",
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (aiOptIn) {
                                            if (aiProcessing.isNotEmpty()) "جاري معالجة ${aiProcessing.size} تسجيلات..."
                                            else "سيتم نسخ التسجيلات وتلخيصها تلقائياً"
                                        } else "نسخ عربي، تلخيص، واستخراج مهام — يتطلب موافقتك",
                                        color = colors.textSecondary,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                if (aiOptIn) {
                                    if (aiProcessing.isNotEmpty()) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = colors.accentActivePill)
                                    } else {
                                        Switch(
                                            checked = true,
                                            onCheckedChange = { onToggleAiOptIn(false) },
                                            colors = SwitchDefaults.colors(checkedThumbColor = colors.accentActivePill, checkedTrackColor = colors.accentActivePill.copy(alpha = 0.5f))
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = { showAiConsent = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentActivePill, contentColor = colors.accentOnActiveText),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("ai_enable_button")
                                    ) {
                                        Text(text = "تفعيل", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

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
                                    modifier = Modifier
                                        .semantics { contentDescription = "مؤقت التسجيل ${formatSeconds(uiState.recordingDurationSeconds)}" }
                                        .testTag("timer_label")
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
                                modifier = Modifier
                                    .semantics { heading() }
                                    .padding(start = 4.dp)
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
                                    val transcriptExists = transcripts.containsKey(item.file.absolutePath)
                                    val isTranscribing = aiProcessing.contains(item.file.absolutePath)
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
                                        onAiRequest = { onRequestTranscription(item) },
                                        hasTranscript = transcriptExists,
                                        isTranscribing = isTranscribing,
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

        // AI consent dialog (Phase 3)
        if (showAiConsent) {
            AlertDialog(
                onDismissRequest = { showAiConsent = false },
                title = { Text(text = "تفعيل الذكاء الاصطناعي؟", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                text = {
                    Text(
                        text = "سيتم إرسال التسجيل إلى Gemini لنسخه وتلخيصه واستخراج المهام. التسجيل يبقى على جهازك ولا يُخزن بدون موافقتك. يمكنك إيقاف الميزة في أي وقت عبر الشريط العلوي.",
                        color = colors.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Right
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onToggleAiOptIn(true); showAiConsent = false },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentActivePill)
                    ) { Text(text = "موافق، فعّل", color = colors.accentOnActiveText) }
                },
                dismissButton = {
                    TextButton(onClick = { showAiConsent = false }) { Text(text = "إلغاء", color = colors.textSecondary) }
                },
                containerColor = colors.cardSurface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Transcript bottom sheet (Phase 3) — inline sheet with copy/share
        selectedTranscript?.let { tr ->
            ModalBottomSheet(
                onDismissRequest = onDismissTranscript,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = colors.cardSurface,
                contentColor = colors.textPrimary,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = tr.titleSuggestion.ifBlank { "النسخ والتلخيص" }, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismissTranscript) { Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = colors.textSecondary) }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // Transcript
                    Text(text = "النص المنسوخ", color = colors.playbackGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = colors.background), border = BorderStroke(1.dp, colors.cardBorder), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(text = tr.transcript.ifBlank { "لا يوجد نص" }, color = colors.textPrimary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(12.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "الملخص", color = colors.playbackGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = colors.background), border = BorderStroke(1.dp, colors.cardBorder), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(text = tr.summary.ifBlank { "—" }, color = colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(12.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "بنود العمل", color = colors.playbackGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = colors.background), border = BorderStroke(1.dp, colors.cardBorder), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(text = tr.actionItems.ifBlank { "لا توجد بنود عمل واضحة." }, color = colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(12.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { onCopyTranscript(tr.transcript + "\n\n" + tr.summary) },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, colors.cardBorder)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "نسخ", color = colors.textSecondary)
                        }
                        Button(
                            onClick = { onShareTranscript(tr.transcript + "\n\n" + tr.summary + "\n\n" + tr.actionItems) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentActivePill, contentColor = colors.accentOnActiveText)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = colors.accentOnActiveText, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "مشاركة")
                        }
                    }
                }
            }
        }
    }
}

// App Header Section — badge tappable for AI (Phase 3)
@Composable
fun HeaderSection(
    aiOptIn: Boolean = false,
    aiProcessingCount: Int = 0,
    onAiClick: () -> Unit = {}
) {
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
        
        // Aesthetic Brand Badge — tappable, reflects AI state + processing
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(if (aiOptIn) colors.accentActivePill else colors.cardSurface)
                .border(1.dp, if (aiOptIn) colors.accentActivePill else colors.cardBorder, RoundedCornerShape(30.dp))
                .clickable(onClick = onAiClick)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (aiOptIn) colors.accentOnActiveText else colors.playbackGold)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (aiProcessingCount > 0) "جاري المعالجة ($aiProcessingCount)" else stringResource(id = R.string.smart_badge_label),
                    color = if (aiOptIn) colors.accentOnActiveText else colors.playbackGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (aiOptIn) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = colors.accentOnActiveText, modifier = Modifier.size(12.dp))
                }
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

// Waveform visualizer Canvas element scanning max Amplitudes — Brush cached per frame (Phase 4)
@Composable
fun WaveformVisualizer(amplitudes: List<Float>) {
    val colors = LocalAppColors.current
    // Cache brush across recompositions (Phase 4 perf)
    val waveformBrush = remember(colors.recordingRed, colors.playbackGold) {
        Brush.verticalGradient(colors = listOf(colors.recordingRed, colors.playbackGold))
    }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .semantics { contentDescription = "مخطط الموجة الصوتية" }
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

// Detailed row cards displaying saved recordings — with mini waveform & trim (Phase 2) + AI (Phase 3)
@Composable
fun RecordingItemCard(
    item: RecordingItem,
    onPlayToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onDeleteRequest: () -> Unit,
    onRenameRequest: () -> Unit,
    onTrimRequest: () -> Unit = {},
    onAiRequest: () -> Unit = {},
    hasTranscript: Boolean = false,
    isTranscribing: Boolean = false,
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
                // Circular Play action button — stable tag via file hash (Phase 4)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (item.isPlaying) colors.recordingRedGlow else colors.cardBorder)
                        .clickable(onClick = onPlayToggle)
                        .semantics { contentDescription = if (item.isPlaying) "إيقاف" else "تشغيل" }
                        .testTag("play_pause_${item.file.absolutePath.hashCode()}"),
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
                            .semantics { contentDescription = "شريط تقدم التشغيل" }
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Actions (Share, Edit, Trim, AI)
                    ActionTextButton(
                        icon = Icons.Default.Share,
                        label = stringResource(id = R.string.share_btn_desc),
                        onClick = onShare
                    )
                    ActionTextButton(
                        icon = Icons.Default.Edit,
                        label = stringResource(id = R.string.rename_btn_desc),
                        onClick = onRenameRequest,
                        tag = "rename_${item.file.absolutePath.hashCode()}"
                    )
                    ActionTextButton(
                        icon = Icons.Default.ContentCut,
                        label = "قص",
                        onClick = onTrimRequest,
                        tag = "trim_${item.file.absolutePath.hashCode()}"
                    )
                    ActionTextButton(
                        icon = if (hasTranscript) Icons.Default.Description else Icons.Default.AutoAwesome,
                        label = if (isTranscribing) "جاري..." else if (hasTranscript) "عرض النسخ" else "ذكاء",
                        onClick = onAiRequest,
                        color = when {
                            hasTranscript -> colors.accentActivePill
                            isTranscribing -> colors.textHint
                            else -> colors.playbackGold
                        },
                        tag = "ai_${item.file.absolutePath.hashCode()}"
                    )
                }

                ActionTextButton(
                    icon = Icons.Default.Delete,
                    label = stringResource(id = R.string.delete_btn_desc),
                    onClick = onDeleteRequest,
                    color = colors.recordingRed.copy(alpha = 0.85f),
                    tag = "delete_${item.file.absolutePath.hashCode()}"
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
