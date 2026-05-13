package com.example.firsahayak

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Generates a filled FIR PDF matching the official BNS FIR form layout exactly.
 *
 * Design rules:
 *  ✓ NO horizontal divider line between title block and section 1
 *  ✓ Larger vertical gap after title before section 1
 *  ✓ Increased line-height throughout to fill the page
 *  ✓ All section headings bold
 *  ✓ Section 3(a): Day | Date From | Date To — all ONE line
 *              then: Time Period (समय) | Time From | Time To — same indentation
 *  ✓ Section 3(b): heading + Date + Time — stretched across full width
 *  ✓ Section 3(c): heading + Entry No + Date/Time — stretched across full width
 *  ✓ Section 5(a): heading + direction+distance value + Beat No pushed to far right
 *  ✓ Section 6(e): address value on same line as label
 *  ✓ Section 7: full heading one line; vertical lines in table
 *  ✓ Section 9/10: NO horizontal rule between them; vertical lines in table
 *  ✓ Signature block pinned to bottom
 */
object FirPdfGenerator {

    private const val TAG = "FirPdfGenerator"

    // ── Page geometry (A4 @ 72 dpi = 595 × 842 pts) ──────────────────────────
    private const val PW   = 595f
    private const val PH   = 842f
    private const val ML   = 42f
    private const val MR   = 42f
    private const val MT   = 36f
    private const val MB   = 52f
    private const val CW   = PW - ML - MR          // 511

    private const val SAFE_Y = PH - MB - 18f

    // ── Vertical rhythm — slightly increased to spread content across page ─────
    private const val LH   = 15f   // normal line height  (was 13)
    private const val SG   = 8f    // gap after each section (was 5)

    private const val LV_GAP = 3f

