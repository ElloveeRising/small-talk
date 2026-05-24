package com.ryan.smalltalk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryan.smalltalk.llm.ModelState
import com.ryan.smalltalk.llm.ModelStatus
import com.ryan.smalltalk.ui.AccentColor
import com.ryan.smalltalk.ui.BackgroundColor
import com.ryan.smalltalk.ui.ChatScreen
import com.ryan.smalltalk.ui.ChatViewModel
import com.ryan.smalltalk.ui.MutedText
import com.ryan.smalltalk.ui.OctopusLoadingIndicator
import com.ryan.smalltalk.ui.Screen
import com.ryan.smalltalk.ui.SettingsScreen
import com.ryan.smalltalk.ui.SetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge so Compose's imePadding modifier actually receives IME insets
        // when the on-screen keyboard opens. Without this, in landscape the input
        // field can end up below the keyboard with no way to see what you're typing.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { App() }
    }
}

@Composable
private fun App() {
    val vm: ChatViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    when {
        showSettings -> SettingsScreen(
            pipeline = state.pipeline,
            webAugmentation = state.webAugmentation,
            thinking = state.thinking,
            activeVariant = state.activeVariant,
            e4bAvailable = state.e4bAvailable,
            e8bAvailable = state.e8bAvailable,
            onWebAugmentationChange = vm::setWebAugmentation,
            onThinkingChange = vm::setThinking,
            onSwitchResponder = vm::switchResponder,
            onRefreshConversation = vm::refreshChat,
            onBack = { showSettings = false },
        )

        state.screen == Screen.SETUP -> SetupScreen(onReady = { vm.startModelLoading() })

        state.screen == Screen.LOADING -> LoadingScreen(
            responder = state.pipeline.responder,
            lowMemory = state.pipeline.lowMemoryWarning,
            skin = com.ryan.smalltalk.ui.OttoSkin.forVariant(state.activeVariant),
        )

        else -> ChatScreen(
            state = state,
            onSend = vm::sendMessage,
            onOpenSettings = { showSettings = true },
            onRefresh = vm::refreshChat,
            onDismissError = vm::dismissError,
            onToggleThinking = { vm.setThinking(!state.thinking) },
            snackbarHostState = snackbar,
        )
    }
}

@Composable
private fun LoadingScreen(
    responder: ModelStatus,
    lowMemory: String?,
    skin: com.ryan.smalltalk.ui.OttoSkin,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        com.ryan.smalltalk.ui.AppLogo(fontSize = 28)
        Spacer(Modifier.height(20.dp))
        OctopusLoadingIndicator(skin = skin)
        Spacer(Modifier.height(16.dp))
        Text("Waking Otto up…", color = MutedText, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        ModelLoadRow("Otto's brain", responder)
        lowMemory?.let {
            Spacer(Modifier.height(24.dp))
            Text(it, color = Color(0xFFffb070), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ModelLoadRow(name: String, status: ModelStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (status.state) {
            ModelState.LOADING ->
                CircularProgressIndicator(color = AccentColor, strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
            ModelState.READY -> Text("✓", color = Color(0xFF6ad97c), fontSize = 18.sp)
            ModelState.ERROR -> Text("✕", color = Color(0xFFff7070), fontSize = 18.sp)
            else -> Text("•", color = MutedText, fontSize = 18.sp)
        }
        Text(
            "  $name",
            color = if (status.state == ModelState.ERROR) Color(0xFFff7070) else Color.White,
            fontSize = 15.sp,
        )
    }
    if (status.state == ModelState.ERROR && status.error != null) {
        Text(status.error, color = Color(0xFFff7070), fontSize = 11.sp)
    }
}
