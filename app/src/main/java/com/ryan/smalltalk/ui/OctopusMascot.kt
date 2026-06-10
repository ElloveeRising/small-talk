package com.ryan.smalltalk.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ryan.smalltalk.llm.ResponderVariant

// ─────────────────────────────────────────────────────────────────────────────
// Otto — the Small Talk octopus mascot.
//
// 20 × 21 pixel grid (3 dp per pixel default = 60 × 63 dp).
//   rows  0–3   accessories (cap on THINKING, "Z" on SLEEPING, magnifier on SEARCHING)
//   rows  4–13  octopus head + face
//   rows 14–17  tentacles (animated per mood)
//   rows 18–19  keyboard (TYPING only)
//
// Six tentacles instead of the old four, positioned symmetrically. TYPING uses a
// phase-offset wave so each tentacle is in a slightly different point of its
// down-up cycle — looks like rapid finger movement, not synchronized clapping.
// ─────────────────────────────────────────────────────────────────────────────

enum class OttoMood {
    IDLE,
    TYPING,
    THINKING,
    SEARCHING,
    EXCITED,
    YAWN,
    SLEEPING,
    WAVING,
    READING,    // user is typing — Otto peeks down at the input
    WINKING,    // long-press easter egg
    CONFUSED,   // shown briefly when something goes wrong
    CRAWLING,   // walk cycle, used while Otto strolls across the strip
    JETTING,    // squeezed-body burst — octopus jet propulsion (recalls + ink moments)
    DANCING,    // rare idle act: a tiny groove with floating notes
    PAINTING,   // rare idle act: easel, brush, dabs of paint
}

/**
 * Tiny per-character "flavor" reaction used in [OttoMood.READING] so Otto looks like he's
 * actually responding to what's being typed. Computed from the input string in OttoStrip.
 */
enum class OttoReaction { NORMAL, CURIOUS, SURPRISED, FOCUSED }

fun reactionFor(text: String): OttoReaction {
    if (text.isBlank()) return OttoReaction.NORMAL
    val excitedShare = text.count { it == '!' || it.isUpperCase() }.toFloat() / text.length
    return when {
        text.length > 80 -> OttoReaction.FOCUSED
        excitedShare > 0.30f -> OttoReaction.SURPRISED
        text.contains('?') -> OttoReaction.CURIOUS
        else -> OttoReaction.NORMAL
    }
}

enum class OttoSkin(val body: Color, val shade: Color, val highlight: Color) {
    PURPLE(Color(0xFF8866EE), Color(0xFF5544AA), Color(0xFFB99AFF)),
    TEAL(Color(0xFF44BBCC), Color(0xFF227788), Color(0xFF88DDEE)),
    CORAL(Color(0xFFEE6677), Color(0xFFAA3344), Color(0xFFFF99AA));

    companion object {
        fun forVariant(v: ResponderVariant): OttoSkin = when (v) {
            ResponderVariant.E2B -> PURPLE
            ResponderVariant.E4B -> TEAL
            ResponderVariant.E8B -> CORAL
        }
    }
}

private val EyeWhite = Color(0xFFFFFFFF)
private val EyePupil = Color(0xFF110022)
private val MouthDark = Color(0xFF330033)

// Cap: bumped from #111122 (lost in the dark bg) to a navy that reads clearly
// against #1a1a2e. The gold band still pops on top.
private val CapBoard = Color(0xFF4a4a78)
private val CapBoardEdge = Color(0xFF2e2e48)
private val CapBand = Color(0xFFFFD24F)
private val Tassel = Color(0xFFFFD24F)

private val KbA = Color(0xFF334455)
private val KbB = Color(0xFF223344)
private val KbPress = Color(0xFF77AADD)
private val ScreenBg = Color(0xFF0E1D33)
private val ScreenText = Color(0xFF7FD9A8)
private val JetBubble = Color(0xFFB8D4E8)
private val NightcapBlue = Color(0xFF4a4a78)
private val CanvasWhite = Color(0xFFEDEAF5)

private val Cheek = Color(0xFFFF99CC)
private val ZColor = Color(0xFFCCCCEE)
private val GlassRim = Color(0xFFEEEEFF)
private val GlassShine = Color(0xFFFFFFFF)
private val GlassHandle = Color(0xFF884422)

// Six tentacle columns, symmetric about col 9-10.
private val TENTACLE_COLS = listOf(1..2, 4..5, 7..8, 11..12, 14..15, 17..18)

// Six-frame typing pattern. Each frame says which tentacle indices are PRESSED
// DOWN (extended to row 17 onto the keyboard); the rest are LIFTED (visible only
// near row 14, like fingers hovering between strokes). Patterns chosen so:
//   - exactly 2 tentacles strike per frame (paired keystroke feel)
//   - every tentacle strikes twice across the 6-frame cycle
//   - no two consecutive frames share the same pair (no double-tap on one finger)
//   - the strikes don't sweep left-to-right (avoids the "cascade" look)
private val TYPING_FRAME_PRESSES: Array<IntArray> = arrayOf(
    intArrayOf(0, 3),  // outer-left + center-right
    intArrayOf(4, 1),  // right-mid + inner-left
    intArrayOf(2, 5),  // center-left + outer-right
    intArrayOf(0, 4),  // outer-left + right-mid
    intArrayOf(3, 1),  // center-right + inner-left
    intArrayOf(5, 2),  // outer-right + center-left
)

