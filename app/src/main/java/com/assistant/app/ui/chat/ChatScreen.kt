package com.assistant.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.assistant.app.R
import com.assistant.app.data.AttachmentIngester
import com.assistant.app.data.ChatLlmState
import com.assistant.app.data.ChatStatus
import com.assistant.app.data.SharedContent
import com.assistant.app.data.VoiceStatus
import com.assistant.app.llm.model.Role
import com.assistant.app.ui.components.AssistantTopBar
import com.assistant.app.ui.components.AttachmentSheet
import com.assistant.app.ui.components.AttachmentThumbnail
import com.assistant.app.ui.components.Composer
import com.assistant.app.ui.components.ComposerAttachAction
import com.assistant.app.ui.components.MESSAGE_LIST_BOTTOM_PADDING
import com.assistant.app.ui.components.MessageList
import com.assistant.app.ui.components.PromptTemplateEditor
import com.assistant.app.ui.theme.AppMotion
import com.assistant.app.ui.theme.AppSpacing
import com.assistant.app.ui.theme.AppShape
import com.assistant.app.ui.theme.appTween
import kotlinx.coroutines.launch
import java.io.File

private val TOP_BAR_HEIGHT = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    viewModelFactory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
    pendingConversationId: String? = null,
    onOpenDrawer: (() -> Unit)? = null,
    onOpenProviderSetup: (() -> Unit)? = null,
    pendingShare: SharedContent? = null,
    onShareConsumed: () -> Unit = {},
) {
    val viewModel: ChatViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsState()
    val chatLlmState by viewModel.chatLlm.collectAsState()
    val reasoningVisible by viewModel.reasoningVisible.collectAsState()
    val voiceOut by viewModel.voiceOutputEnabled.collectAsState()
    val templates by viewModel.templates.collectAsState()
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var draftBeforeEdit by remember { mutableStateOf("") }
    var showMicRationale by remember { mutableStateOf(false) }
    var showQuickActions by remember { mutableStateOf(false) }
    var saveTemplateRequest by remember { mutableStateOf<String?>(null) }
    var unfilledPlaceholders by remember { mutableStateOf<List<String>>(emptyList()) }

    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showMicRationale = false
            viewModel.onMicClick()
        } else {
            showMicRationale = true
        }
    }
    val startVoiceInput: () -> Unit = {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            viewModel.onMicClick()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(AttachmentIngester.MAX_IMAGES_PER_MESSAGE),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addImageAttachments(uris)
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.addTextAttachment(uri)
    }
    var cameraTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { captured ->
        val target = cameraTarget?.let(Uri::parse)
        val file = cameraFilePath?.let(::File)
        cameraTarget = null
        cameraFilePath = null
        if (captured && target != null) {
            viewModel.addImageAttachments(listOf(target))
        } else {
            file?.delete()
        }
    }
    val launchCamera: () -> Unit = {
        cameraFilePath?.let { File(it).delete() }
        val file = File(
            context.cacheDir,
            "camera/IMG_${System.currentTimeMillis()}.jpg",
        ).apply { parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraTarget = uri.toString()
        cameraFilePath = file.absolutePath
        cameraLauncher.launch(uri)
    }
    DisposableEffect(Unit) {
        viewModel.setChatScreenActive(true)
        onDispose {
            viewModel.setChatScreenActive(false)
            cameraFilePath?.let { File(it).delete() }
        }
    }

    val attachActions = if (viewModel.attachmentSupport) {
        listOf(
            ComposerAttachAction(
                label = stringResource(R.string.attach_photos),
                contentDescription = stringResource(R.string.cd_attach_photos),
                icon = R.drawable.ic_image,
                onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            ),
            ComposerAttachAction(
                label = stringResource(R.string.attach_camera),
                contentDescription = stringResource(R.string.cd_attach_camera),
                icon = R.drawable.ic_camera,
                onClick = launchCamera,
            ),
            ComposerAttachAction(
                label = stringResource(R.string.attach_file),
                contentDescription = stringResource(R.string.cd_attach_file),
                icon = R.drawable.ic_attach_file,
                onClick = { filePicker.launch(arrayOf("*/*")) },
            ),
        )
    } else {
        emptyList()
    }
    var showAttachSheet by remember { mutableStateOf(false) }
    if (showAttachSheet) {
        AttachmentSheet(
            actions = attachActions,
            subtitles = mapOf(
                stringResource(R.string.attach_photos) to R.string.attach_photos_subtitle,
                stringResource(R.string.attach_camera) to R.string.attach_camera_subtitle,
                stringResource(R.string.attach_file) to R.string.attach_file_subtitle,
            ),
            onDismiss = { showAttachSheet = false },
            searchEnabled = state.searchEnabled,
            onToggleSearch = viewModel::toggleSearch,
        )
    }

    LaunchedEffect(pendingConversationId) {
        if (pendingConversationId != null) {
            viewModel.openConversation(pendingConversationId)
        }
    }

    // Staged for review, never sent: the user still presses send.
    LaunchedEffect(pendingShare) {
        val share = pendingShare ?: return@LaunchedEffect
        viewModel.stageSharedContent(share)
        onShareConsumed()
    }

    if (showQuickActions) {
        QuickActionsSheet(
            templates = templates,
            onQuickAction = {
                showQuickActions = false
                viewModel.applyQuickAction(it)
            },
            onTemplate = {
                showQuickActions = false
                val filled = viewModel.applyTemplate(it)
                unfilledPlaceholders = filled.missingValues
            },
            onSaveAsTemplate = {
                showQuickActions = false
                if (state.draft.isNotBlank()) saveTemplateRequest = state.draft
            },
            onDismiss = { showQuickActions = false },
        )
    }

    saveTemplateRequest?.let { initialBody ->
        val existing = templates.firstOrNull { it.body == initialBody }
        PromptTemplateEditor(
            existing = existing,
            onSave = { title, body ->
                viewModel.saveTemplate(existing?.id, title, body)
                saveTemplateRequest = null
            },
            onDismiss = { saveTemplateRequest = null },
        )
    }

    val error = state.status as? ChatStatus.Error
    val isGenerating = state.status is ChatStatus.Generating
    val isSearching = state.status is ChatStatus.Searching

    val isBusy = isGenerating || isSearching
    val voiceActive = state.voiceStatus == VoiceStatus.Listening ||
        state.voiceStatus == VoiceStatus.Processing ||
        state.voiceStatus == VoiceStatus.Speaking
    val showSetupPrompt = (state.needsSetup || chatLlmState is ChatLlmState.NeedsSetup) && state.messages.isEmpty()
    val isPendingLoad = pendingConversationId != null && state.messages.isEmpty()
    val isInitialLoading = chatLlmState is ChatLlmState.Loading && state.messages.isEmpty()
    val suggestions = state.messages.lastOrNull()
        ?.takeIf { !isBusy && it.role == Role.ASSISTANT }
        ?.followUps
        .orEmpty()
    val composerHint: () -> String? = {
        when {
            showMicRationale -> context.getString(R.string.voice_mic_rationale)
            state.voiceHint -> context.getString(R.string.voice_no_match_hint)
            else -> state.searchNotice
        }
    }

    val showSearchingHint = isSearching && state.messages.lastOrNull()?.content?.isNotBlank() == true

    val listState = rememberLazyListState()
    val listScope = rememberCoroutineScope()

    val density = LocalDensity.current
    val contentBottomPaddingPx = with(density) { MESSAGE_LIST_BOTTOM_PADDING.roundToPx() }

    val atLatest = remember(listState, contentBottomPaddingPx) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last != null && last.index == info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportSize.height - contentBottomPaddingPx
        }
    }

    var pendingSendPositioning by remember { mutableStateOf(false) }
    var sendBaselineIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sendBaselineConversationId by remember { mutableStateOf<String?>(null) }
    var pendingEditedMessageId by remember { mutableStateOf<String?>(null) }
    var pendingJumpToBottom by remember { mutableStateOf(false) }

    LaunchedEffect(pendingConversationId) {
        if (pendingConversationId != null) {
            pendingSendPositioning = false
            pendingEditedMessageId = null
            sendBaselineIds = emptySet()
            sendBaselineConversationId = null
            pendingJumpToBottom = true
            viewModel.openConversation(pendingConversationId)
        }
    }

    val jumpToLatest: suspend () -> Unit = {
        if (state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex)
            listState.scrollBy(1_000_000f)
        }
    }

    LaunchedEffect(pendingJumpToBottom, state.messages.size) {
        if (!pendingJumpToBottom || state.messages.isEmpty()) return@LaunchedEffect
        pendingJumpToBottom = false
        jumpToLatest()
    }

    val messageIds = state.messages.map { it.id }
    val conversationId = state.conversationId
    LaunchedEffect(pendingSendPositioning, messageIds, conversationId) {
        if (!pendingSendPositioning) return@LaunchedEffect
        if (sendBaselineConversationId != null && sendBaselineConversationId != conversationId) {
            pendingSendPositioning = false
            pendingEditedMessageId = null
            sendBaselineIds = emptySet()
            sendBaselineConversationId = null
            return@LaunchedEffect
        }
        val baseline = sendBaselineIds
        val sent = state.messages.lastOrNull { it.role == Role.USER && it.id !in baseline }
        val edited = pendingEditedMessageId?.let { id ->
            state.messages.firstOrNull { it.id == id && it.role == Role.USER }
        }
        val target = edited ?: sent
        if (target == null) {
            if (baseline.isNotEmpty()) return@LaunchedEffect
            pendingSendPositioning = false
            pendingEditedMessageId = null
            sendBaselineConversationId = null
            return@LaunchedEffect
        }
        pendingSendPositioning = false
        pendingEditedMessageId = null
        sendBaselineIds = emptySet()
        sendBaselineConversationId = null
        listState.scrollToItem(state.messages.indexOf(target))
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling -> if (scrolling) keyboard?.hide() }
    }
    LaunchedEffect(isBusy) {
        if (!isBusy && state.draft.isBlank()) keyboard?.hide()
    }

    @OptIn(ExperimentalLayoutApi::class)
    val composer: @Composable () -> Unit = {
        Column(modifier = Modifier.readingColumn()) {
            if (state.pendingAttachments.isNotEmpty() || state.isIngestingAttachments) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.md, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    state.pendingAttachments.forEach { attachment ->
                        if (attachment.kind == com.assistant.app.llm.model.UiAttachment.Kind.IMAGE) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            ) {
                                AttachmentThumbnail(attachment = attachment, onClick = {})
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(48.dp)
                                        .clickable(
                                            onClickLabel = stringResource(R.string.cd_remove_attachment),
                                        ) { viewModel.removePendingAttachment(attachment.id) },
                                    contentAlignment = Alignment.TopEnd,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(20.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                CircleShape,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
                                ) {
                                    Icon(
                                        painter = painterResource(com.assistant.app.ui.components.AppIcons.File),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacing.xs))
                                    Text(
                                        text = attachment.displayName,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 140.dp)
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacing.xs))
                                    IconButton(
                                        onClick = { viewModel.removePendingAttachment(attachment.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.cd_remove_attachment),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (state.isIngestingAttachments) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            state.attachmentError?.let { errorText -> InlineHint(text = errorText) }
            if (unfilledPlaceholders.isNotEmpty()) {
                InlineHint(
                    text = context.getString(
                        R.string.templates_fill_placeholder_hint,
                        unfilledPlaceholders.joinToString(", "),
                    ),
                )
            }
            Composer(
                value = state.draft,
                onValueChange = {
                    viewModel.setDraft(it)
                    if (unfilledPlaceholders.isNotEmpty()) unfilledPlaceholders = emptyList()
                    if (state.voiceHint) viewModel.dismissVoiceHint()
                    if (state.attachmentError != null) viewModel.dismissAttachmentError()
                    if (state.searchNotice != null) viewModel.dismissSearchNotice()
                },
                onSend = {
                    val editing = editingMessageId
                    sendBaselineIds = state.messages.map { it.id }.toSet()
                    sendBaselineConversationId = state.conversationId
                    pendingEditedMessageId = editing
                    pendingSendPositioning = true
                    if (editing != null) {
                        viewModel.editAndResend(editing, state.draft)
                    } else {
                        viewModel.send(state.draft)
                    }
                    editingMessageId = null
                    keyboard?.hide()
                },
                onStop = viewModel::stop,
                isGenerating = isBusy,
                onMicClick = when (state.voiceStatus) {
                    VoiceStatus.Speaking, VoiceStatus.Listening, VoiceStatus.Processing -> {
                        { viewModel.onMicClick() }
                    }
                    else -> if (viewModel.isVoiceInputAvailable) startVoiceInput else null
                },
                voiceActive = voiceActive,
                voiceStatus = state.voiceStatus,
                onAttachClick = { showAttachSheet = true },
                onQuickActionsClick = { showQuickActions = true },
                searchActive = state.searchEnabled,
                onToggleSearch = viewModel::toggleSearch,
                thinkCapability = state.thinkCapability,
                thinkConfig = state.thinkConfig,
                onThinkSelect = viewModel::setThinkConfig,
                hasAttachments = state.pendingAttachments.isNotEmpty(),
                topPadding = if (suggestions.isNotEmpty()) AppSpacing.xxs else AppSpacing.sm,
            )
        }
    }

    val topBarBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            AssistantTopBar(
                title = stringResource(R.string.app_name),
                onOpenDrawer = onOpenDrawer,
                onToggleVoiceOutput = if (viewModel.ttsAvailable) viewModel::toggleVoiceOutput else null,
                voiceOutputEnabled = voiceOut,
                scrollBehavior = topBarBehavior,
            )
        },
    ) { innerPadding ->

        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val barTop = TOP_BAR_HEIGHT
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    PaddingValues(
                        start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                        top = statusBarTop,
                        end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                        bottom = innerPadding.calculateBottomPadding(),
                    ),
                )
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            val regionEnter = fadeIn(appTween(AppMotion.MEDIUM))
            val regionExit = fadeOut(appTween(AppMotion.FAST))
            AnimatedContent(
                targetState = if (showSetupPrompt || state.messages.isEmpty()) 0 else 1,
                transitionSpec = {
                    regionEnter togetherWith regionExit using SizeTransform(clip = false)
                },
                modifier = Modifier.weight(1f),
                label = "chatRegion",
            ) { region ->
                when (region) {
                    0 -> Box(modifier = Modifier.fillMaxSize().padding(top = barTop)) {
                        when {
                            isPendingLoad || isInitialLoading -> {
                                Box(modifier = Modifier.fillMaxSize())
                            }
                            showSetupPrompt -> {
                                SetupRequired(
                                    onOpenSettings = onOpenSettings,
                                    onOpenProviderSetup = onOpenProviderSetup,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            else -> {
                                EmptyHome(
                                    onPromptSelected = viewModel::setDraft,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }

                    else -> Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pointerInput(Unit) {
                                    detectTapGestures { keyboard?.hide() }
                                },
                        ) {
                            MessageList(
                                messages = state.messages,
                                status = state.status,
                                onRegenerate = viewModel::regenerate,
                                onEditAndResend = { messageId, content ->
                                    draftBeforeEdit = state.draft
                                    editingMessageId = messageId
                                    viewModel.setDraft(content)
                                },
                                onSwitchVersion = viewModel::switchVersion,
                                listState = listState,
                                showReasoning = reasoningVisible,
                                onSpeakMessage = if (viewModel.ttsAvailable && voiceOut) {
                                    viewModel::speakMessage
                                } else {
                                    null
                                },
                                topPadding = barTop,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            val showScrollAffordance = !atLatest.value
                            val fabAlpha by animateFloatAsState(
                                targetValue = if (showScrollAffordance) 1f else 0f,
                                animationSpec = appTween(AppMotion.FAST),
                                label = "scrollAffordance",
                            )
                            if (fabAlpha > 0.01f) {
                                val scrollLabel = stringResource(R.string.cd_scroll_to_latest)
                                Surface(
                                    onClick = {
                                        listScope.launch { jumpToLatest() }
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    shadowElevation = 2.dp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(AppSpacing.lg)
                                        .size(48.dp)
                                        .graphicsLayer {
                                            alpha = fabAlpha
                                            val scale = 0.85f + 0.15f * fabAlpha
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .semantics { contentDescription = scrollLabel },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = error != null,
                            enter = fadeIn(appTween(AppMotion.MEDIUM)) +
                                expandVertically(appTween(AppMotion.MEDIUM)),
                            exit = fadeOut(appTween(AppMotion.FAST)) +
                                shrinkVertically(appTween(AppMotion.FAST)),
                        ) {
                            error?.let {
                                ErrorBanner(
                                    message = it.message,
                                    onRetry = viewModel::retry,
                                    onDismiss = viewModel::dismissError,
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = editingMessageId != null,
                            enter = fadeIn(appTween(AppMotion.MEDIUM)) +
                                expandVertically(appTween(AppMotion.MEDIUM)),
                            exit = fadeOut(appTween(AppMotion.FAST)) +
                                shrinkVertically(appTween(AppMotion.FAST)),
                        ) {
                            if (editingMessageId != null) {
                                EditBanner(
                                    onCancel = {
                                        editingMessageId = null
                                        viewModel.setDraft(draftBeforeEdit)
                                        draftBeforeEdit = ""
                                    },
                                )
                            }
                        }

                        if (showSearchingHint) {
                            InlineHint(text = stringResource(R.string.status_searching_web))
                        }
                        composerHint()?.let { InlineHint(text = it) }
                        if (suggestions.isNotEmpty()) {
                            SuggestionsRow(
                                suggestions = suggestions,
                                onSelect = { suggestion ->
                                    viewModel.setDraft(suggestion)
                                    if (state.voiceHint) viewModel.dismissVoiceHint()
                                    if (state.attachmentError != null) viewModel.dismissAttachmentError()
                                    if (state.searchNotice != null) viewModel.dismissSearchNotice()
                                },
                            )
                        }
                    }
                }
            }

            if (!showSetupPrompt) {
                composer()
            }
        }
    }
}