    // ─────────────────────────────────────────────────────────────────────────
    // Paint helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun pLabel(sz: Float = 7.5f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sz; color = Color.BLACK
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    }

    private fun pBold(sz: Float = 8f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sz; color = Color.BLACK
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }

    private fun pValue(sz: Float = 8f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sz; color = Color.parseColor("#111111")
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }

    private fun pLine() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; strokeWidth = 0.55f; style = Paint.Style.STROKE
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DrawState
    // ─────────────────────────────────────────────────────────────────────────

    private class DrawState(val doc: PdfDocument) {
        var c: Canvas = Canvas()
        var y: Float  = MT
        private var pageNum = 0
        private var activePage: PdfDocument.Page? = null

        fun startNewPage() {
            finishPage()
            pageNum++
            val page = doc.startPage(
                PdfDocument.PageInfo.Builder(PW.toInt(), PH.toInt(), pageNum).create()
            )
            activePage = page
            c = page.canvas
            y = MT
        }

        fun finishPage() {
            activePage?.let { doc.finishPage(it) }
            activePage = null
        }

        fun down(dy: Float) {
            y += dy
            if (y > SAFE_Y) { finishPage(); startNewPage() }
        }

        fun need(needed: Float) {
            if (y + needed > SAFE_Y) { finishPage(); startNewPage() }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    private fun w(text: String, p: Paint) = p.measureText(text) + LV_GAP

    private fun fit(text: String, maxW: Float, p: Paint): String {
        if (text.isBlank() || p.measureText(text) <= maxW) return text
        val ell = "…"; var end = text.length
        while (end > 1 && p.measureText(text.substring(0, end) + ell) > maxW) end--
        return text.substring(0, end) + ell
    }

    /** Draw label then value inline; value truncated to maxRight. */
    private fun lv(
        s: DrawState, x: Float,
        label: String, value: String,
        lp: Paint = pLabel(), vp: Paint = pValue(8f),
        maxRight: Float = ML + CW
    ) {
        s.c.drawText(label, x, s.y, lp)
        val vx = x + w(label, lp)
        val avail = maxRight - vx
        if (avail > 4f) s.c.drawText(fit(value, avail, vp), vx, s.y, vp)
    }

    private fun wrap(s: DrawState, text: String, x: Float, maxW: Float, p: Paint, lh: Float = LH) {
        if (text.isBlank()) return
        val words = text.trim().split(Regex("\\s+"))
        val buf   = StringBuilder()
        for (word in words) {
            val test = if (buf.isEmpty()) word else "$buf $word"
            if (p.measureText(test) > maxW) {
                if (buf.isNotEmpty()) { s.c.drawText(buf.toString(), x, s.y, p); s.down(lh) }
                buf.clear()
                if (p.measureText(word) > maxW) {
                    s.c.drawText(fit(word, maxW, p), x, s.y, p); s.down(lh); continue
                }
            }
            if (buf.isNotEmpty()) buf.append(' ')
            buf.append(word)
        }
        if (buf.isNotEmpty()) { s.c.drawText(buf.toString(), x, s.y, p); s.down(lh) }
    }

    private fun hline(s: DrawState) = s.c.drawLine(ML, s.y, ML + CW, s.y, pLine())

    // ─────────────────────────────────────────────────────────────────────────
    // Public entry point
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun generate(context: Context, entity: FirEntity): File = withContext(Dispatchers.IO) {
        val doc   = PdfDocument()
        val state = DrawState(doc)
        state.startNewPage()

        drawTitle(state)
        drawSec1(state, entity)
        drawSec2(state, entity)
        drawSec3(state, entity)
        drawSec4(state, entity)
        drawSec5(state, entity)
        drawSec6(state, entity)
        drawSec7(state, entity)
        drawSec8(state, entity)
        drawSec9_10(state, entity)
        drawSec11(state)
        drawSec12(state, entity)
        drawSignatures(state)

        state.finishPage()

        val outDir  = File(context.filesDir, "fir_output").also { it.mkdirs() }
        val outFile = File(outDir, "FIR_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        Log.i(TAG, "PDF written: ${outFile.absolutePath}")
        outFile
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TITLE  — no hline after; larger gap before section 1
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawTitle(s: DrawState) {
        val cx = ML + CW / 2f
        fun centred(text: String, p: Paint, gap: Float = 14f) {
            s.c.drawText(text, cx - p.measureText(text) / 2f, s.y, p)
            s.down(gap)
        }
        centred("FIRST INFORMATION REPORT",                   pBold(13f), 16f)
        centred("(Under Section 173 B.N.S.S.)",               pBold(8.5f), 13f)
        centred("(प्रथम सूचना रिपोर्ट)",                     pBold(8.5f), 13f)
        centred("(धारा 173 बी. एन. एस. एस. के अन्तर्गत)",   pBold(8.5f), 13f)
        // ── Increased gap before the first section heading ──
        s.down(18f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 1
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawSec1(s: DrawState, e: FirEntity) {
        val lp = pBold(7.5f); val vp = pValue(8f)

        val distLabel = "1. District (जिला):"
        lv(s, ML, distLabel, e.district, lp, vp, maxRight = ML + 170f)

        val yearLabel = "Year (वर्ष):"
        val yearX = ML + 210f
        lv(s, yearX, yearLabel, e.year, lp, vp, maxRight = yearX + 65f)

        val psLabel = "P.S. (थाना):"
        lv(s, ML + 290f, psLabel, e.policeStation, lp, vp, maxRight = ML + CW)
        s.down(LH)

        val firLabel = "FIR No (प्र.सू.रि.सं.):"
        lv(s, ML, firLabel, e.firNumber, lp, vp, maxRight = ML + 175f)

        val filing = listOf(e.dateOfFiling, e.timeOfFiling).filter { it.isNotBlank() }.joinToString("  ")
        lv(s, ML + 210f, "Date & Time of FIR (व दिनांक की प्रसूप्र):", filing, lp, vp, maxRight = ML + CW)
        s.down(LH + SG)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 2
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawSec2(s: DrawState, e: FirEntity) {
        val lp = pBold(7.5f); val vp = pValue(7.5f)
        val secColX = ML + 210f
        val actMaxW = secColX - ML - 18f
        val secMaxW = ML + CW - secColX

        s.c.drawText("2. Act(s) (अधिनियम ):", ML, s.y, lp)
        s.c.drawText("Section(s) (धाराएँ):", secColX, s.y, lp)
        s.down(LH)

        if (e.acts.isEmpty()) { s.down(LH) } else {
            e.acts.forEach { act ->
                s.need(LH)
                s.c.drawText("–  ${fit(act.actName, actMaxW, vp)}", ML + 4f, s.y, vp)
                s.c.drawText(fit(act.sections.joinToString(" / "), secMaxW, vp), secColX, s.y, vp)
                s.down(LH)
            }
        }
        s.down(SG)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 3 — Occurrence of Offence
    //
    // Exact layout matching original image:
    //
    //  Row A: "(a) Day (दिन):  <dayType>    Date From (दिनांक से):  <df>    Date To (दिनांक तक):  <dt>"
    //  Row B: "Time Period (समय)            Time From (समय से):   <tf>    Time To (समय तक):    <tt>"
    //         ↑ same left edge as (a)       ↑ same X as Date From        ↑ same X as Date To
    //  Row C: "(b) Information received at P.S.(…)    Date(दिनांक): <d>              Time  <t>"
    //  Row D: "(c)General Diary Reference (रोजानामचा)  Entry No.(प्रविष्टि)  <n>  Date/Time  <dt>"
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawSec3(s: DrawState, e: FirEntity) {
        val lp  = pBold(7.5f)
        val lpr = pLabel(7.5f)
        val vp  = pValue(7.5f)
        val o   = e.occurrence

        s.c.drawText("3. Occurrence of Offence (अपराध की घटना):", ML, s.y, lp)
        s.down(LH)

        // ── Fixed column anchors ───────────────────────────────────────────────
        // "Date From" column — fixed at this X regardless of day-type length
        val dfColX  = ML + 148f
        // "Date To" column — fixed far right
        val dtColX  = ML + 318f

        // ── Row A: (a) Day | Date From | Date To ──────────────────────────────
        s.c.drawText("(a) Day (दिन):", ML, s.y, lp)
        val dayValX = ML + w("(a) Day (दिन):", lp)
        // day value — truncate so it doesn't overlap Date From column
        s.c.drawText(fit(o.dayType, dfColX - dayValX - 4f, vp), dayValX, s.y, vp)

        s.c.drawText("Date From (दिनांक से):", dfColX, s.y, lp)
        val dfValX = dfColX + w("Date From (दिनांक से):", lp)
        s.c.drawText(o.dateFrom, dfValX, s.y, vp)

        s.c.drawText("Date To (दिनांक तक):", dtColX, s.y, lp)
        val dtValX = dtColX + w("Date To (दिनांक तक):", lp)
        s.c.drawText(o.dateTo, dtValX, s.y, vp)
        s.down(LH)

        // ── Row B: Time Period | Time From | Time To ──────────────────────────
        // "Time Period (समय)" at same left edge as "(a) Day"
        s.c.drawText("Time Period (समय)", ML, s.y, lpr)

        // "Time From" at same X as "Date From" above
        s.c.drawText("Time From (समय से):", dfColX, s.y, lp)
        val tfValX = dfColX + w("Time From (समय से):", lp)
        s.c.drawText(formatHrs(o.timeFrom), tfValX, s.y, vp)

        // "Time To" at same X as "Date To" above
        s.c.drawText("Time To (समय तक):", dtColX, s.y, lp)
        val ttValX = dtColX + w("Time To (समय तक):", lp)
        s.c.drawText(formatHrs(o.timeTo), ttValX, s.y, vp)
        s.down(LH)

        // ── Row C: (b) Info received at P.S. — full width ─────────────────────
        val bHead  = "(b) Information received at P.S.(थाना जहां सूचना प्राप्त हुई):"
        s.c.drawText(bHead, ML, s.y, lp)

        // "Date(दिनांक):" anchored at ~55% — same anchor as Entry No below
        val bDateX = ML + 228f
        s.c.drawText("Date (दिनांक):", bDateX, s.y, lp)
        val bDateVX = bDateX + w("Date (दिनांक):", lp)
        s.c.drawText(o.infoReceivedAtPsDate, bDateVX, s.y, vp)

        // "Time" pushed far right — same anchor as Date/Time below
        val bTimeX = ML + 388f
        s.c.drawText("Time", bTimeX, s.y, lp)
        val bTimeVX = bTimeX + w("Time", lp)
        s.c.drawText(formatHrs(o.infoReceivedAtPsTime), bTimeVX, s.y, vp)
        s.down(LH)

        // ── Row D: (c) General Diary Reference — full width ───────────────────
        val cHead  = "(c)General Diary Reference (रोजानामचा संदर्भ):"
        s.c.drawText(cHead, ML, s.y, lp)

        // "Entry No." at same anchor as "Date(दिनांक):" above
        val cEntX  = ML + 228f
        s.c.drawText("Entry No. (प्रविष्टि संख्या.)", cEntX, s.y, lp)
        val cEntVX = cEntX + w("Entry No. (प्रविष्टि संख्या.)", lp)
        s.c.drawText(o.gdEntryNumber, cEntVX, s.y, vp)

        // "Date/Time" at same anchor as "Time" above
        val cDtX   = ML + 388f
        s.c.drawText("Date/Time", cDtX, s.y, lp)
        val cDtVX  = cDtX + w("Date/Time", lp)
        s.c.drawText(o.gdEntryDatetime, cDtVX, s.y, vp)
        s.down(LH + SG)
    }

    private fun formatHrs(time: String): String {
        if (time.isBlank()) return ""
        return if (time.contains("hrs", ignoreCase = true) ||
            time.contains("AM") || time.contains("PM")) time
        else "$time hrs"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 4
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawSec4(s: DrawState, e: FirEntity) {
        lv(s, ML, "4. Type of Information (सूचना का प्रकार):", e.typeOfInformation.ifBlank { "Written" },
            pBold(7.5f), pValue(8f))
        s.down(LH + SG)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 5 — Place of Occurrence
    //
    // (a): heading left portion, direction+distance in middle, Beat No far right
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawSec5(s: DrawState, e: FirEntity) {
        val lp = pBold(7.5f); val vp = pValue(7.5f)
        val pl = e.place

        s.c.drawText("5. Place of Occurrence (घटनास्थल):", ML, s.y, lp)
        s.down(LH)

        // ── (a) one line: heading | direction+distance | Beat No far right ─────
        val aHead = "(a) Direction and Distance from P.S (थाने से दिशा और दूरी)"
        s.c.drawText(aHead, ML, s.y, lp)

        val dirDistVal = buildString {
            if (pl.directionFromPs.isNotBlank()) append(pl.directionFromPs)
            if (pl.distanceFromPsKm.isNotBlank()) {
                if (isNotEmpty()) append(" , ")
                append(pl.distanceFromPsKm).append(" Km(s)")
            }
        }
        val aHeadEndX = ML + w(aHead, lp)
        s.c.drawText(dirDistVal, aHeadEndX, s.y, vp)

        // Beat No anchored to far right edge
        val beatLabel = "Beat No(बीट सं.) :"
        val beatValW  = vp.measureText(pl.beatNumber)
        val beatLblW  = lp.measureText(beatLabel) + LV_GAP
        val beatX     = ML + CW - beatLblW - beatValW - 2f
        s.c.drawText(beatLabel, beatX, s.y, lp)
        s.c.drawText(pl.beatNumber, beatX + beatLblW, s.y, vp)
        s.down(LH)

        // ── (b) Address — label and value same line ────────────────────────────
        val addrLabel = "(b) Address (पता):"
        s.c.drawText(addrLabel, ML, s.y, lp)
        val addrVX = ML + w(addrLabel, lp)
        s.c.drawText(fit(pl.address, ML + CW - addrVX, vp), addrVX, s.y, vp)
        s.down(LH)

        // ── (c) Outside PS limit ──────────────────────────────────────────────
        s.c.drawText(
            "(c) In case, Outside the limit of the Police Station (यदि थाना सीमा के बाहर हैं):",
            ML, s.y, pBold(7f)
        )
        s.down(LH)
        lv(s, ML + 6f, "Name of P.S (थाना का नाम):", pl.outsidePsName, lp, vp, maxRight = ML + 250f)
        lv(s, ML + 258f, "District (जिला):", pl.outsideDistrict, lp, vp, maxRight = ML + CW)
        s.down(LH + SG)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 6
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawSec6(s: DrawState, e: FirEntity) {
        val lp = pBold(7.5f); val vp = pValue(7.5f)
        val co = e.complainant

        s.need(LH * 8f)
        s.c.drawText("6. Complainant / Informant (शिकायतकर्ता / सूचनाकर्ता):", ML, s.y, lp)
        s.down(LH)

        // (a) Name
        val nameLabel = "(a) Name (नाम):"
        s.c.drawText(nameLabel, ML, s.y, lp)
        val nameVX   = ML + w(nameLabel, lp)
        val nameMaxW = 128f
        s.c.drawText(fit(co.name, nameMaxW, vp), nameVX, s.y, vp)
        val soX = nameVX + nameMaxW + 4f
        s.c.drawText("(S/O)", soX, s.y, lp)
        val foMaxW = ML + CW - (soX + w("(S/O)", lp))
        s.c.drawText(fit(co.fathersOrHusbandsName, foMaxW, vp), soX + w("(S/O)", lp), s.y, vp)
        s.down(LH)

        // (b)
        lv(s, ML, "(b) Date/Year of Birth (जन्म तिथि):", co.dateOfBirth, lp, vp, maxRight = ML + 265f)
        lv(s, ML + 268f, "Nationality (राष्ट्रीयता):", co.nationality.ifBlank { "INDIA" }, lp, vp, maxRight = ML + CW)
        s.down(LH)

        // (c)
        lv(s, ML, "(c) Passport No. (पासपोर्ट सं.):", co.passportNumber, lp, vp, maxRight = ML + 185f)
        lv(s, ML + 188f, "Date of Issue (जारी करने की तिथि):", co.passportIssueDate, lp, vp, maxRight = ML + 360f)
        lv(s, ML + 363f, "Place of Issue (जारी करने का स्थान):", co.passportIssuePlace, lp, vp, maxRight = ML + CW)
        s.down(LH)

        // (d)
        lv(s, ML, "(d) Occupation:", co.occupation, lp, vp, maxRight = ML + CW)
        s.down(LH)

        // (e) Address — label and value on same line, overflow wraps below
        val addrLabel = "(e) Address(पता):"
        s.c.drawText(addrLabel, ML, s.y, lp)
        val addrVX   = ML + w(addrLabel, lp)
        val addrMaxW = ML + CW - addrVX

        if (co.address.isNotBlank()) {
            val words = co.address.trim().split(Regex("\\s+"))
            val buf   = StringBuilder()
            var firstDone = false
            for (word in words) {
                val avail = if (!firstDone) addrMaxW else CW - 6f
                val test  = if (buf.isEmpty()) word else "$buf $word"
                if (vp.measureText(test) > avail) {
                    val px = if (!firstDone) addrVX else ML + 6f
                    if (buf.isNotEmpty()) { s.c.drawText(buf.toString(), px, s.y, vp); s.down(LH) }
                    firstDone = true; buf.clear()
                    if (vp.measureText(word) > CW - 6f) {
                        s.c.drawText(fit(word, CW - 6f, vp), ML + 6f, s.y, vp); s.down(LH); continue
                    }
                }
                if (buf.isNotEmpty()) buf.append(' ')
                buf.append(word)
            }
            if (buf.isNotEmpty()) {
                s.c.drawText(buf.toString(), if (!firstDone) addrVX else ML + 6f, s.y, vp)
                s.down(LH)
            }
        } else {
            s.down(LH)
        }

        if (co.contact.isNotBlank()) { s.c.drawText(co.contact, ML + 6f, s.y, vp); s.down(LH) }
        s.down(SG)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 7 — Accused table with vertical lines
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawSec7(s: DrawState, e: FirEntity) {
        val lp = pBold(7.5f); val vp = pValue(7f); val bp = pBold(7.5f)

        s.need(LH * 5f)
        s.c.drawText(
            "7. Details of Known/Suspect/Unknown accused with full particulars(attach separate sheet if necessary) (ज्ञात / संदिग्ध / अज्ञात अभियुक्तों का पूरे विवरण सहित वर्णन):",
            ML, s.y, lp
        )
        s.down(LH)

        val cSl  = ML;         val cNm  = ML + 24f
        val cFn  = ML + 148f;  val cA1  = ML + 278f
        val cA2  = ML + 394f;  val cEnd = ML + CW

        val nmW = cFn - cNm - 4f
        val fnW = cA1 - cFn - 4f
        val a1W = cA2 - cA1 - 4f
        val a2W = cEnd - cA2

        s.c.drawText("Sl.", cSl, s.y, bp)
        s.c.drawText("Name", cNm, s.y, bp)
        s.c.drawText("Father's Name", cFn, s.y, bp)
        s.c.drawText("Address 1", cA1, s.y, bp)
        s.c.drawText("Address 2", cA2, s.y, bp)
        s.down(3f); hline(s)
        val tableTopY = s.y
        s.down(LH)

        if (e.accused.isEmpty()) { s.down(LH) } else {
            e.accused.forEachIndexed { i, acc ->
                s.need(LH)
                s.c.drawText("${i + 1}", cSl, s.y, vp)
                s.c.drawText(fit(acc.name,        nmW, vp), cNm, s.y, vp)
                s.c.drawText(fit(acc.fathersName, fnW, vp), cFn, s.y, vp)
                s.c.drawText(fit(acc.address1,    a1W, vp), cA1, s.y, vp)
                s.c.drawText(fit(acc.address2,    a2W, vp), cA2, s.y, vp)
                s.down(LH)
            }
        }

        val tableBottomY = s.y
        hline(s)

        val lnP = pLine()
        for (colX in listOf(cNm, cFn, cA1, cA2, cEnd)) {
            s.c.drawLine(colX - 2f, tableTopY, colX - 2f, tableBottomY, lnP)
        }
        s.down(SG+2f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 8
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawSec8(s: DrawState, e: FirEntity) {
        val lp = pBold(7.5f); val vp = pValue(7.5f)
        s.c.drawText(
            "8. Reasons for delay in reporting by the complainant/informant (शिकायतकर्ता / सूचनाकर्ता द्वारा रिपोर्ट देरी से दर्ज कराने के कारण):",
            ML, s.y, lp
        )
        s.down(LH)
        s.c.drawText(e.delayReason.ifBlank { "NO DELAY" }, ML + 6f, s.y, vp)
        s.down(LH + SG)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 9 & 10 — property table + total; NO hline between 9 and 10
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawSec9_10(s: DrawState, e: FirEntity) {
        val lp = pBold(7.5f); val vp = pValue(7f); val bp = pBold(7.5f)

        s.c.drawText("9. Particulars of the properties stolen / involved (attach separate sheet if necessary):", ML, s.y, lp)
        s.down(LH)

        // Column X positions — no borders, no lines, plain text only
        val pSl  = ML;        val pDes = ML + 38f
        val pVal = ML + 376f; val pEnd = ML + CW
        val desW = pVal - pDes - 4f
        val valW = pEnd - pVal

        // Header row — indented to match original
        s.c.drawText("Sl.No. (क्र.सं.)", pSl + 6f, s.y, bp)
        s.c.drawText("Property Type(Description)", pDes + 10f, s.y, bp)
        s.c.drawText("Est. Value(Rs.) (मूल्य (रु में))", pVal, s.y, bp)
        s.down(LH)

        // Data rows — no borders, no lines
        val valid = e.propertyStolen.filter { it.description.isNotBlank() }
        if (valid.isEmpty()) {
            // no rows drawn when empty — original shows nothing here
        } else {
            valid.forEachIndexed { i, prop ->
                s.need(LH)
                s.c.drawText("${i + 1}", pSl + 6f, s.y, vp)
                s.c.drawText(fit(prop.description, desW - 6f, vp), pDes + 6f, s.y, vp)
                s.c.drawText(fit(prop.estimatedValueRs, valW, vp), pVal, s.y, vp)
                s.down(LH)
            }
        }
        s.down(SG)

        // Section 10 — directly after, zero separation, no line
        lv(s, ML,
            "10 Total value of property stolen (चोरी हुई सम्पत्ति का कुल मूल्य)",
            e.totalPropertyValueRs.ifBlank { "" },
            pBold(7.5f), pValue(7.5f))
        s.down(LH + SG)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 11
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawSec11(s: DrawState) {
        s.c.drawText(
            "11. Inquest Report / U.D. Case No., if any (मृत्यु समीक्षा रिपोर्ट / यू.डी. प्रकरण न., यदि कोई हो):",
            ML, s.y, pBold(7.5f)
        )
        s.down(LH + SG)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 12
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawSec12(s: DrawState, e: FirEntity) {
        val lp = pBold(7.5f); val vp = pValue(7.5f)
        s.need(LH * 3f)
        s.c.drawText("12. F.I.R. Contents (attach separate sheet, if required)(प्रथम सूचना रिपोर्ट तथ्य):", ML, s.y, lp)
        s.down(LH)
        if (e.firContents.isNotBlank()) wrap(s, e.firContents, ML + 4f, CW - 8f, vp)
        s.down(SG)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SIGNATURES — pinned to bottom
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawSignatures(s: DrawState) {
        val lp   = pLabel(7.5f)
        val sigY = PH - MB + 4f
        s.c.drawLine(ML, sigY, ML + 140f, sigY, pLine())
        s.c.drawText("Complainant Signature", ML, sigY + 11f, lp)
        val rX = ML + CW - 168f
        s.c.drawLine(rX, sigY, ML + CW, sigY, pLine())
        s.c.drawText("Signature of Officer-in-charge", rX, sigY + 11f, lp)
    }
}