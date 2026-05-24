package com.ryan.smalltalk.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryan.smalltalk.SmallTalkApp
import com.ryan.smalltalk.llm.ModelDownloader
import com.ryan.smalltalk.llm.ModelFiles
import kotlinx.coroutines.launch
import java.io.File

private const val E2B_FILENAME = "gemma-4-E2B-it.litertlm"

@Composable
fun SetupScreen(onReady: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SmallTalkApp
    val downloader = app.downloader
    val downloadState by downloader.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var hasAllFiles by remember { mutableStateOf(ModelFiles.hasAllFilesAccess()) }
    var showAdvanced by remember { mutableStateOf(false) }
    var manualPath by remember { mutableStateOf(ModelFiles.getResponderPath(context) ?: "") }

    // Local detection: does the model file exist at the expected download path? If yes, we
    // skip the download CTA and go straight to "Start". This also catches the case where the
    // user re-opens setup after a successful download.
    val expectedFile = remember { File(ModelDownloader.ensureModelsDir(), E2B_FILENAME) }
    var modelReady by remember { mutableStateOf(expectedFile.exists() && expectedFile.length() > 0) }
    // Picked-manually fallback is also acceptable
    val manualOk = ModelFiles.isReadable(manualPath)

    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { hasAllFiles = ModelFiles.hasAllFilesAccess() }

    val manualPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val resolved = ModelFiles.resolveDocumentUriToPath(uri)
            if (resolved != null) {
                manualPath = resolved
                ModelFiles.setResponderPath(context, resolved)
            } else {
                Toast.makeText(
                    context,
                    "Couldn't resolve a real file path. Paste the path manually below.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val canStart = hasAllFiles && (modelReady || manualOk)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        // ── Otto front and center ──
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Otto(
                mood = when {
                    downloadState is ModelDownloader.State.Downloading -> OttoMood.TYPING
                    downloadState is ModelDownloader.State.Done || modelReady -> OttoMood.EXCITED
                    else -> OttoMood.IDLE
                },
                skin = OttoSkin.PURPLE,
                pixel = 5.dp,
                modifier = Modifier.size(100.dp, 105.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Hi, I'm Otto.",
            color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "I'm a small assistant that runs entirely on your phone — nothing you tell me leaves " +
                "this device. To get started, I need a brain. Two steps and you're in.",
            color = MutedText, fontSize = 14.sp,
        )

        // ── Step 1: Permission ──
        StepCard("1. Allow file access") {
            if (hasAllFiles) {
                Text("Granted ✓", color = Color(0xFF6ad97c), fontSize = 13.sp)
            } else {
                Text(
                    "Otto's brain is a multi-gig file that lives on your phone's storage. I " +
                        "read it directly from disk (never copied). Tap to allow.",
                    color = MutedText, fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    allFilesLauncher.launch(ModelFiles.allFilesAccessIntent(context))
                }) { Text("Grant access") }
            }
        }

        // ── Step 2: Download or use existing ──
        StepCard("2. Give Otto a brain") {
            when (val s = downloadState) {
                is ModelDownloader.State.Downloading -> DownloadProgressBlock(s)
                is ModelDownloader.State.Done -> {
                    // Side-effect: persist the path so ModelFiles.isConfigured() flips true
                    LaunchedEffectOnce(s.absolutePath) {
                        ModelFiles.setResponderPath(context, s.absolutePath)
                        modelReady = true
                    }
                    Text("Brain ready ✓", color = Color(0xFF6ad97c), fontSize = 13.sp)
                    Text(
                        "Saved to Downloads/${ModelDownloader.MODELS_DIR_PUBLIC}/",
                        color = MutedText, fontSize = 11.sp,
                    )
                }
                is ModelDownloader.State.Failed -> {
                    Text(
                        "Download failed: ${s.reason}",
                        color = Color(0xFFff7070), fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            downloader.reset()
                            coroutineScope.launch {
                                val path = downloader.download(
                                    context = context,
                                    url = ModelDownloader.E2B_URL,
                                    filename = E2B_FILENAME,
                                )
                                if (path != null) {
                                    ModelFiles.setResponderPath(context, path)
                                    modelReady = true
                                }
                            }
                        }) { Text("Try again") }
                        TextButton(onClick = { showAdvanced = true }) {
                            Text("Use a file I already have", color = AccentColor)
                        }
                    }
                }
                ModelDownloader.State.Idle -> {
                    if (modelReady) {
                        Text("Brain found ✓", color = Color(0xFF6ad97c), fontSize = 13.sp)
                        Text(
                            "Already at Downloads/${ModelDownloader.MODELS_DIR_PUBLIC}/.",
                            color = MutedText, fontSize = 11.sp,
                        )
                    } else {
                        Text(
                            "Otto runs on Gemma 4 E2B — Google's compact on-device model. " +
                                "About 3 GB, one-time download.",
                            color = MutedText, fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                if (!hasAllFiles) {
                                    Toast.makeText(
                                        context,
                                        "Grant file access first.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    return@OutlinedButton
                                }
                                coroutineScope.launch {
                                    val path = downloader.download(
                                        context = context,
                                        url = ModelDownloader.E2B_URL,
                                        filename = E2B_FILENAME,
                                    )
                                    if (path != null) {
                                        ModelFiles.setResponderPath(context, path)
                                        modelReady = true
                                    }
                                }
                            },
                        ) { Text("Download Otto's brain") }
                    }
                }
            }

            // Advanced section — "I already have a file" fallback
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { showAdvanced = !showAdvanced },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(
                    if (showAdvanced) "Hide advanced" else "Already have a file? Set up manually",
                    color = MutedText, fontSize = 12.sp,
                )
            }
            if (showAdvanced) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Point Otto at any .litertlm file on your phone. The file must be on the " +
                        "shared storage (e.g. /storage/emulated/0/Download/…) — files inside " +
                        "another app's private folder won't work.",
                    color = MutedText, fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { manualPicker.launch(arrayOf("*/*")) }) {
                    Text("Choose file")
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = manualPath,
                    onValueChange = {
                        manualPath = it
                        if (ModelFiles.isReadable(it)) ModelFiles.setResponderPath(context, it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("/storage/emulated/0/…/gemma-4-E2B-it.litertlm",
                        color = MutedText, fontSize = 11.sp) },
                    singleLine = true,
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
                Text(
                    if (manualOk) "Found ✓" else "Not found yet",
                    color = if (manualOk) Color(0xFF6ad97c) else Color(0xFFff7070),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onReady,
            enabled = canStart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentColor,
                disabledContainerColor = Color(0xFF3a3a55),
            ),
        ) {
            Text(
                if (canStart) "Wake Otto up" else "Finish the steps above",
                color = Color.White,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DownloadProgressBlock(s: ModelDownloader.State.Downloading) {
    val totalMb = if (s.totalBytes > 0) "%.0f".format(s.totalBytes / 1_000_000.0) else "?"
    val gotMb = "%.0f".format(s.bytesSoFar / 1_000_000.0)
    val pctStr = if (s.totalBytes > 0) "${(s.pct * 100).toInt()}%" else ""
    val eta = if (s.bytesPerSec > 0 && s.totalBytes > 0) {
        val remaining = s.totalBytes - s.bytesSoFar
        val secs = remaining / s.bytesPerSec
        when {
            secs < 60 -> "~${secs}s left"
            secs < 3600 -> "~${secs / 60}m left"
            else -> "~${secs / 3600}h left"
        }
    } else ""
    val rateStr = if (s.bytesPerSec > 0) "${(s.bytesPerSec / 1_000_000.0).format(1)} MB/s" else ""

    Column {
        Text("Downloading Otto's brain…", color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        if (s.totalBytes > 0) {
            LinearProgressIndicator(
                progress = { s.pct },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = AccentColor,
                trackColor = Color(0xFF333355),
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = AccentColor,
                trackColor = Color(0xFF333355),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("$gotMb / $totalMb MB  $pctStr", color = MutedText, fontSize = 11.sp)
            Text(listOf(rateStr, eta).filter { it.isNotBlank() }.joinToString(" · "),
                color = MutedText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StepCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AssistantBubbleColor, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** Runs `block` once when [key] becomes non-null (so a Done state side-effect fires once). */
@Composable
private fun LaunchedEffectOnce(key: Any, block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(key) { block() }
}

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