@Composable
fun Otto(
    mood: OttoMood,
    skin: OttoSkin = OttoSkin.PURPLE,
    pixel: Dp = 3.dp,
    reaction: OttoReaction = OttoReaction.NORMAL,
    wearingCap: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "otto-$mood")

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mood) {
                    OttoMood.IDLE -> 2400
                    OttoMood.TYPING -> 720    // 6 frames * 120 ms each
                    OttoMood.THINKING -> 1600
                    OttoMood.SEARCHING -> 1200
                    OttoMood.EXCITED -> 260
                    OttoMood.YAWN -> 1400
                    OttoMood.SLEEPING -> 2800
                    OttoMood.WAVING -> 500
                    OttoMood.READING -> 900   // scanning eyes side-to-side
                    OttoMood.WINKING -> 1200
                    OttoMood.CONFUSED -> 700
                    OttoMood.CRAWLING -> 360  // brisk walk cycle
                    OttoMood.JETTING -> 240   // rapid squeeze pulses while jetting
                    OttoMood.DANCING -> 520
                    OttoMood.PAINTING -> 1700 // slow, contemplative brushwork
                },
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    val blinkPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink",
    )

    // Slow channels for ambient life on IDLE — eye-roam (where Otto's looking)
    // and breathe (a 1-px body bob). Independent of the per-mood phase so they
    // keep going regardless of what Otto's doing.
    val roamPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "roam",
    )
    val breathePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    val frame = when (mood) {
        OttoMood.IDLE -> ((phase * 4f).toInt()).coerceIn(0, 3)    // 4-pose tentacle curl ripple
        OttoMood.TYPING -> ((phase * 6f).toInt()).coerceIn(0, 5)  // 6-frame drumming pattern
        OttoMood.THINKING -> ((phase * 4f).toInt()).coerceIn(0, 3)
        OttoMood.SEARCHING -> ((phase * 4f).toInt()).coerceIn(0, 3)
        OttoMood.EXCITED -> if (phase < 0.5f) 0 else 1
        OttoMood.YAWN -> if (phase < 0.5f) 0 else 1
        OttoMood.SLEEPING -> ((phase * 3f).toInt()).coerceIn(0, 2)
        OttoMood.WAVING -> if (phase < 0.5f) 0 else 1
        OttoMood.READING -> ((phase * 4f).toInt()).coerceIn(0, 3)
        OttoMood.WINKING -> if (phase < 0.85f) 0 else 1  // wink held most of cycle, brief snap-back
        OttoMood.CONFUSED -> ((phase * 2f).toInt()).coerceIn(0, 1)
        OttoMood.CRAWLING -> if (phase < 0.5f) 0 else 1   // two-step walk gait
        OttoMood.JETTING -> if (phase < 0.5f) 0 else 1
        OttoMood.DANCING -> if (phase < 0.5f) 0 else 1
        OttoMood.PAINTING -> ((phase * 4f).toInt()).coerceIn(0, 3)
    }
    val blinking = blinkPhase > 0.94f

    // Eyes are forced-closed only when SLEEPING (override blink). YAWN squints.
    // READING-FOCUSED used to squint too, which made Otto look frozen / asleep while
    // "reading carefully" — now it keeps open, downcast, scanning eyes (handled in the
    // READING branch) so he stays alive.
    val eyesClosed = mood == OttoMood.SLEEPING
    val eyesSquinted = mood == OttoMood.YAWN

    // Breathe: a 1-pixel vertical body bob on IDLE only. Half the time at 0, half
    // at +1. Subtle but adds a "Otto is alive" feel without distracting from
    // active moods.
    val bobOffset = if (mood == OttoMood.IDLE && breathePhase > 0.5f) 1 else 0

    Canvas(modifier = modifier.size(pixel * 20, pixel * 21)) {
        val p = pixel.toPx()
        fun px(x: Int, y: Int, c: Color) =
            drawRect(color = c, topLeft = Offset(x * p, (y + bobOffset) * p), size = Size(p, p))
        fun row(xs: IntRange, y: Int, c: Color) = xs.forEach { px(it, y, c) }

        // JETTING is a whole-body transformation (squeezed mantle, arms streaming behind),
        // so it draws its own everything and skips the normal head/face path.
        if (mood == OttoMood.JETTING) {
            drawJetting(::px, ::row, skin, frame)
            return@Canvas
        }

        // ── Accessory rows 0-3 ────────────────────────────────────────────────
        // Priority: an action that needs the top rows (searching = magnifier,
        // sleeping = Zs) wins; otherwise the thinking cap shows whenever thinking
        // mode is on — including while Otto reads your input or sits idle, so it no
        // longer pops off the moment you start typing.
        when {
            mood == OttoMood.SEARCHING -> drawMagnifyingGlass(::px, ::row, skin, frame)
            mood == OttoMood.SLEEPING -> {
                drawSleepingZs(::px, frame)
                // After dark he sleeps in a proper nightcap (cap left, Zs float right).
                if (isNightHour()) drawNightcap(::px, ::row)
            }
            mood == OttoMood.CONFUSED -> drawQuestionMark(::px)
            mood == OttoMood.DANCING -> drawMusicNotes(::px, frame)
            wearingCap -> drawCap(::px, ::row)
            else -> {}
        }

        // ── Head dome (rows 4–7) ─────────────────────────────────────────────
        row(6..13, 4, skin.body)
        row(4..15, 5, skin.body)
        row(2..17, 6, skin.body)
        row(1..18, 7, skin.body)

        // ── Face block (rows 8–11) and bottom curve (rows 12-13) ─────────────
        row(0..19, 8, skin.body)
        row(0..19, 9, skin.body)
        row(0..19, 10, skin.body)
        row(0..19, 11, skin.body)
        row(1..18, 12, skin.body)
        row(2..17, 13, skin.body)

        // Side shading hint
        px(0, 8, skin.shade); px(19, 8, skin.shade)
        px(0, 10, skin.shade); px(19, 10, skin.shade)

        // Top-of-head highlight
        px(7, 4, skin.highlight); px(8, 4, skin.highlight)
        px(5, 5, skin.highlight); px(6, 5, skin.highlight)

        // Cheek blushes (suppressed on SLEEPING & YAWN — looks weird with closed eyes)
        if (mood != OttoMood.SLEEPING && mood != OttoMood.YAWN) {
            px(1, 10, Cheek); px(18, 10, Cheek)
        }

        // ── Eyes (rows 8-10) ──────────────────────────────────────────────────
        when {
            eyesClosed -> {
                // Soft closed eyes — short curve drawn as two pixels each
                px(3, 9, EyePupil); px(4, 9, EyePupil); px(5, 10, EyePupil); px(6, 10, EyePupil)
                px(13, 10, EyePupil); px(14, 10, EyePupil); px(15, 9, EyePupil); px(16, 9, EyePupil)
            }
            eyesSquinted -> {
                // Squinted eyes — just a dash each
                row(3..6, 10, EyePupil)
                row(13..16, 10, EyePupil)
            }
            blinking -> {
                row(3..6, 9, EyePupil)
                row(13..16, 9, EyePupil)
            }
            mood == OttoMood.CONFUSED -> {
                // Misaligned eyes — one up, one down — the universal "huh??" look.
                for (y in 8..10) {
                    row(3..6, y, EyeWhite)
                    row(13..16, y, EyeWhite)
                }
                // Left pupil HIGH, right pupil LOW
                px(4, 8, EyePupil); px(5, 8, EyePupil)
                px(4, 9, EyePupil); px(5, 9, EyePupil)
                px(14, 10, EyePupil); px(15, 10, EyePupil)
                px(14, 9, EyePupil); px(15, 9, EyePupil)
            }
            mood == OttoMood.WINKING && frame == 0 -> {
                // Left eye open normally
                for (y in 8..10) row(3..6, y, EyeWhite)
                for (y in 9..10) { px(4, y, EyePupil); px(5, y, EyePupil) }
                px(4, 9, EyeWhite)  // eye shine
                // Right eye CLOSED — same curve we use for SLEEPING
                px(13, 10, EyePupil); px(14, 10, EyePupil)
                px(15, 9, EyePupil); px(16, 9, EyePupil)
            }
            mood == OttoMood.READING && reaction == OttoReaction.SURPRISED -> {
                // Wide-open eyes — whole eye block is pupil, with a small shine
                for (y in 8..10) {
                    row(3..6, y, EyePupil)
                    row(13..16, y, EyePupil)
                }
                px(4, 8, EyeWhite); px(14, 8, EyeWhite)
            }
            mood == OttoMood.READING -> {
                // Eyes locked DOWN onto the input below. We do TWO things to make
                // the gaze unmistakable at small sizes:
                //   1. Eye whites stay at rows 8-10 (head shape doesn't change), but
                //      pupils sit FLATTENED against the bottom edge — row 10 only,
                //      2x1 each. Reads as "eyes pointed straight down".
                //   2. A faux-eyelid pixel above each eye (row 8) — like the eye
                //      has narrowed to focus downward.
                for (y in 8..10) {
                    row(3..6, y, EyeWhite)
                    row(13..16, y, EyeWhite)
                }
                // Eyelid hints
                row(3..6, 8, skin.shade)
                row(13..16, 8, skin.shade)
                // Scanning shift (left-right "reading" motion)
                val scanShift = when (frame) { 0 -> 0; 1 -> 1; 2 -> 1; 3 -> 0; else -> 0 }
                val (basePupilL, basePupilR) = when (reaction) {
                    OttoReaction.CURIOUS -> 3 to 14   // slight head-tilt feel
                    else -> 4 to 14
                }
                val pupilL = (basePupilL + scanShift).coerceIn(3, 5)
                val pupilR = (basePupilR + scanShift).coerceIn(13, 15)
                // Pupils at row 10 only — flattened, definitively downcast
                px(pupilL, 10, EyePupil); px(pupilL + 1, 10, EyePupil)
                px(pupilR, 10, EyePupil); px(pupilR + 1, 10, EyePupil)
            }
            else -> {
                for (y in 8..10) {
                    row(3..6, y, EyeWhite)
                    row(13..16, y, EyeWhite)
                }
                // On IDLE only, Otto roams his eyes around so he doesn't look like a
                // mannequin. roamPhase cycles every 7.2 s; we step through 5 positions
                // (left, center, right, center, up-glance) with a long hold at center.
                val (pupilL, pupilR) = when (mood) {
                    OttoMood.THINKING -> 5 to 15
                    OttoMood.SEARCHING -> 5 to 15      // looking up-right toward glass
                    OttoMood.EXCITED, OttoMood.WAVING -> 4 to 14
                    OttoMood.TYPING -> 3 to 13         // eyes on his little terminal screen
                    OttoMood.PAINTING -> 3 to 13       // eyes on the canvas
                    OttoMood.DANCING -> if (frame == 0) 3 to 13 else 5 to 15
                    OttoMood.IDLE -> {
                        when ((roamPhase * 5f).toInt().coerceIn(0, 4)) {
                            0 -> 3 to 13   // glance left
                            1 -> 4 to 14   // center
                            2 -> 5 to 15   // glance right
                            3 -> 4 to 14   // center
                            else -> 4 to 14
                        }
                    }
                    else -> 4 to 14
                }
                for (y in 9..10) {
                    px(pupilL, y, EyePupil); px(pupilL + 1, y, EyePupil)
                    px(pupilR, y, EyePupil); px(pupilR + 1, y, EyePupil)
                }
                // Eye shine — top-left of each pupil
                px(pupilL, 9, EyeWhite); px(pupilR, 9, EyeWhite)
            }
        }

        // ── Mouth ────────────────────────────────────────────────────────────
        when (mood) {
            OttoMood.TYPING, OttoMood.EXCITED -> {
                // Open "o"
                px(9, 12, MouthDark); px(10, 12, MouthDark)
                px(9, 13, MouthDark); px(10, 13, MouthDark)
            }
            OttoMood.THINKING, OttoMood.SEARCHING -> {
                // Concentrated little line
                row(8..11, 12, MouthDark)
            }
            OttoMood.YAWN -> {
                // BIG open mouth
                row(7..12, 12, MouthDark)
                row(7..12, 13, MouthDark)
                row(8..11, 14, MouthDark)   // mouth extends slightly down
            }
            OttoMood.SLEEPING -> {
                // Tiny line, peacefully closed
                px(9, 12, MouthDark); px(10, 12, MouthDark)
            }
            OttoMood.WAVING, OttoMood.WINKING -> {
                // Big grin
                px(7, 12, MouthDark); px(12, 12, MouthDark)
                row(8..11, 13, MouthDark)
            }
            OttoMood.READING -> {
                when (reaction) {
                    OttoReaction.CURIOUS -> {
                        // Small "o" — curious
                        px(9, 13, MouthDark); px(10, 13, MouthDark)
                    }
                    OttoReaction.SURPRISED -> {
                        // Open mouth — "oh!"
                        px(9, 12, MouthDark); px(10, 12, MouthDark)
                        px(9, 13, MouthDark); px(10, 13, MouthDark)
                    }
                    OttoReaction.FOCUSED -> {
                        // Pursed concentration line
                        row(8..11, 13, MouthDark)
                    }
                    OttoReaction.NORMAL -> {
                        // Subtle smile, same as IDLE
                        px(8, 12, MouthDark); px(11, 12, MouthDark)
                        px(9, 13, MouthDark); px(10, 13, MouthDark)
                    }
                }
            }
            OttoMood.CONFUSED -> {
                // Small off-centre frown — corners down, slightly lopsided
                px(8, 13, MouthDark); px(9, 12, MouthDark)
                px(10, 12, MouthDark); px(11, 13, MouthDark)
            }
            OttoMood.CRAWLING -> {
                // Determined little smile
                px(8, 12, MouthDark); px(11, 12, MouthDark)
                px(9, 13, MouthDark); px(10, 13, MouthDark)
            }
            OttoMood.IDLE -> {
                // Subtle smile — corners up
                px(8, 12, MouthDark); px(11, 12, MouthDark)
                px(9, 13, MouthDark); px(10, 13, MouthDark)
            }
            OttoMood.DANCING -> {
                // Full grin — he's having a moment
                px(7, 12, MouthDark); px(12, 12, MouthDark)
                row(8..11, 13, MouthDark)
            }
            OttoMood.PAINTING -> {
                // Pursed artist's concentration
                row(8..11, 12, MouthDark)
            }
            OttoMood.JETTING -> { /* unreachable — JETTING draws its own body */ }
        }

        // ── Tentacles (rows 14-17, sometimes higher) ─────────────────────────
        drawTentacles(::px, mood, skin, frame, phase)

        // ── Battlestation (TYPING only): terminal on the left, keys below ───
        if (mood == OttoMood.TYPING) {
            // Upright terminal — drawn after the body so Otto sits "behind the desk".
            for (y in 11..16) for (x in 0..5) px(x, y, KbB)
            for (y in 12..15) for (x in 1..4) px(x, y, ScreenBg)
            // Lines of text accumulate on screen as he types, then scroll away.
            val lines = frame % 6
            if (lines >= 1) { px(1, 12, ScreenText); px(2, 12, ScreenText) }
            if (lines >= 2) { px(1, 13, ScreenText); px(2, 13, ScreenText); px(3, 13, ScreenText) }
            if (lines >= 3) { px(1, 14, ScreenText) }
            if (lines >= 4) { px(2, 14, ScreenText); px(3, 14, ScreenText) }
            if (lines >= 5) { px(1, 15, ScreenText); px(2, 15, ScreenText) }
            if (frame % 2 == 0) px(4, 15, ScreenText)   // blinking cursor
            // Keyboard under his left arms only — the right arms get the night off.
            (0..11).forEach { x -> px(x, 18, if (x % 2 == 0) KbA else KbB) }
            row(0..11, 19, KbB)
            TENTACLE_COLS.forEachIndexed { idx, cols ->
                if (idx <= 2 && isTentaclePressed(idx, frame)) {
                    cols.forEach { x -> if (x <= 11) px(x, 18, KbPress) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Accessory drawers
// ─────────────────────────────────────────────────────────────────────────────

private fun drawCap(
    px: (Int, Int, Color) -> Unit,
    row: (IntRange, Int, Color) -> Unit,
) {
    // Outer edge (slightly darker) for definition against the bg
    row(3..16, 0, CapBoardEdge)
    row(2..17, 1, CapBoardEdge)
    // Cap board (lighter inside)
    row(4..15, 0, CapBoard)
    row(3..16, 1, CapBoard)
    // Gold band
    row(5..14, 2, CapBand)
    // Little inside-band detail pixel
    px(9, 2, Color(0xFFAA8822))
    // Tassel
    px(17, 1, Tassel); px(17, 2, Tassel); px(18, 3, Tassel)
}

private fun drawQuestionMark(px: (Int, Int, Color) -> Unit) {
    // A "?" floating off Otto's top-right, in the attention-grabbing cap gold.
    // Sits in cols 16-19 / rows 0-5 — clear of the head silhouette.
    val c = CapBand
    px(16, 0, c); px(17, 0, c); px(18, 0, c)  // top arc
    px(19, 1, c)                              // right shoulder
    px(18, 2, c)                              // curving back in
    px(17, 3, c)                              // stem
    px(17, 5, c)                              // dot (gap at row 4)
}

/** True between 9 pm and 6 am — Otto's nightcap hours. */
private fun isNightHour(): Boolean {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return h >= 21 || h < 6
}

/** Floppy nightcap worn while SLEEPING at night. Sits left of the Zs (cols ≤ 12). */
private fun drawNightcap(
    px: (Int, Int, Color) -> Unit,
    row: (IntRange, Int, Color) -> Unit,
) {
    row(5..12, 3, ZColor)          // pale folded brim on the dome
    row(5..11, 2, NightcapBlue)    // cone
    row(4..8, 1, NightcapBlue)     // flopping left
    px(3, 0, NightcapBlue); px(4, 0, NightcapBlue)
    px(2, 0, ZColor)               // pom on the tip
}

/** Floating notes for the DANCING easter egg — alternating positions per frame. */
private fun drawMusicNotes(px: (Int, Int, Color) -> Unit, frame: Int) {
    val c = CapBand
    if (frame == 0) {
        px(15, 2, c); px(15, 1, c); px(15, 0, c); px(14, 2, c)   // eighth note, right
        px(3, 1, c); px(3, 0, c); px(2, 1, c)                    // small note, left
    } else {
        px(17, 1, c); px(17, 0, c); px(16, 1, c)
        px(5, 2, c); px(5, 1, c); px(5, 0, c); px(4, 2, c)
    }
}

/** Easel + canvas for the PAINTING easter egg. Dabs accumulate with the frame. */
private fun drawEasel(px: (Int, Int, Color) -> Unit, frame: Int) {
    for (y in 14..17) { px(0, y, GlassHandle); px(4, y, GlassHandle) }   // legs
    for (x in 0..4) px(x, 8, GlassHandle)                                // top rail
    for (y in 9..13) for (x in 0..4) px(x, y, CanvasWhite)               // canvas
    if (frame >= 0) px(1, 12, Cheek)
    if (frame >= 1) px(3, 10, CapBand)
    if (frame >= 2) px(2, 11, Color(0xFF44BBCC))
    if (frame >= 3) px(1, 10, Color(0xFF8866EE))
}

/**
 * JETTING — full-body redraw. Otto squeezes his mantle and rockets head-first
 * (downward) with all eight... fine, six arms streaming behind him, bubbles
 * ripping past. The squeeze pulses with the frame like a real siphon burst.
 */
private fun drawJetting(
    px: (Int, Int, Color) -> Unit,
    row: (IntRange, Int, Color) -> Unit,
    skin: OttoSkin,
    frame: Int,
) {
    val s = if (frame == 0) 0 else 1   // 1 = tighter squeeze
    // Trailing arm bundle (above — he's headed down)
    row(9..10, 0, skin.shade)
    row(9..10, 1, skin.shade)
    row(8..11, 2, skin.shade)
    row(8..11, 3, skin.shade)
    // Squeezed mantle
    row(8 + s..11 - s, 4, skin.body)
    row(7 + s..12 - s, 5, skin.body)
    row(6 + s..13 - s, 6, skin.body)
    for (y in 7..11) row(5 + s..14 - s, y, skin.body)
    row(6 + s..13 - s, 12, skin.body)
    row(7 + s..12 - s, 13, skin.body)
    // Determined little eyes
    px(7, 9, EyeWhite); px(8, 9, EyePupil)
    px(12, 9, EyeWhite); px(11, 9, EyePupil)
    // Bubbles streaming past
    if (frame == 0) { px(4, 2, JetBubble); px(15, 5, JetBubble); px(3, 8, JetBubble) }
    else { px(15, 1, JetBubble); px(4, 6, JetBubble); px(16, 9, JetBubble) }
}

private fun drawSleepingZs(
    px: (Int, Int, Color) -> Unit,
    frame: Int,
) {
    // Three Z-shape sprites floating up over time. `frame` picks which one is brightest.
    // Small Z = 3 pixels in an L-ish shape.
    if (frame >= 0) {
        px(13, 3, ZColor); px(14, 3, ZColor); px(14, 2, ZColor); px(15, 2, ZColor)
    }
    if (frame >= 1) {
        px(16, 1, ZColor); px(17, 1, ZColor); px(17, 0, ZColor); px(18, 0, ZColor)
    }
}

private fun drawMagnifyingGlass(
    px: (Int, Int, Color) -> Unit,
    row: (IntRange, Int, Color) -> Unit,
    skin: OttoSkin,
    frame: Int,
) {
    // Magnifying glass sits above Otto's right shoulder. The "lens" is a 5x5 ring;
    // the handle drops diagonally toward the right tentacle (which curls up to "hold" it).
    // Animation: lens drifts one column left/right per frame to feel like scanning.
    val drift = when (frame % 4) { 0 -> 0; 1 -> -1; 2 -> 0; 3 -> 1; else -> 0 }
    val left = 11 + drift  // lens left edge
    // Lens ring
    row(left + 1..left + 3, 0, GlassRim)
    px(left, 1, GlassRim); px(left + 4, 1, GlassRim)
    px(left, 2, GlassRim); px(left + 4, 2, GlassRim)
    px(left, 3, GlassRim); px(left + 4, 3, GlassRim)
    row(left + 1..left + 3, 4, GlassRim)
    // Shine on top-left of lens
    px(left + 1, 1, GlassShine)
    // Handle — diagonal from bottom-right of lens to tentacle 6 area
    px(left + 4, 4, GlassHandle)
    px(left + 5, 5, GlassHandle)
    px(left + 6, 6, GlassHandle)
}

// ─────────────────────────────────────────────────────────────────────────────
// Tentacle rendering — different per-mood
// ─────────────────────────────────────────────────────────────────────────────

// Whether tentacle [idx] is currently "pressed down" onto the keyboard in the
// 6-frame drumming pattern. Used both by tentacle drawing and by the keyboard-press
// highlight so the two stay in sync.
private fun isTentaclePressed(idx: Int, frame: Int): Boolean {
    val pair = TYPING_FRAME_PRESSES.getOrNull(frame) ?: return false
    return pair[0] == idx || pair[1] == idx
}

private fun drawTentacles(
    px: (Int, Int, Color) -> Unit,
    mood: OttoMood,
    skin: OttoSkin,
    frame: Int,
    phase: Float,
) {
    when (mood) {
        OttoMood.TYPING -> {
            // Side-on workstation pose: the LEFT three arms drum the keys (one strike
            // per frame, rotating); the right three rest in a relaxed curl. Pairs with
            // the terminal drawn on the left of the canvas.
            TENTACLE_COLS.forEachIndexed { idx, cols ->
                if (idx <= 2) {
                    if (isTentaclePressed(idx, frame)) {
                        for (y in 14..17) cols.forEach { x -> px(x, y, skin.shade) }
                        val splayLeft = cols.first - 1
                        val splayRight = cols.last + 1
                        if (splayLeft >= 0) px(splayLeft, 17, skin.shade)
                        if (splayRight <= 19) px(splayRight, 17, skin.shade)
                    } else {
                        for (y in 14..15) cols.forEach { x -> px(x, y, skin.shade) }
                    }
                } else {
                    for (y in 14..16) cols.forEach { x -> px(x, y, skin.shade) }
                    px((cols.last + 1).coerceAtMost(19), 16, skin.shade)   // lazy off-hand curl
                }
            }
        }
        OttoMood.THINKING -> {
            // 5 tentacles straight down, 1 curled up to chin
            TENTACLE_COLS.forEachIndexed { idx, cols ->
                if (idx == 1) {
                    // Curl up the side of the head
                    cols.forEach { x -> px(x, 14, skin.shade) }
                    px(6, 13, skin.shade); px(6, 12, skin.shade)
                    px(7, 12, skin.shade)
                } else {
                    for (y in 14..17) cols.forEach { x -> px(x, y, skin.shade) }
                }
            }
        }
        OttoMood.SEARCHING -> {
            // Left 5 tentacles relaxed, right-most tentacle curls UP to hold the magnifier
            TENTACLE_COLS.forEachIndexed { idx, cols ->
                if (idx == 5) {
                    // Curl up and out to meet the handle
                    cols.forEach { x -> px(x, 14, skin.shade) }
                    px(17, 13, skin.shade); px(17, 12, skin.shade)
                    px(17, 11, skin.shade); px(17, 10, skin.shade)
                    px(17, 9, skin.shade); px(17, 8, skin.shade)
                    px(17, 7, skin.shade)
                } else {
                    for (y in 14..17) cols.forEach { x -> px(x, y, skin.shade) }
                }
            }
        }
        OttoMood.EXCITED -> {
            // Short, waving arms — all up high
            TENTACLE_COLS.forEachIndexed { idx, cols ->
                for (y in 14..15) cols.forEach { x -> px(x, y, skin.shade) }
                val tipDir = if (frame == 0) -1 else 1
                val tipCol = (cols.first + 1 + tipDir).coerceIn(0, 19)
                px(tipCol, 16, skin.shade)
            }
        }
        OttoMood.YAWN -> {
            // Tentacles stretched up high — Otto's stretching as he yawns
            TENTACLE_COLS.forEachIndexed { idx, cols ->
                for (y in 14..15) cols.forEach { x -> px(x, y, skin.shade) }
                // The outer tentacles stretch a pixel higher
                if (idx == 0 || idx == 5) {
                    cols.forEach { x -> px(x, 14, skin.shade) }
                }
            }
        }
        OttoMood.SLEEPING -> {
            // Tentacles relaxed, slight gentle bob with frame
            val bob = if (frame == 1) 1 else 0
            TENTACLE_COLS.forEachIndexed { idx, cols ->
                for (y in 14..(16 + bob)) cols.forEach { x -> px(x, y, skin.shade) }
            }
        }
        OttoMood.WAVING -> {
            // Right-most tentacle waves high overhead
            TENTACLE_COLS.forEachIndexed { idx, cols ->
                if (idx == 5) {
                    // Reach UP, way above the head
                    cols.forEach { x -> px(x, 14, skin.shade) }
                    px(17, 13, skin.shade); px(17, 12, skin.shade)
                    px(17, 11, skin.shade); px(17, 10, skin.shade)
                    // Tip waves left/right
                    val tip = if (frame == 0) 18 else 16
                    px(tip, 9, skin.shade); px(tip, 8, skin.shade)
                } else {
                    for (y in 14..17) cols.forEach { x -> px(x, y, skin.shade) }
                }
            }
        }
        OttoMood.IDLE -> {
            // Curl ripple: each arm drifts through straight → tip-slide → hook-curl →
            // gather, staggered by index so the row of arms undulates instead of
            // stamping. Outer arms hook outward, like the resting pose of a real
            // octopus (and our banner).
            TENTACLE_COLS.forEachIndexed { idx, cols ->
                val a = cols.first
                val b = cols.last
                val hook = if (idx >= 3) b + 1 else a - 1
                when ((frame + idx) % 4) {
                    0 -> {   // straight, tip resting on its left foot
                        for (y in 14..16) cols.forEach { x -> px(x, y, skin.shade) }
                        px(a, 17, skin.shade)
                    }
                    1 -> {   // tip slides across
                        for (y in 14..16) cols.forEach { x -> px(x, y, skin.shade) }
                        px(b, 17, skin.shade)
                    }
                    2 -> {   // curl: tip hooks out and lifts
                        for (y in 14..16) cols.forEach { x -> px(x, y, skin.shade) }
                        if (hook in 0..19) { px(hook, 16, skin.shade); px(hook, 15, skin.shade) }
                    }
                    else -> { // gather: shorter, loading the next ripple
                        for (y in 14..15) cols.forEach { x -> px(x, y, skin.shade) }
                        px(if (idx >= 3) b else a, 16, skin.shade)
                    }
                }
            }
        }
        OttoMood.READING -> {
            // Five tentacles relaxed at full length. The right-most tentacle extends
            // DOWN PAST the canvas edge — clear visual that Otto is peering at the
            // text directly below him.
            TENTACLE_COLS.forEachIndexed { idx, cols ->
                if (idx == 5) {
                    // Down past the canvas — rows 14-20 (canvas is 21 tall, this hits the edge)
                    for (y in 14..20) cols.forEach { x -> px(x, y, skin.shade) }
                } else {
                    for (y in 14..17) cols.forEach { x -> px(x, y, skin.shade) }
                }
            }
        }
        OttoMood.WINKING -> {
            // Relaxed tentacles — one of them gives a tiny wave on the wink
            TENTACLE_COLS.forEachIndexed { idx, cols ->
                if (idx == 5) {
                    // Tiny wave overhead alongside the wink
                    cols.forEach { x -> px(x, 14, skin.shade) }
                    px(17, 13, skin.shade); px(17, 12, skin.shade)
                    px(17, 11, skin.shade)
                } else {
                    for (y in 14..17) cols.forEach { x -> px(x, y, skin.shade) }
                }
            }
        }
        OttoMood.CONFUSED -> {
            // Most tentacles hang; the right-most curls up to scratch the head.
            TENTACLE_COLS.forEachIndexed { idx, cols ->
                if (idx == 5) {
                    cols.forEach { x -> px(x, 14, skin.shade) }
                    px(16, 13, skin.shade); px(15, 12, skin.shade)
                    px(14, 11, skin.shade)   // tip touching the side of the head
                } else {
                    for (y in 14..17) cols.forEach { x -> px(x, y, skin.shade) }
                }
            }
        }
        OttoMood.CRAWLING -> {
            // Rolling gait: planted arms drag a heel pixel; lifted arms curl forward
            // gathering the next step — a crawl, not a march.
            TENTACLE_COLS.forEachIndexed { idx, cols ->
                val stepping = (idx % 2 == 0) == (frame == 0)
                val a = cols.first
                val b = cols.last
                if (stepping) {
                    for (y in 14..17) cols.forEach { x -> px(x, y, skin.shade) }
                    if (a - 1 >= 0) px(a - 1, 17, skin.shade)    // heel drag
                } else {
                    for (y in 14..15) cols.forEach { x -> px(x, y, skin.shade) }
                    if (b + 1 <= 19) px(b + 1, 15, skin.shade)   // curled forward mid-air
                }
            }
        }
        OttoMood.DANCING -> {
            // Alternating sides thrown up — a tiny rave. Raised tips hook outward.
            TENTACLE_COLS.forEachIndexed { idx, cols ->
                val up = (idx % 2 == 0) == (frame == 0)
                if (up) {
                    cols.forEach { x -> px(x, 14, skin.shade) }
                    val tip = (cols.first + if (idx < 3) -1 else 2).coerceIn(0, 19)
                    px(tip, 13, skin.shade); px(tip, 12, skin.shade)
                } else {
                    for (y in 14..16) cols.forEach { x -> px(x, y, skin.shade) }
                }
            }
        }
        OttoMood.PAINTING -> {
            drawEasel(px, frame)
            // Most arms rest; the second-left arm holds the brush up to the canvas,
            // bobbing slightly with each new dab.
            TENTACLE_COLS.forEachIndexed { idx, cols ->
                if (idx == 1) {
                    cols.forEach { x -> px(x, 14, skin.shade) }
                    px(4, 13, skin.shade)
                    val bob = if (frame % 2 == 0) 0 else 1
                    px(3, 12 - bob, GlassHandle)
                    px(3, 11 - bob, GlassHandle)
                    px(3, 10 - bob, Cheek)        // brush tip, paint-loaded
                } else {
                    for (y in 14..16) cols.forEach { x -> px(x, y, skin.shade) }
                }
            }
        }
        OttoMood.JETTING -> { /* unreachable — JETTING draws its own body */ }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Public convenience composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun OctopusTypingIndicator(
    skin: OttoSkin = OttoSkin.PURPLE,
    modifier: Modifier = Modifier,
) = Otto(mood = OttoMood.TYPING, skin = skin, pixel = 3.dp, modifier = modifier)

@Composable
fun OctopusLoadingIndicator(
    skin: OttoSkin = OttoSkin.PURPLE,
    modifier: Modifier = Modifier,
) = Otto(mood = OttoMood.IDLE, skin = skin, pixel = 4.dp, modifier = modifier)

@Composable
fun OctopusThinkingIndicator(
    skin: OttoSkin = OttoSkin.PURPLE,
    modifier: Modifier = Modifier,
) = Otto(mood = OttoMood.THINKING, skin = skin, pixel = 3.dp, modifier = modifier)

@Composable
fun OttoIdle(
    skin: OttoSkin = OttoSkin.PURPLE,
    pixel: Dp = 3.dp,
    modifier: Modifier = Modifier,
) = Otto(mood = OttoMood.IDLE, skin = skin, pixel = pixel, modifier = modifier)

@Composable
fun OttoExcited(
    skin: OttoSkin = OttoSkin.PURPLE,
    pixel: Dp = 4.dp,
    modifier: Modifier = Modifier,
) = Otto(mood = OttoMood.EXCITED, skin = skin, pixel = pixel, modifier = modifier)
