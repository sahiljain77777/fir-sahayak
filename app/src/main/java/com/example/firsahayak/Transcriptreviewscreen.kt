package com.example.firsahayak

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgColor      = Color(0xFFF5F2EB)
private val SurfaceColor = Color(0xFFFFFFFF)
private val BorderColor  = Color(0xFFD4CFC4)
private val TextColor    = Color(0xFF1A1814)
private val MutedColor   = Color(0xFF6B6457)
private val AccentBlue   = Color(0xFF1A3A5C)
private val GreenColor   = Color(0xFF1A6B3A)
private val AccentRed    = Color(0xFFC0392B)
private val AmberColor   = Color(0xFFB85C00)
private val AmberBg      = Color(0xFFFDF6EC)
private val AmberBorder  = Color(0xFFF0D9B5)

/**
 * Shown after STT completes, before Gemma analysis.
 * User can read, correct, then confirm the transcript.
 *
 * @param transcript  Raw text from Android STT
 * @param onConfirm   Called with (possibly edited) transcript → triggers Gemma
 * @param onReRecord  User wants to speak again → goes back to Idle
 */
@Composable
fun TranscriptReviewScreen(
    transcript: String,
    onConfirm : (String) -> Unit,
    onReRecord: () -> Unit
) {
    var editedText  by remember { mutableStateOf(transcript) }
    var isEditing   by remember { mutableStateOf(false) }
    val wordCount   = editedText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
    val charCount   = editedText.length
    val wasEdited   = editedText.trim() != transcript.trim()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AccentBlue)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Review Transcript",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Check what was recorded. Correct any errors before analysis.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Info banner ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AmberBg)
                .border(1.dp, AmberBorder, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text("!", color = AmberColor, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Speech-to-text may have errors",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AmberColor
                    )
                    Text(
                        "Names, addresses, section numbers, and Hindi words are most likely to be wrong. Tap the text below to edit.",
                        fontSize = 11.sp,
                        color = AmberColor.copy(alpha = 0.85f),
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Transcript editor card ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceColor)
                .border(
                    width = if (isEditing) 2.dp else 1.dp,
                    color = if (isEditing) AccentBlue else BorderColor,
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "RECORDED TRANSCRIPT",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = MutedColor
                )
                if (wasEdited) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFEEF6FF))
                            .border(1.dp, Color(0xFFB8D4F0), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "Edited",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AccentBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderColor)

            // Editable text field — uses BasicTextField for full control
            BasicTextField(
                value = editedText,
                onValueChange = { editedText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 160.dp)
                    .onFocusChanged { isEditing = it.isFocused },
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = TextColor,
                    lineHeight = 22.sp,
                    fontFamily = FontFamily.Default
                ),
                cursorBrush = SolidColor(AccentBlue),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (editedText.isEmpty()) {
                            Text(
                                "Transcript will appear here…",
                                fontSize = 14.sp,
                                color = MutedColor.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            HorizontalDivider(color = BorderColor)

            // Word / char count footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatPill(label = "Words", value = "$wordCount")
                StatPill(label = "Chars", value = "$charCount")
                if (wasEdited) {
                    Text(
                        "* Changes will be sent to Gemma",
                        fontSize = 10.sp,
                        color = AccentBlue,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Quick tips card ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceColor)
                .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "WHAT TO CHECK",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = MutedColor
            )
            HorizontalDivider(color = BorderColor)
            listOf(
                "Names of accused and complainant — spelled correctly?",
                "Addresses and locality names — accurate?",
                "Dates and times — correctly captured?",
                "Section numbers mentioned — e.g. 'dhara 302' → '302'",
                "Hindi words — transliterated correctly?"
            ).forEach { tip ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        "•",
                        fontSize = 12.sp,
                        color = AccentBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        tip,
                        fontSize = 12.sp,
                        color = MutedColor,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Action buttons ────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Primary: confirm and analyse
            Button(
                onClick = { onConfirm(editedText.trim()) },
                enabled = editedText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenColor,
                    disabledContainerColor = BorderColor
                )
            ) {
                Text(
                    if (wasEdited) "CONFIRM EDITS & ANALYSE" else "LOOKS CORRECT — ANALYSE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                    color         = Color.White
                )
            }

            // Secondary: re-record
            OutlinedButton(
                onClick = onReRecord,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "Re-record Audio",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MutedColor
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Small stat pill ───────────────────────────────────────────────────────────
@Composable
private fun StatPill(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 10.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(BgColor)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                value,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TextColor
            )
        }
    }
}