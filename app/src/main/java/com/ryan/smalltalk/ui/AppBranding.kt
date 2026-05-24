package com.ryan.smalltalk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/**
 * The "Small Talk" wordmark — centered, monospace (typewriter feel), with a thin platen rule
 * underneath like a sheet rolled into a typewriter. Built from the device monospace face so it
 * needs no bundled font asset.
 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    fontSize: Int = 22,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Small Talk",
            color = AccentColor,
            fontSize = fontSize.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(5.dp))
        // Platen rule — short, centered, evokes paper in a typewriter carriage.
        Box(
            modifier = Modifier
                .width((fontSize * 5.2f).dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(AccentColor.copy(alpha = 0.45f))
        )
    }
}
