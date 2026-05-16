package com.example.firsahayak

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Shared colours ────────────────────────────────────────────────────────────
private val BgColor      = Color(0xFFF5F2EB)
private val SurfaceColor = Color(0xFFFFFFFF)
private val BorderColor  = Color(0xFFD4CFC4)
private val TextColor    = Color(0xFF1A1814)
private val MutedColor   = Color(0xFF6B6457)
private val AccentRed    = Color(0xFFC0392B)
private val AccentBlue   = Color(0xFF1A3A5C)
private val GreenColor   = Color(0xFF1A6B3A)
private val AmberColor   = Color(0xFFB85C00)


/*
 * SectionBreakdownCard.kt — Legal section severity display layer.
 *
 * Renders a full per-section breakdown for every act in a FirEntity.
 * Entry point is SectionBreakdownCard(entity) which auto-routes to:
 *
 *   BnsOnlyBreakdown  — used for audio path and BNS-only PDFs.
 *                       Passes all sections directly to BnsSeverityDb.
 *
 *   MultiActBreakdown — used for PDF FIRs that invoke multiple acts
 *                       (e.g. BNS + POCSO + Dowry Act simultaneously).
 *                       Separates BNS sections from other-act sections,
 *                       queries each DB independently, and computes an
 *                       overall severity as max(bnsScore, otherActScores).
 *
 * Each section row displays:
 *   • Section number pill (dark blue = BNS, amber = other act)
 *   • Severity badge (LOW / MEDIUM / HIGH / CRITICAL)
 *   • Short title and plain-language definition
 *   • Maximum punishment string
 *   • Cognisable / Non-cognisable chip
 *   • Bailable / Non-bailable chip
 *   • Urgency score out of 10
 *
 * Unmatched sections (not found in either DB) are shown in an amber
 * warning row prompting manual verification.
 */

