package com.ryan.smalltalk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                if (e4bAvailable) add(ResponderVariant.E4B)
                if (e8bAvailable) add(ResponderVariant.E8B)
            }
            variants.forEachIndexed { idx, variant ->
                ModelCard(
                    variant = variant,
                    selected = activeVariant == variant,
                    enabled = !loading,
                    onClick = {
                        if (!loading && activeVariant != variant) onSwitchResponder(variant)
                    },
                )
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
