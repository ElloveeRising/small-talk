package com.ryan.smalltalk.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.random.Random
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
        // Otto's free-roam state: when he's "away", the strip shows an empty spot and the
        // explorer overlay (drawn after the Column below) owns him.
        var ottoAway by remember { mutableStateOf(false) }
        var explorerMood by remember { mutableStateOf(OttoMood.CRAWLING) }
        var inkWipe by remember { mutableStateOf(false) }
        var expeditionDemoTick by remember { mutableStateOf(0) }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
        val screenW = maxWidth
        val screenH = maxHeight
        Column(modifier = Modifier.fillMaxSize()) {
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
                    // Otto inks the screen; the actual wipe fires at full cover (see overlay).
                    onClick = { if (!inkWipe) inkWipe = true },
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
                    ottoAway = ottoAway,
                    inking = inkWipe,
                    onToggleThinking = onToggleThinking,
                    onRequestExpedition = { expeditionDemoTick++ },
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

        // ── Otto's expeditions: every couple of minutes of calm he leaves the strip,
        // climbs the edge of the screen, strolls across the top, peeks at the world,
        // and wanders home. If the user starts typing (or an answer starts streaming)
        // he squeezes into a jet, leaves a puff of ink, and rockets straight back —
        // from wherever he happens to be. ──
        val curInput by rememberUpdatedState(inputText)
        val curStreaming by rememberUpdatedState(state.isStreaming)
        val exX = remember { Animatable(12f) }
        val exY = remember { Animatable(0f) }
        var inkPoofAt by remember { mutableStateOf<Pair<Float, Float>?>(null) }
        val homeX = 12f
        val homeY = (screenH - 150.dp).value.coerceAtLeast(0f)
        val farX = (screenW - 72.dp).value.coerceAtLeast(0f)

        suspend fun runExpedition() {
                exX.snapTo(homeX); exY.snapTo(homeY)
                explorerMood = OttoMood.CRAWLING
                ottoAway = true
                var recalled = false
                coroutineScope {
                    val path = launch {
                        exX.animateTo(6f, tween(1500))
                        exY.animateTo(10f, tween(11_000, easing = LinearEasing))    // slow climb
                        explorerMood = OttoMood.IDLE
                        delay(1700)                                                 // take in the view
                        explorerMood = OttoMood.CRAWLING
                        exX.animateTo(farX, tween(12_000, easing = LinearEasing))   // cross the top
                        explorerMood = OttoMood.READING
                        delay(1800)                                                 // peek down at the chat
                        explorerMood = OttoMood.CRAWLING
                        exX.animateTo(6f, tween(12_000, easing = LinearEasing))     // wander back
                        exY.animateTo(homeY, tween(11_000, easing = LinearEasing))  // climb down
                    }
                    val watcher = launch {
                        snapshotFlow { curInput.isNotEmpty() || curStreaming }.first { it }
                        recalled = true
                        path.cancel()
                    }
                    path.join()
                    watcher.cancel()
                }
                if (recalled) {
                    inkPoofAt = exX.value to exY.value
                    explorerMood = OttoMood.JETTING
                    coroutineScope {
                        joinAll(
                            launch { exX.animateTo(homeX, tween(700, easing = FastOutSlowInEasing)) },
                            launch { exY.animateTo(homeY, tween(700, easing = FastOutSlowInEasing)) },
                        )
                    }
                    explorerMood = OttoMood.EXCITED
                    delay(700)
                }
                ottoAway = false
        }

        LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(95_000, 160_000))
                if (curInput.isNotEmpty() || curStreaming || ottoAway || inkWipe) continue
                runExpedition()
            }
        }
        // Quadruple-tap demo: expedition on demand, wherever the cycle lands on it.
        LaunchedEffect(expeditionDemoTick) {
            if (expeditionDemoTick > 0 && !ottoAway && !inkWipe) runExpedition()
        }

        if (ottoAway) {
            Otto(
                mood = explorerMood,
                skin = skin,
                wearingCap = state.thinking,
                modifier = Modifier.offset(exX.value.dp, exY.value.dp),
            )
        }
        inkPoofAt?.let { at -> InkPoof(at) { inkPoofAt = null } }

        // ── Ink-wipe refresh: the screen floods with Otto's ink; the wipe happens at
        // full cover, and the fresh empty chat is revealed as the ink thins away. ──
        if (inkWipe) {
            val cover = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                cover.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
                onRefresh()
                delay(140)
                cover.animateTo(0f, tween(520))
                inkWipe = false
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val t = cover.value
                val ink = Color(0xFF160B2E)
                val maxR = size.maxDimension * 1.15f
                val cx = size.width * 0.16f
                val cy = size.height * 0.80f
                drawCircle(ink.copy(alpha = (t * 0.98f).coerceIn(0f, 1f)), maxR * t, Offset(cx, cy))
                drawCircle(ink.copy(alpha = (t * 0.90f).coerceIn(0f, 1f)), maxR * t * 0.78f, Offset(cx + 80f, cy - 160f))
                drawCircle(ink.copy(alpha = (t * 0.85f).coerceIn(0f, 1f)), maxR * t * 0.60f, Offset(cx + 180f, cy + 40f))
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
    ottoAway: Boolean,
    inking: Boolean,
    onToggleThinking: () -> Unit,
    onRequestExpedition: () -> Unit,
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
    var confusedUntilSecond by remember { mutableIntStateOf(-1) } // shown on errors

    // Stroll: Otto occasionally walks across the strip so he isn't stuck on one side.
    // `strollX` is 0 (home, far left) → 1 (far right); animated smoothly. `strolling`
    // gates the CRAWLING mood + the status text + fading the labels.
    var strolling by remember { mutableStateOf(false) }
    var strollTarget by remember { mutableFloatStateOf(0f) }
    val strollX by animateFloatAsState(
        targetValue = strollTarget,
        animationSpec = tween(2000, easing = FastOutSlowInEasing),
        label = "strollX",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (strolling) 0f else 1f,
        animationSpec = tween(350),
        label = "labelAlpha",
    )

    // Rare acts: every ~8-12 calm minutes Otto does something nobody asked for —
    // a little dance, a painting session, or blowing a bubble. The fish swims on
    // its own, rarer schedule.
    val curPipeline by rememberUpdatedState(pipelineMood)
    val curInputText by rememberUpdatedState(inputText)
    var actMood by remember { mutableStateOf<OttoMood?>(null) }
    var bubbleActive by remember { mutableStateOf(false) }
    val bubbleT = remember { Animatable(0f) }
    var fishActive by remember { mutableStateOf(false) }
    val fishT = remember { Animatable(0f) }
    var recentTaps by remember { mutableStateOf(listOf<Long>()) }
    var demoRequest by remember { mutableStateOf(0) }

    LaunchedEffect(pipelineMood) {
        if (pipelineMood != null) {
            idleSeconds = 0
            yawnUntilSecond = -1
        }
    }
    LaunchedEffect(state.messages.size) { idleSeconds = 0 }
    LaunchedEffect(inputText) { if (inputText.isNotEmpty()) idleSeconds = 0 }
    // Error → Otto looks confused for a few seconds
    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null) confusedUntilSecond = idleSeconds + 3
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            idleSeconds++
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(40_000, 75_000))
            if (pipelineMood == null && inputText.isEmpty() && idleSeconds in 8..115) {
                yawnUntilSecond = idleSeconds + 2
            }
        }
    }
    // Stroll scheduler — every ~30-55s of calm idleness, take a walk across and back.
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(30_000, 55_000))
            val calm = pipelineMood == null && inputText.isEmpty() &&
                idleSeconds in 10..110 && state.errorMessage == null
            if (calm && !strolling) {
                strolling = true
                strollTarget = 1f      // walk to the right
                delay(2200)
                strollTarget = 0f      // walk back home
                delay(2200)
                strolling = false
            }
        }
    }
    // Rare-act scheduler (~every 10 minutes of use).
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(480_000, 720_000))
            val calm = curPipeline == null && curInputText.isEmpty() &&
                idleSeconds in 5..115 && !strolling && actMood == null
            if (!calm) continue
            when (Random.nextInt(3)) {
                0 -> { actMood = OttoMood.DANCING; delay(4_500); actMood = null }
                1 -> { actMood = OttoMood.PAINTING; delay(6_800); actMood = null }
                else -> {
                    bubbleActive = true
                    bubbleT.snapTo(0f)
                    bubbleT.animateTo(1f, tween(2_600, easing = LinearEasing))
                    bubbleActive = false
                }
            }
        }
    }
    // A tiny fish, once in a while. Otto doesn't comment on it. Neither do we.
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(360_000, 600_000))
            if (curPipeline == null && curInputText.isEmpty() && !fishActive) {
                fishActive = true
                fishT.snapTo(0f)
                fishT.animateTo(1f, tween(6_500, easing = LinearEasing))
                fishActive = false
            }
        }
    }
    // Quadruple-tap demo executor: cycles dance → paint → bubble → fish → expedition,
    // so nobody has to wait ten minutes to film a dance. try/finally keeps a cancelled
    // act (another quad-tap mid-dance) from leaving Otto stuck in a pose.
    LaunchedEffect(demoRequest) {
        if (demoRequest == 0) return@LaunchedEffect
        when ((demoRequest - 1) % 5) {
            0 -> if (actMood == null) {
                try { actMood = OttoMood.DANCING; delay(4_500) } finally { actMood = null }
            }
            1 -> if (actMood == null) {
                try { actMood = OttoMood.PAINTING; delay(6_800) } finally { actMood = null }
            }
            2 -> if (!bubbleActive) {
                try {
                    bubbleActive = true
                    bubbleT.snapTo(0f)
                    bubbleT.animateTo(1f, tween(2_600, easing = LinearEasing))
                } finally { bubbleActive = false }
            }
            3 -> if (!fishActive) {
                try {
                    fishActive = true
                    fishT.snapTo(0f)
                    fishT.animateTo(1f, tween(6_500, easing = LinearEasing))
                } finally { fishActive = false }
            }
            else -> onRequestExpedition()
        }
    }

    val reaction = if (!strolling && inputText.isNotEmpty()) reactionFor(inputText) else OttoReaction.NORMAL

    // Computed mood — priority order: confused > wink > stroll > pipeline > reading > idle.
    val idleMood = when {
        idleSeconds < waveUntilSecond -> OttoMood.WAVING
        idleSeconds > 120 -> OttoMood.SLEEPING
        idleSeconds < yawnUntilSecond -> OttoMood.YAWN
        else -> OttoMood.IDLE
    }
    val mood = when {
        inking -> OttoMood.JETTING            // squeeze! that ink came from somewhere
        idleSeconds < confusedUntilSecond -> OttoMood.CONFUSED
        idleSeconds < winkUntilSecond -> OttoMood.WINKING
        actMood != null -> actMood!!
        strolling -> OttoMood.CRAWLING
        pipelineMood != null -> pipelineMood
        inputText.isNotEmpty() -> OttoMood.READING
        else -> idleMood
    }

    // The cap = "thinking mode is on", shown in every mood except confusion (so the
    // "?" reads cleanly). This is what keeps the cap on while Otto reads your input.
    val wearingCap = state.thinking && mood != OttoMood.CONFUSED

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
        OttoMood.CONFUSED -> "Otto's stumped…"
        OttoMood.CRAWLING -> "Otto's stretching his legs…"
        OttoMood.JETTING -> "💨"
        OttoMood.DANCING -> "Otto's vibing ♪"
        OttoMood.PAINTING -> "Otto's painting…"
        OttoMood.IDLE ->
            if (state.thinking) "Thinking mode on" else "Otto's listening"
    }
    val shownStatus = if (ottoAway) "Otto's off exploring the screen… 🐙" else statusText

    BoxWithConstraints(
        modifier = modifier
            .combinedClickable(
                onClick = {
                    val wasAsleep = idleSeconds > 120
                    idleSeconds = 0
                    yawnUntilSecond = -1
                    waveUntilSecond = -1
                    if (!wasAsleep) onToggleThinking()
                    // Quadruple-tap within 1.6 s: demo a rare act on demand. Four taps
                    // also flip thinking four times — a net no-op, nothing disturbed.
                    val now = System.currentTimeMillis()
                    recentTaps = (recentTaps + now).filter { now - it < 1600 }
                    if (recentTaps.size >= 4) {
                        recentTaps = emptyList()
                        demoRequest++
                    }
                },
                onLongClick = {
                    idleSeconds = 0
                    winkUntilSecond = 2   // ~2 seconds of winking
                },
            )
            .padding(vertical = 4.dp),
    ) {
        val ottoWidth = 60.dp   // Otto's natural footprint at 3 dp/px
        val maxTravel = (maxWidth - ottoWidth).coerceAtLeast(0.dp)

        // Labels (name + status on the left, model chip on the right). Fades out while
        // Otto strolls over them, fades back when he returns home.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                .alpha(labelAlpha),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(ottoWidth + 12.dp))   // reserve Otto's home spot
            Column {
                Text("Otto", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(shownStatus, color = MutedText, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(skin.body),
            )
            Spacer(Modifier.width(6.dp))
            Text(state.activeVariant.label, color = MutedText, fontSize = 11.sp)
        }

        // Otto himself, offset horizontally during a stroll — unless he's off on an
        // expedition, in which case the explorer overlay owns him and his spot sits
        // conspicuously empty.
        if (!ottoAway) {
            Otto(
                mood = mood,
                skin = skin,
                reaction = reaction,
                wearingCap = wearingCap,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxTravel * strollX),
            )
        }

        // A bubble he blew, drifting up and away.
        if (bubbleActive) {
            val t = bubbleT.value
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 34.dp + 10.dp * t, y = (-6).dp - 30.dp * t)
                    .size(10.dp),
            ) {
                drawCircle(
                    Color.White.copy(alpha = (0.45f * (1f - t * 0.6f)).coerceIn(0f, 1f)),
                    3.dp.toPx() + 1.5.dp.toPx() * t,
                    center,
                )
            }
        }
        // The fish. It has places to be.
        if (fishActive) {
            PixelFish(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (maxWidth - 20.dp) * fishT.value, y = (-4).dp),
            )
        }

        // While strolling, show his status centered so the user can still read it.
        if (strolling) {
            Text(
                shownStatus,
                color = MutedText,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Expanding, fading puff of ink with a couple of escaping bubbles — dropped at the
 * spot Otto jets away from.
 */
@Composable
private fun InkPoof(at: Pair<Float, Float>, onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(at) { t.animateTo(1f, tween(750)); onDone() }
    Canvas(
        modifier = Modifier
            .offset(at.first.dp, at.second.dp)
            .size(64.dp),
    ) {
        val v = t.value
        val alpha = ((1f - v) * 0.6f).coerceIn(0f, 1f)
        val ink = Color(0xFF221140)
        drawCircle(ink.copy(alpha = alpha), 12.dp.toPx() + 26.dp.toPx() * v, center)
        drawCircle(
            ink.copy(alpha = alpha * 0.8f), 8.dp.toPx() + 20.dp.toPx() * v,
            center + Offset(16.dp.toPx() * v, -10.dp.toPx() * v),
        )
        drawCircle(
            ink.copy(alpha = alpha * 0.7f), 6.dp.toPx() + 15.dp.toPx() * v,
            center + Offset(-13.dp.toPx() * v, 9.dp.toPx() * v),
        )
        drawCircle(
            Color.White.copy(alpha = alpha * 0.5f), 2.dp.toPx(),
            center + Offset(0f, -30.dp.toPx() * v),
        )
        drawCircle(
            Color.White.copy(alpha = alpha * 0.35f), 1.5.dp.toPx(),
            center + Offset(9.dp.toPx(), -22.dp.toPx() * v),
        )
    }
}

/** A six-by-five-pixel fish. Swims left to right. Knows nothing about language models. */
@Composable
private fun PixelFish(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp, 10.dp)) {
        val p = 2.dp.toPx()
        fun fx(x: Int, y: Int, c: Color) =
            drawRect(c, topLeft = Offset(x * p, y * p), size = Size(p, p))
        val body = Color(0xFF6FB7D9)
        val dark = Color(0xFF2A5A75)
        fx(0, 0, body); fx(0, 4, body)            // tail tips
        fx(1, 1, body); fx(1, 2, body); fx(1, 3, body)
        for (x in 2..5) fx(x, 1, body)
        for (x in 2..6) fx(x, 2, body)
        for (x in 2..5) fx(x, 3, body)
        fx(5, 1, dark)                            // eye
        fx(7, 2, body)                            // pouty little mouth
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