// ─────────────────────────────────────────────────────────────────────────────
// PUBLIC ENTRY POINT
// Routes to BnsOnlyBreakdown (audio) or MultiActBreakdown (PDF with named acts)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SectionBreakdownCard(entity: FirEntity) {
    val allSections = entity.acts.flatMap { it.sections }
    if (allSections.isEmpty()) return

    // Check whether any act is a recognised non-BNS act (Dowry, POCSO, SC/ST…)
    val hasNamedNonBnsActs = entity.acts.any { act ->
        !act.actName.contains("BNS", ignoreCase = true) &&
                !act.actName.contains("भारतीय न्याय संहिता", ignoreCase = true) &&
                !act.actName.contains("Bharatiya Nyaya Sanhita", ignoreCase = true) &&
                OtherActsSeverityDb.isNonBnsAct(act.actName)
    }

    if (hasNamedNonBnsActs) {
        // PDF with multiple named acts
        MultiActBreakdown(entity)
    } else {
        // Audio path OR simple BNS-only PDF
        // Treat ALL sections as BNS — no act-name filtering needed
        BnsOnlyBreakdown(allSections)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BNS-ONLY BREAKDOWN  (audio + simple PDF)
// Passes all sections directly to BnsSeverityDb — no act-name filtering
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BnsOnlyBreakdown(sections: List<String>) {
    val result = BnsSeverityDb.computeSeverity(sections)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceColor)
            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "SECTIONS APPLIED",
            fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = MutedColor
        )
        HorizontalDivider(color = BorderColor)

        OverallSeverityBar(
            score      = result.urgencyScore,
            level      = result.level,
            cognisable = result.cognisable,
            bailable   = result.bailable
        )
        HorizontalDivider(color = BorderColor)

        if (result.matchedEntries.isNotEmpty()) {
            ActGroupHeader("THE BHARATIYA NYAYA SANHITA (BNS), 2023")
            result.matchedEntries
                .sortedByDescending { it.urgencyPoints }
                .forEach { entry ->
                    BnsSectionRow(entry)
                    HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))
                }
        }

        if (result.unmatchedSecs.isNotEmpty()) {
            UnmatchedSectionsRow(result.unmatchedSecs)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MULTI-ACT BREAKDOWN  (PDF with Dowry Act, POCSO, etc. alongside BNS)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MultiActBreakdown(entity: FirEntity) {
    val bnsActs = entity.acts.filter { act ->
        act.actName.contains("BNS", ignoreCase = true) ||
                act.actName.contains("भारतीय न्याय संहिता", ignoreCase = true) ||
                act.actName.contains("Bharatiya Nyaya Sanhita", ignoreCase = true)
    }
    val otherActs = entity.acts.filter { act ->
        !act.actName.contains("BNS", ignoreCase = true) &&
                !act.actName.contains("भारतीय न्याय संहिता", ignoreCase = true) &&
                !act.actName.contains("Bharatiya Nyaya Sanhita", ignoreCase = true)
    }

    val bnsSections = bnsActs.flatMap { it.sections }
    val bnsResult   = BnsSeverityDb.computeSeverity(bnsSections)

    val allOtherResults = otherActs.map { act ->
        OtherActsSeverityDb.computeSeverity(
            OtherActsSeverityDb.detectActType(act.actName),
            act.sections
        )
    }

    // Overall = max urgency score across all acts
    val overallScore = maxOf(
        bnsResult.urgencyScore,
        allOtherResults.maxOfOrNull { it.urgencyScore } ?: 0
    )
    val overallLevel = when (overallScore) {
        in 1..3 -> BnsSeverityDb.SeverityLevel.LOW
        in 4..5 -> BnsSeverityDb.SeverityLevel.MEDIUM
        in 6..7 -> BnsSeverityDb.SeverityLevel.HIGH
        else    -> BnsSeverityDb.SeverityLevel.CRITICAL
    }
    val overallCognisable = bnsResult.cognisable || allOtherResults.any { it.cognisable }
    val overallBailable   = bnsResult.bailable   && allOtherResults.all { it.bailable }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceColor)
            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "SECTIONS APPLIED",
            fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = MutedColor
        )
        HorizontalDivider(color = BorderColor)

        OverallSeverityBar(overallScore, overallLevel, overallCognisable, overallBailable)
        HorizontalDivider(color = BorderColor)

        // ── BNS sections ──────────────────────────────────────────────────────
        if (bnsResult.matchedEntries.isNotEmpty()) {
            ActGroupHeader("THE BHARATIYA NYAYA SANHITA (BNS), 2023")
            bnsResult.matchedEntries
                .sortedByDescending { it.urgencyPoints }
                .forEach { entry ->
                    BnsSectionRow(entry)
                    HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))
                }
            if (bnsResult.unmatchedSecs.isNotEmpty()) {
                UnmatchedSectionsRow(bnsResult.unmatchedSecs)
            }
        }

        // ── Other named acts ──────────────────────────────────────────────────
        otherActs.forEach { act ->
            val actType = OtherActsSeverityDb.detectActType(act.actName)
            val result  = OtherActsSeverityDb.computeSeverity(actType, act.sections)

            HorizontalDivider(color = BorderColor)
            ActGroupHeader(act.actName)

            if (result.matchedEntries.isNotEmpty()) {
                result.matchedEntries
                    .sortedByDescending { it.urgencyPoints }
                    .forEach { entry ->
                        OtherActSectionRow(entry)
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))
                    }
            }
            if (result.unmatchedSecs.isNotEmpty()) {
                UnmatchedSectionsRow(result.unmatchedSecs)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OVERALL SEVERITY BAR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OverallSeverityBar(
    score      : Int,
    level      : BnsSeverityDb.SeverityLevel,
    cognisable : Boolean,
    bailable   : Boolean
) {
    val (bg, fg) = severityColors(level)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$score",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = TextColor
            )
            Text("/10", fontSize = 11.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(bg)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    BnsSeverityDb.levelName(level).uppercase(),
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, color = fg
                )
            }
            UrgencyBar(score = score, level = level)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MetaChip(
                    label = if (cognisable) "Cognisable" else "Non-cognisable",
                    color = if (cognisable) GreenColor else AccentRed,
                    bg    = if (cognisable) Color(0xFFD5F0DE) else Color(0xFFFDE0DE)
                )
                MetaChip(
                    label = if (bailable) "Bailable" else "Non-bailable",
                    color = if (bailable) GreenColor else AccentRed,
                    bg    = if (bailable) Color(0xFFD5F0DE) else Color(0xFFFDE0DE)
                )
            }
        }
    }
}

