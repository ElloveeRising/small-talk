package com.ryan.smalltalk.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

    var hasAllFiles by remember { mutableStateOf(ModelFiles.hasAllFilesAccess()) }
    var showAdvanced by remember { mutableStateOf(false) }
    var manualPath by remember { mutableStateOf(ModelFiles.getResponderPath(context) ?: "") }
    // True after the user has tapped "Grant access" at least once — used to show a helpful
    // "find the toggle on the screen that opened" hint if they come back without granting.
    var permissionAttempted by remember { mutableStateOf(false) }

    val expectedFile = remember { File(ModelDownloader.ensureModelsDir(), E2B_FILENAME) }
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
                    "Couldn't read that file's location. Paste the full path below instead.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    // Launch the download on the app-lifetime scope so rotating the phone or briefly
    // leaving the screen won't cancel a multi-GB transfer.
    fun startDownload() {
        downloader.reset()
        app.appScope.launch {
            val path = downloader.download(context, ModelDownloader.E2B_URL, E2B_FILENAME)
            if (path != null) ModelFiles.setResponderPath(context, path)
        }
    }

    val isDownloading = downloadState is ModelDownloader.State.Downloading
    val downloadDone = downloadState is ModelDownloader.State.Done

    // Resolve a usable model path, re-checking whenever permission is granted, a download
    // finishes, or a manual path is entered. Re-checking on `hasAllFiles` matters: a model
    // file already sitting in shared storage only becomes visible to File.exists() AFTER
    // all-files access is granted (Android scoped storage).
    val resolvedModelPath: String? = remember(hasAllFiles, downloadDone, manualPath) {
        when {
            ModelFiles.isReadable(manualPath) -> manualPath
            expectedFile.exists() && expectedFile.length() > 0 -> expectedFile.absolutePath
            else -> null
        }
    }
    val modelReady = resolvedModelPath != null

    // Persist the resolved path so ChatViewModel.startModelLoading() can find it. THIS is the
    // fix for "both steps green but Wake Otto up won't proceed": when the brain file already
    // existed (rather than being downloaded this session) its location was never written to
    // prefs, so model loading bounced straight back to the setup screen.
    LaunchedEffect(resolvedModelPath) {
        if (resolvedModelPath != null) ModelFiles.setResponderPath(context, resolvedModelPath)
    }

    val canStart = hasAllFiles && modelReady

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        // ── Otto, front and center ──
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Otto(
                mood = when {
                    isDownloading -> OttoMood.TYPING
                    modelReady || downloadState is ModelDownloader.State.Done -> OttoMood.EXCITED
                    else -> OttoMood.WAVING
                },
                skin = OttoSkin.PURPLE,
                pixel = 5.dp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Hi, I'm Otto.",
            color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "I'm a little assistant who lives entirely on your phone — nothing you tell me ever " +
                "leaves this device. Let's get me set up. It's just two steps, and I'll wait " +
                "for you at each one.",
            color = MutedText, fontSize = 14.sp,
        )

        // ── Step 1: Permission ──
        StepCard(
            title = "Step 1 of 2 — Let me reach my brain file",
            done = hasAllFiles,
        ) {
            if (hasAllFiles) {
                Text(
                    "Done — thanks! On to step 2. 👇",
                    color = Color(0xFF6ad97c), fontSize = 13.sp,
                )
            } else {
                Text(
                    "My \"brain\" is a big file that sits in your phone's storage. Android needs " +
                        "you to give me permission to read it. When you tap the button below, a " +
                        "settings screen opens — just flip the switch ON for \"Small Talk\", then " +
                        "tap back. That's it.",
                    color = MutedText, fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        permissionAttempted = true
                        allFilesLauncher.launch(ModelFiles.allFilesAccessIntent(context))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                ) { Text("Give permission", color = Color.White) }
                if (permissionAttempted) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Didn't work? Open the settings screen again, find \"Small Talk\" in the " +
                            "list, and turn its switch ON.",
                        color = Color(0xFFffb070), fontSize = 12.sp,
                    )
                }
            }
        }

        // ── Step 2: Download the brain ──
        StepCard(
            title = "Step 2 of 2 — Download my brain",
            done = modelReady,
            dimmed = !hasAllFiles,
        ) {
            when {
                !hasAllFiles -> {
                    Text(
                        "Finish step 1 first, then I'll grab my brain.",
                        color = MutedText, fontSize = 13.sp,
                    )
                }
                modelReady -> {
                    Text("My brain's ready ✓", color = Color(0xFF6ad97c), fontSize = 14.sp)
                    Text(
                        "Stored safely in your Downloads folder. You only download this once.",
                        color = MutedText, fontSize = 12.sp,
                    )
                }
                isDownloading -> {
                    DownloadProgressBlock(downloadState as ModelDownloader.State.Downloading)
                }
                downloadState is ModelDownloader.State.Failed -> {
                    val reason = (downloadState as ModelDownloader.State.Failed).reason
                    Text(
                        "Hmm, the download didn't finish.",
                        color = Color(0xFFff7070), fontSize = 14.sp,
                    )
                    Text(
                        "($reason)\nCheck you're on Wi-Fi with a steady connection, then try again.",
                        color = MutedText, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { startDownload() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                    ) { Text("Try again", color = Color.White) }
                }
                else -> {
                    Text(
                        "I run on Gemma — Google's compact on-device model. The download is about " +
                            "3 GB, so a few things to know:",
                        color = MutedText, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Bullet("Use Wi-Fi — it's a big file.")
                    Bullet("Keep this screen open while it downloads.")
                    Bullet("It takes a few minutes. You'll see a progress bar.")
                    Bullet("You only ever do this once.")
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { startDownload() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                    ) { Text("Download my brain (~3 GB)", color = Color.White) }
                }
            }

            // Advanced fallback — quiet, for people who already have the file.
            if (!modelReady && !isDownloading) {
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        if (showAdvanced) "Hide advanced" else "Already have the file? Tap here",
                        color = MutedText, fontSize = 12.sp,
                    )
                }
                if (showAdvanced) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Point me at a .litertlm file already on your phone. It must be in shared " +
                            "storage (like your Downloads folder).",
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
                        placeholder = {
                            Text("/storage/emulated/0/…/gemma-4-E2B-it.litertlm",
                                color = MutedText, fontSize = 11.sp)
                        },
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
                        if (manualOk) "Found it ✓" else "Not found yet",
                        color = if (manualOk) Color(0xFF6ad97c) else Color(0xFFff7070),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                // Belt-and-suspenders: make sure the path is saved before we hand off to
                // model loading, so it can never bounce back for a missing pref.
                resolvedModelPath?.let { ModelFiles.setResponderPath(context, it) }
                onReady()
            },
            enabled = canStart,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentColor,
                disabledContainerColor = Color(0xFF3a3a55),
            ),
        ) {
            Text(
                if (canStart) "Wake Otto up →" else "Finish both steps to continue",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text("•  ", color = AccentColor, fontSize = 13.sp)
        Text(text, color = MutedText, fontSize = 13.sp)
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
            secs < 3600 -> "~${secs / 60} min left"
            else -> "~${secs / 3600} hr left"
        }
    } else ""
    val rateStr = if (s.bytesPerSec > 0) "${(s.bytesPerSec / 1_000_000.0).format(1)} MB/s" else ""

    Column {
        Text("Downloading my brain… hang tight! 🐙", color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        if (s.totalBytes > 0) {
            LinearProgressIndicator(
                progress = { s.pct },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = AccentColor,
                trackColor = Color(0xFF333355),
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(8.dp),
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
        Spacer(Modifier.height(6.dp))
        Text(
            "Keep this screen open. You can put the phone down — just don't close the app.",
            color = MutedText, fontSize = 11.sp,
        )
    }
}

@Composable
private fun StepCard(
    title: String,
    done: Boolean = false,
    dimmed: Boolean = false,
    content: @Composable () -> Unit,
) {
    val border = when {
        done -> Color(0xFF6ad97c)
        dimmed -> Color(0xFF2a2a4a)
        else -> AccentColor
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AssistantBubbleColor, RoundedCornerShape(12.dp))
            .border(
                width = if (done || !dimmed) 1.5.dp else 1.dp,
                color = border,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (done) {
                Text("✓ ", color = Color(0xFF6ad97c), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                title,
                color = if (dimmed) MutedText else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
