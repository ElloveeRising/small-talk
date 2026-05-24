package com.ryan.smalltalk.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.random.Random
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ryan.smalltalk.model.Message

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onSend: (String, Uri?) -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onToggleThinking: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val listState = rememberLazyListState()
    var inputText by rememberSaveable { mutableStateOf("") }
    var pendingImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) pendingImageUri = uri }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onDismissError()
        }
    }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    val skin = OttoSkin.forVariant(state.activeVariant)

    Scaffold(
        containerColor = BackgroundColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header — title centered, Refresh on the left, Settings on the right, and a small
            // idle Otto peeking up from the bottom edge so the character is present even when
            // there's no inference happening.
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                AppLogo(
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 20,
                )
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "New chat", tint = IconTint)
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = IconTint)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(state.messages, key = { it.id }) { MessageBubble(it, skin) }
            }

            // Otto strip sits RIGHT ABOVE the input so when the user types he can lean
            // in and "read" what they're writing. Hidden when the IME is up in landscape
            // (too little vertical room) — Otto reappears as soon as the keyboard closes.
            val imeVisible = WindowInsets.isImeVisible
            val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current
                .orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            if (!(imeVisible && isLandscape)) {
                OttoStrip(
                    state = state,
                    inputText = inputText,
                    onToggleThinking = onToggleThinking,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                if (pendingImageUri != null) {
                    Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp)) {
                        AsyncImage(
                            model = pendingImageUri,
                            contentDescription = "Pending image",
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        IconButton(
                            onClick = { pendingImageUri = null },
                            modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                        ) { Text("✕", color = Color.White, fontSize = 12.sp) }
                    }
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    placeholder = { Text("Message Otto…", color = MutedText) },
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputFieldColor,
                        unfocusedContainerColor = InputFieldColor,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentColor,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = AccentColor,
                    ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.pipeline.visionEnabled) {
                        IconButton(onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach image", tint = IconTint)
                        }
                    }

                    // Thinking toggle — filled lightbulb when on, outlined when off. Always
                    // available so users don't have to dig into Settings to flip it.
                    IconButton(onClick = onToggleThinking) {
                        Icon(
                            if (state.thinking) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                            contentDescription = if (state.thinking)
                                "Thinking mode on — tap to disable" else "Thinking mode off — tap to enable",
                            tint = if (state.thinking) Color(0xFFFFCC44) else IconTint,
                        )
                    }
                    if (state.thinking) {
                        Text(
                            "thinking",
                            color = Color(0xFFFFCC44),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 2.dp, end = 8.dp),
                        )
                    }

                    Spacer(Modifier.weight(1f))
                    val canSend = !state.isStreaming && (inputText.isNotBlank() || pendingImageUri != null)
                    IconButton(
                        onClick = {
                            if (canSend) {
                                onSend(inputText, pendingImageUri)
                                inputText = ""
                                pendingImageUri = null
                            }
                        },
                        enabled = canSend,
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (canSend) AccentColor else Color(0xFF555555),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Persistent Otto + state-of-mind strip just under the header. Otto's mood reflects what the
 * pipeline is doing right now (typing / thinking / searching) and otherwise drifts through
 * idle behaviours: he waves on first show, yawns at random, and falls asleep after 2 min of
 * quiet so the screen feels alive even when nothing's happening.
 *
 * Tapping Otto toggles thinking mode (and resets the sleep timer).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OttoStrip(
    state: ChatUiState,
    inputText: String,
    onToggleThinking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = OttoSkin.forVariant(state.activeVariant)
    val streamingMsg = state.messages.lastOrNull()?.takeIf { it.isStreaming && it.role == "assistant" }

    // Pipeline-driven mood — the "real" thing Otto's doing.
    val pipelineMood: OttoMood? = when {
        streamingMsg?.toolStatus?.let { isSearchingStatus(it) } == true -> OttoMood.SEARCHING
        streamingMsg?.thinkingDisplay?.isNotBlank() == true -> OttoMood.THINKING
        streamingMsg != null -> OttoMood.TYPING
        else -> null
    }

    // Idle-time tracking: bumps every second, reset by activity or user interaction.
    var idleSeconds by remember { mutableIntStateOf(0) }
    var yawnUntilSecond by remember { mutableIntStateOf(-1) }
    var waveUntilSecond by remember { mutableIntStateOf(3) }   // wave the first 3 seconds
    var winkUntilSecond by remember { mutableIntStateOf(-1) }  // long-press easter egg

    LaunchedEffect(pipelineMood) {
        if (pipelineMood != null) {
            idleSeconds = 0
            yawnUntilSecond = -1
        }
    }
    LaunchedEffect(state.messages.size) { idleSeconds = 0 }
    LaunchedEffect(inputText) { if (inputText.isNotEmpty()) idleSeconds = 0 }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            idleSeconds++
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val gap = Random.nextLong(40_000, 75_000)
            delay(gap)
            if (pipelineMood == null && inputText.isEmpty() && idleSeconds in 8..115) {
                yawnUntilSecond = idleSeconds + 2
            }
        }
    }

    // Computed mood — priority order:
    //   1. Wink (long-press easter egg, brief)
    //   2. What the pipeline is doing (typing / thinking / searching)
    //   3. What the user is doing (reading their typed input)
    //   4. Idle-state behaviours (wave / sleep / yawn / thinking-on / plain idle)
    val idleMood = when {
        idleSeconds < waveUntilSecond -> OttoMood.WAVING
        idleSeconds > 120 -> OttoMood.SLEEPING
        idleSeconds < yawnUntilSecond -> OttoMood.YAWN
        state.thinking -> OttoMood.THINKING
        else -> OttoMood.IDLE
    }
    val mood = when {
        idleSeconds < winkUntilSecond -> OttoMood.WINKING
        pipelineMood != null -> pipelineMood
        inputText.isNotEmpty() -> OttoMood.READING
        else -> idleMood
    }

    val reaction = if (mood == OttoMood.READING) reactionFor(inputText) else OttoReaction.NORMAL

    val statusText = when (mood) {
        OttoMood.TYPING -> "Otto's typing…"
        OttoMood.THINKING ->
            if (streamingMsg != null) "Otto's thinking it through…"
            else "Thinking mode on — Otto will reason first"
        OttoMood.SEARCHING -> "Otto's googling something…"
        OttoMood.SLEEPING -> "Zzz — tap to wake Otto"
        OttoMood.YAWN -> "Otto's yawning"
        OttoMood.WAVING -> "Hi, I'm Otto!"
        OttoMood.EXCITED -> "Otto's hyped"
        OttoMood.READING -> when (reaction) {
            OttoReaction.CURIOUS -> "Otto's curious"
            OttoReaction.SURPRISED -> "Otto's intrigued"
            OttoReaction.FOCUSED -> "Otto's reading carefully…"
            OttoReaction.NORMAL -> "Otto's reading…"
        }
        OttoMood.WINKING -> "😉"
        OttoMood.IDLE -> "Otto's listening"
    }

    Row(
        modifier = modifier
            .combinedClickable(
                onClick = {
                    // First tap when Otto is sleeping = just wake him up, don't toggle thinking.
                    // Subsequent taps (already awake) toggle thinking mode like before.
                    val wasAsleep = idleSeconds > 120
                    idleSeconds = 0
                    yawnUntilSecond = -1
                    waveUntilSecond = -1
                    if (!wasAsleep) onToggleThinking()
                },
                onLongClick = {
                    // Easter egg: long-press Otto and he winks at you. Doesn't touch
                    // thinking mode. Resets the idle clock so he doesn't sleep mid-wink.
                    idleSeconds = 0
                    winkUntilSecond = 2   // ~2 seconds of winking
                },
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No explicit size — Otto sizes himself via his `pixel` arg (default 3 dp/px =
        // 60 × 63 dp). Forcing 48 × 50 here was squashing every pixel below the
        // intended scale and making subtle facial details (eye-shift, reactions)
        // disappear on phone screens.
        Otto(mood = mood, skin = skin, reaction = reaction)
        Spacer(Modifier.size(width = 12.dp, height = 0.dp))
        Column {
            Text(
                "Otto",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                statusText,
                color = MutedText,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(skin.body),
        )
        Spacer(Modifier.size(width = 6.dp, height = 0.dp))
        Text(
            state.activeVariant.label,
            color = MutedText,
            fontSize = 11.sp,
        )
    }
}

private fun isSearchingStatus(s: String): Boolean =
    s.contains("Search", ignoreCase = true) ||
        s.contains("Fetch", ignoreCase = true) ||
        s.contains("page", ignoreCase = true)

/**
 * Three pulsing dots used inside the message bubble while waiting for the first token.
 * Deliberately minimal — Otto himself lives up top in the OttoStrip; this is just here
 * so the empty bubble doesn't look broken in the half-second before tokens arrive.
 */
@Composable
private fun BubbleDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(4.dp),
    ) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = i * 160),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun MessageBubble(message: Message, skin: OttoSkin) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            color = if (isUser) UserBubbleColor else AssistantBubbleColor,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.imageUri != null) {
                    AsyncImage(
                        model = message.imageUri,
                        contentDescription = "Image",
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .padding(bottom = if (message.text.isNotEmpty()) 8.dp else 0.dp),
                        contentScale = ContentScale.FillWidth,
                    )
                }

                // Live thinking content shown above the answer (transient — only while streaming).
                // Otto's character avatar lives in the OttoStrip at the top of the screen; here we
                // just label this block so the reader knows what they're looking at.
                if (message.thinkingDisplay != null) {
                    Text(
                        "💭  thinking",
                        color = Color(0xFFFFCC44),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        message.thinkingDisplay,
                        color = MutedText,
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                    )
                    if (message.text.isNotEmpty() || message.isStreaming) {
                        Spacer(Modifier.height(8.dp))
                    }
                }

                when {
                    message.toolStatus != null ->
                        Text(message.toolStatus, color = Color.White, fontSize = 14.sp)
                    message.isStreaming && message.text.isEmpty() && message.thinkingDisplay == null ->
                        // No Otto here — the OttoStrip above the input is the canonical Otto.
                        // Just three minimal pulsing dots so the bubble doesn't look empty.
                        BubbleDots()
                    message.text.isNotEmpty() ->
                        if (isUser) {
                            Text(message.text, color = Color.White, fontSize = 15.sp)
                        } else {
                            MarkdownText(
                                text = message.text,
                                color = Color.White,
                                linkColor = AccentColor,
                                fontSize = 15.sp,
                            )
                        }
                }
            }
        }
    }
}