@Composable
private fun UrgencyBar(score: Int, level: BnsSeverityDb.SeverityLevel) {
    val (_, fg) = severityColors(level)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(BorderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(score / 10f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(fg)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ACT GROUP HEADER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActGroupHeader(actName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(AccentBlue.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            actName,
            fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, color = AccentBlue
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BNS SECTION ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BnsSectionRow(entry: BnsSeverityDb.BnsEntry) {
    val (bg, fg) = severityColors(entry.severity)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(AccentBlue)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    "BNS ${entry.section}",
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, color = Color.White
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(bg)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    BnsSeverityDb.levelName(entry.severity).uppercase(),
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium, color = fg
                )
            }
            Text(
                entry.shortTitle,
                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = TextColor, modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(bg.copy(alpha = 0.6f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    "${entry.urgencyPoints}/10",
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                    color = fg, fontWeight = FontWeight.Bold
                )
            }
        }
        Text(entry.definition, fontSize = 11.sp, color = MutedColor, lineHeight = 16.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Max punishment:",
                fontSize = 10.sp, color = MutedColor, fontFamily = FontFamily.Monospace
            )
            Text(
                entry.maxPunishment,
                fontSize = 10.sp, color = TextColor,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallChip(
                label = if (entry.cognisable) "Cognisable" else "Non-cognisable",
                color = if (entry.cognisable) GreenColor else MutedColor,
                bg    = if (entry.cognisable) Color(0xFFD5F0DE) else BorderColor
            )
            SmallChip(
                label = if (entry.bailable) "Bailable" else "Non-bailable",
                color = if (entry.bailable) GreenColor else AccentRed,
                bg    = if (entry.bailable) Color(0xFFD5F0DE) else Color(0xFFFDE0DE)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OTHER ACT SECTION ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OtherActSectionRow(entry: OtherActsSeverityDb.OtherActEntry) {
    val (bg, fg) = severityColors(entry.severity)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(AmberColor)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    "Sec ${entry.section}",
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, color = Color.White
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(bg)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    BnsSeverityDb.levelName(entry.severity).uppercase(),
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium, color = fg
                )
            }
            Text(
                entry.shortTitle,
                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = TextColor, modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(bg.copy(alpha = 0.6f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    "${entry.urgencyPoints}/10",
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                    color = fg, fontWeight = FontWeight.Bold
                )
            }
        }
        Text(entry.definition, fontSize = 11.sp, color = MutedColor, lineHeight = 16.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Max punishment:",
                fontSize = 10.sp, color = MutedColor, fontFamily = FontFamily.Monospace
            )
            Text(
                entry.maxPunishment,
                fontSize = 10.sp, color = TextColor,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallChip(
                label = if (entry.cognisable) "Cognisable" else "Non-cognisable",
                color = if (entry.cognisable) GreenColor else MutedColor,
                bg    = if (entry.cognisable) Color(0xFFD5F0DE) else BorderColor
            )
            SmallChip(
                label = if (entry.bailable) "Bailable" else "Non-bailable",
                color = if (entry.bailable) GreenColor else AccentRed,
                bg    = if (entry.bailable) Color(0xFFD5F0DE) else Color(0xFFFDE0DE)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UNMATCHED SECTIONS WARNING
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UnmatchedSectionsRow(sections: List<String>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFFDF6EC))
            .border(1.dp, Color(0xFFF0D9B5), RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Sections not found in database — verify manually",
                fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium, color = AmberColor
            )
            Text(
                sections.joinToString(", "),
                fontSize = 11.sp, color = AmberColor, fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CHIP HELPERS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MetaChip(label: String, color: Color, bg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            label, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium, color = color
        )
    }
}

@Composable
private fun SmallChip(label: String, color: Color, bg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            label, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium, color = color
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COLOUR HELPER
// ─────────────────────────────────────────────────────────────────────────────

private fun severityColors(level: BnsSeverityDb.SeverityLevel): Pair<Color, Color> = when (level) {
    BnsSeverityDb.SeverityLevel.CRITICAL -> Color(0xFF7B0000) to Color.White
    BnsSeverityDb.SeverityLevel.HIGH     -> Color(0xFFFDE0DE) to AccentRed
    BnsSeverityDb.SeverityLevel.MEDIUM   -> Color(0xFFFDECD6) to AmberColor
    BnsSeverityDb.SeverityLevel.LOW      -> Color(0xFFD5F0DE) to GreenColor
}
