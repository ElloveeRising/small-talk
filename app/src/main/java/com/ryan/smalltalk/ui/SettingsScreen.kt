package com.ryan.smalltalk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import android.content.Intent
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.core.net.toUri
import com.ryan.smalltalk.llm.ModelDownloader
import com.ryan.smalltalk.llm.ModelState
import com.ryan.smalltalk.llm.ModelStatus
import com.ryan.smalltalk.llm.PipelineStatus
import com.ryan.smalltalk.llm.ResponderVariant

@Composable
fun SettingsScreen(
    pipeline: PipelineStatus,
    webAugmentation: Boolean,
    activeVariant: ResponderVariant,
    e4bAvailable: Boolean,
    e8bAvailable: Boolean,
    e4bDownloadState: ModelDownloader.State,
    onDownloadE4B: () -> Unit,
    thinking: Boolean,
    onWebAugmentationChange: (Boolean) -> Unit,
    onThinkingChange: (Boolean) -> Unit,
    onSwitchResponder: (ResponderVariant) -> Unit,
    onRefreshConversation: () -> Unit,
    onBack: () -> Unit,
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        // ── Otto preview on top, showing his current color ──
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Otto(
                    mood = OttoMood.IDLE,
                    skin = OttoSkin.forVariant(activeVariant),
                    pixel = 4.dp,
                    modifier = Modifier.size(80.dp, 84.dp),
                )
                Spacer(Modifier.size(12.dp))
                Column {
                    Text("Otto", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${activeVariant.label} brain",
                        color = MutedText, fontSize = 12.sp,
                    )
                }
            }
        }

        Section("Brain") {
            Text(
                "The on-device model that powers Otto's replies. Switch any time — Otto reloads " +
                    "his brain on the spot.",
                color = MutedText, fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val loading = pipeline.responder.state == ModelState.LOADING
            val variants = buildList {
                add(ResponderVariant.E2B)
                add(ResponderVariant.E4B)   // always listed — downloadable in place if absent
                if (e8bAvailable) add(ResponderVariant.E8B)
            }
            variants.forEachIndexed { idx, variant ->
                val present = variant != ResponderVariant.E4B || e4bAvailable
                ModelCard(
                    variant = variant,
                    selected = activeVariant == variant,
                    enabled = !loading && present,
                    onClick = {
                        if (!loading && present && activeVariant != variant) onSwitchResponder(variant)
                    },
                )
                if (variant == ResponderVariant.E4B && !e4bAvailable) {
                    E4BDownloadBlock(state = e4bDownloadState, onDownload = onDownloadE4B)
                }
                if (idx < variants.size - 1) Spacer(Modifier.height(8.dp))
            }
            if (loading) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Loading… give Otto a sec to wake up.",
                    color = Color(0xFFffb070), fontSize = 12.sp,
                )
            }
            pipeline.lowMemoryWarning?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFFffb070), fontSize = 12.sp)
            }
            if (pipeline.responder.state == ModelState.ERROR && pipeline.responder.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(pipeline.responder.error, color = Color(0xFFff7070), fontSize = 11.sp)
            }
        }

        Section("Web augmentation") {
            ToggleRow(
                label = "Allow web search & page fetch",
                checked = webAugmentation,
                enabled = true,
                onCheckedChange = onWebAugmentationChange,
            )
            Text(
                "When off, Otto answers from his on-device knowledge only — zero network.",
                color = MutedText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }

        Section("Thinking mode") {
            ToggleRow(
                label = "Let Otto think first",
                checked = thinking,
                enabled = true,
                onCheckedChange = onThinkingChange,
            )
            Text(
                "Otto reasons step-by-step before answering. Stronger on hard questions, " +
                    "noticeably slower. You'll see his reasoning live above each reply.",
                color = MutedText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }

        Section("Conversation") {
            TextButton(onClick = onRefreshConversation) {
                Text("Refresh conversation", color = Color(0xFFff7070))
            }
            Text(
                "Wipes the chat and Otto's memory of it. Nothing here ever leaves your phone.",
                color = MutedText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }

        Section("Support Otto") {
            Text(
                "Otto is free and always will be — no ads, no tracking, no subscription. If he's " +
                    "useful to you, a tip helps keep him swimming. Tap a link to open it, or an " +
                    "address to copy it.",
                color = MutedText, fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            LinkDonateRow("Buy Me a Coffee ☕", "buymeacoffee.com/aSchellCompany", BMC_URL)
            Spacer(Modifier.height(8.dp))
            DonateRow("Cash App", CASHAPP_TAG)
            Spacer(Modifier.height(8.dp))
            DonateRow("Monero (XMR)", MONERO_ADDRESS)
            Spacer(Modifier.height(8.dp))
            DonateRow("Bitcoin Lightning", LIGHTNING_INVOICE)
            Spacer(Modifier.height(8.dp))
            DonateRow("Bitcoin (on-chain)", BITCOIN_ADDRESS)
        }

        Section("About") {
            Text("Small Talk v2.0", color = Color.White, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Fully on-device. Built on Gemma via Google AI Edge LiteRT-LM (Apache 2.0). " +
                    "No accounts, no telemetry. Otto says hi.",
                color = MutedText, fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

// Public-by-design donation handles. Safe to ship in the clear.
private const val BMC_URL = "https://buymeacoffee.com/aSchellCompany"
private const val CASHAPP_TAG = "\$Aircityryan"
private const val MONERO_ADDRESS =
    "4B3RLHnNS6tNeHEneTXcecTAntHknXzbLYR1yBP3yUWS9baUjdnHv4UdhjRubaSexuPGEGmJ4QKpxHdrHNjLMuZpHf15gUt"
private const val LIGHTNING_INVOICE =
    "lnbc1p4p8zqkdqdgdshx6pqg9c8qpp5une0x62xnlx9wemg6pxz20gdtjjq280hs0d3ll5sauwuttd0nh7qsp5t43pu7vwt5fguvjr2a0ap24tppzw9vd8nc63levax96exma20s0s9qrsgqcqzp2xqy8ayqrzjqv06k0m23t593pngl0jt7n9wznp64fqngvctz7vts8nq4tukvtljqzxm45qqvtgqqvqqqqqqqqqqqqqqxqrzjqfzhphca8jlc5zznw52mnqxsnymltjgg3lxe4ul82g42vw0jpkgkwzg65sqq2ggqqyqqqqqqqqqqqqqqxqu2ma9pqrjyu0c8skg4txhu2rgtv798hpk7hrsrg2q53mzh9y0e2959evu4xlln45sw06dqcf6mavxmellq2mncttzfds4jzrgqlj7wgp9aqnlj"
private const val BITCOIN_ADDRESS = "bc1q4q0u5f7ya3ylwg3h4sdq5yw7cgfpl4ghpu9uap"

@Composable
private fun DonateRow(label: String, address: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                clipboard.setText(AnnotatedString(address))
                Toast.makeText(context, "$label address copied", Toast.LENGTH_SHORT).show()
            }
            .background(Color(0xFF1f1f37), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f))
            Text("Copy", color = AccentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            // Truncate the middle of long addresses so the row stays tidy; full value is copied.
            if (address.length > 26) "${address.take(14)}…${address.takeLast(10)}" else address,
            color = MutedText,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** A donation row that opens a link instead of copying an address. */
@Composable
private fun LinkDonateRow(label: String, display: String, url: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            .background(Color(0xFF1f1f37), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f))
            Text("Open ↗", color = AccentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Text(display, color = MutedText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

/** Inline download UI shown under the E4B card while that brain isn't on the device yet. */
@Composable
private fun E4BDownloadBlock(state: ModelDownloader.State, onDownload: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp, bottom = 2.dp)) {
        when (state) {
            is ModelDownloader.State.Downloading -> {
                val pct = (state.pct * 100).toInt()
                Text("Downloading the E4B brain… $pct%", color = MutedText, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { state.pct },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = AccentColor,
                    trackColor = Color(0xFF333355),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "You can leave this screen — I'll keep downloading.",
                    color = MutedText, fontSize = 11.sp,
                )
            }
            is ModelDownloader.State.Failed -> {
                Text(
                    "Download didn't finish (${state.reason}).",
                    color = Color(0xFFff7070), fontSize = 11.sp,
                )
                TextButton(onClick = onDownload, contentPadding = PaddingValues(0.dp)) {
                    Text("Try again", color = AccentColor, fontSize = 12.sp)
                }
            }
            else -> {
                TextButton(onClick = onDownload, contentPadding = PaddingValues(0.dp)) {
                    Text("⬇  Download the E4B brain (~4 GB)", color = AccentColor, fontSize = 13.sp)
                }
                Text(
                    "Wi-Fi strongly recommended. This brain wants a phone with plenty of RAM — " +
                        "if there's a memory warning above, E2B is the better home.",
                    color = MutedText, fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AssistantBubbleColor, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/**
 * Model selection card. Each variant gets one — Otto in that variant's color is drawn on the
 * left, the variant's name + description on the right, and a colored border + dot when selected.
 */
@Composable
private fun ModelCard(
    variant: ResponderVariant,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val skin = OttoSkin.forVariant(variant)
    val borderColor = if (selected) skin.body else Color(0xFF333355)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) skin.body.copy(alpha = 0.12f) else Color(0xFF1f1f37),
                RoundedCornerShape(10.dp),
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Otto(
            mood = if (selected) OttoMood.EXCITED else OttoMood.IDLE,
            skin = skin,
            pixel = 2.5.dp,
            modifier = Modifier.size(50.dp, 52.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    variant.label,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (selected) {
                    Spacer(Modifier.size(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(skin.body, RoundedCornerShape(50)),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("ACTIVE", color = skin.body, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                variant.description,
                color = MutedText,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = if (enabled) Color.White else MutedText,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentColor,
            ),
        )
    }
}
