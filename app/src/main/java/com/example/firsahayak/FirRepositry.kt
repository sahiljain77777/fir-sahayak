package com.example.firsahayak

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject


/*
 * FirRepository.kt — Central data layer and pipeline orchestrator.
 *
 * Owns the two main analysis flows:
 *   • analyseFromPdf()     — OCR → clean → Gemma → parse → Done
 *   • analyseTranscript()  — Gemma → parse → Done (audio path)
 *
 * Key responsibilities:
 *   • Runs ML Kit OCR via OcrEngine and feeds cleaned text to LlmEngine
 *   • Streams Gemma tokens back to ViewModel via channelFlow (cross-dispatcher safe)
 *   • cleanOcrActSectionMerge() — fixes OCR-split act/section lines and
 *     normalises Devanagari/Bengali digits before text reaches Gemma
 *   • parseAndMerge() — strips markdown fences, repairs truncated JSON,
 *     and patches any fields Gemma missed using regex pre-extraction hints
 *   • sanitiseOccurrence() — detects and fixes date/time field swaps
 *     that Gemma occasionally produces (e.g. a date value in time_to)
 *   • regexExtract() — fast pre-pass that pulls FIR number, dates, times,
 *     phone numbers as fallback hints before LLM inference runs
 */

class FirRepository(private val context: Context) {

    private val ocr = OcrEngine(context)
    private val stt = SttEngine(context)
    private val llm = LlmEngine(context)

    sealed class Progress {
        data class Status(val message: String) : Progress()
        data class Token(val token: String) : Progress()
        data class Done(val result: JSONObject) : Progress()
        data class Error(val message: String) : Progress()
    }

    suspend fun initialize(modelPath: String) {
        ocr.initialize()
        llm.initialize(modelPath)
    }

    // ── PDF flow ──────────────────────────────────────────────────────────────

    fun analyseFromPdf(uri: Uri): Flow<Progress> = channelFlow {
        try {
            send(Progress.Status("Extracting text via OCR…"))
            val text = ocr.extractTextFromPdf(uri)
            val cleanedText = cleanOcrActSectionMerge(text)
            val hints = regexExtract(cleanedText)
            send(Progress.Status("Analysing with Gemma (this takes 30–120s)…"))
            var rawJson = ""
            llm.extractFirEntities(cleanedText, "pdf") { token ->
                rawJson += token
                send(Progress.Token(token))   // ← send() is safe across contexts
            }
            send(Progress.Status("Parsing results…"))
            val result = parseAndMerge(rawJson, hints)
            send(Progress.Done(result))
        } catch (e: Exception) {
            send(Progress.Error(e.message ?: "Unknown error"))
        }
    }


    private fun normalizeDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(when (ch) {
                // Bengali digits
                '০' -> '0'; '১' -> '1'; '২' -> '2'; '৩' -> '3'; '৪' -> '8'
                '৫' -> '5'; '৬' -> '6'; '৭' -> '9'; '৮' -> '8'; '৯' -> '9'
                // Devanagari digits
                '०' -> '0'; '१' -> '1'; '२' -> '2'; '३' -> '3'; '४' -> '4'
                '५' -> '5'; '६' -> '6'; '७' -> '7'; '८' -> '8'; '९' -> '9'
                // Extended Arabic-Indic digits (used in some Urdu/Arabic prints)
                '\u0660' -> '0'; '\u0661' -> '1'; '\u0662' -> '2'; '\u0663' -> '3'
                '\u0664' -> '4'; '\u0665' -> '5'; '\u0666' -> '6'; '\u0667' -> '7'
                '\u0668' -> '8'; '\u0669' -> '9'
                else -> ch
            })
        }
        return sb.toString()
    }



    private fun cleanOcrActSectionMerge(text: String): String {
        // ── Step 1: normalize all non-Latin digits first ──────────────────────
        val normalizedText = normalizeDigits(text)

        // ── Step 2: rest of your existing logic ──────────────────────────────
        val lines = normalizedText.lines()
        val result = StringBuilder()

        val actKeywords = listOf(
            "न्याय संहिता", "bharatiya nyaya", "nyaya sanhita",
            "अधिनियम", "BNS", "IPC",
            "POCSO", "NDPS", "UAPA",
            "Protection Act", "Prevention Act", "Prohibition Act",
            "मुस्लिम महिला", "दहेज", "शस्त्र"
        )

        val sectionPattern = Regex(
            """\s+(\d{1,3}[A-Z]{0,2}(?:\(\d+[a-zA-Z]?\))*)\s*$""",
            RegexOption.IGNORE_CASE
        )

        for (line in lines) {
            val trimmed = line.trim()
            val containsActKeyword = actKeywords.any { kw ->
                trimmed.contains(kw, ignoreCase = true)
            }
            if (containsActKeyword) {
                val matchResult = sectionPattern.find(trimmed)
                if (matchResult != null) {
                    val secPart = matchResult.groupValues[1].trim()
                    val actPart = trimmed.substring(0, matchResult.range.first).trim()
                    val indent  = line.substringBefore(trimmed)
                    if (actPart.isNotBlank()) {
                        result.appendLine("$indent$actPart  |  $secPart")
                        continue
                    }
                }
            }
            result.appendLine(line)
        }
        return result.toString()
    }


    // ── Audio flow ────────────────────────────────────────────────────────────


    suspend fun transcribeOnly(): String {
        return stt.transcribeWithAndroidSTT()
    }

    fun analyseTranscript(transcript: String): Flow<Progress> = channelFlow {
        try {
            send(Progress.Status("Analysing with Gemma…"))
            val hints = regexExtract(transcript)
            var rawJson = ""
            llm.extractFirEntities(transcript, "audio") { token ->
                rawJson += token
                send(Progress.Token(token))
            }
            send(Progress.Status("Parsing results…"))
            val result = parseAndMerge(rawJson, hints)
            result.put("_transcript", transcript)
            send(Progress.Done(result))
        } catch (e: Exception) {
            send(Progress.Error(e.message ?: "Unknown error"))
        }
    }

    fun analyseFromAudio(onListening: () -> Unit): Flow<Progress> = flow {
        try {
            emit(Progress.Status("Listening… speak now"))
            onListening()
            val transcript = stt.transcribeWithAndroidSTT()

            emit(Progress.Status("Transcript ready. Analysing with Gemma…"))
            val hints = regexExtract(transcript)

            var rawJson = ""
            llm.extractFirEntities(transcript, "audio") { token -> rawJson += token }

            val result = parseAndMerge(rawJson, hints)
            result.put("_transcript", transcript)

            emit(Progress.Done(result))

        } catch (e: Exception) {
            emit(Progress.Error(e.message ?: "Unknown error"))
        }
    }

    // ── JSON cleanup + regex hint merging ─────────────────────────────────────
    private fun parseAndMerge(rawJson: String, hints: Map<String, Any>): JSONObject {
        var clean = rawJson.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        if (!clean.startsWith("{")) {
            val idx = clean.indexOf('{')
            if (idx >= 0) clean = clean.substring(idx)
        }
        val lastBrace = clean.lastIndexOf('}')
        if (lastBrace >= 0) clean = clean.substring(0, lastBrace + 1)

        val result = try {
            JSONObject(clean)
        } catch (e: Exception) {
            Log.e("REPO", "JSON parse failed: ${e.message}")
            JSONObject().apply { put("parse_error", "Failed: ${e.message}") }
        }

        // Merge regex hints into top-level fields if LLM missed them
        mapOf(
            "fir_number"       to "fir_number",
            "police_station"   to "police_station",
            "district"         to "district",
            "date_of_incident" to "date_from",          // maps to occurrence.date_from
            "time_of_incident" to "time_from"
        ).forEach { (hintKey, _) ->
            if (hints.containsKey(hintKey)) {
                // Try to patch into occurrence sub-object if relevant
                if (hintKey == "date_of_incident") {
                    val occ = result.optJSONObject("occurrence") ?: org.json.JSONObject().also { result.put("occurrence", it) }
                    if (occ.optString("date_from", "").isBlank()) occ.put("date_from", hints[hintKey])
                } else if (hintKey == "time_of_incident") {
                    val occ = result.optJSONObject("occurrence") ?: org.json.JSONObject().also { result.put("occurrence", it) }
                    if (occ.optString("time_from", "").isBlank()) occ.put("time_from", hints[hintKey])
                } else {
                    if (result.optString(hintKey, "").isBlank()) result.put(hintKey, hints[hintKey])
                }
            }
        }
        sanitiseOccurrence(result)
        return result
    }


    private fun sanitiseOccurrence(result: JSONObject) {
        val occ = result.optJSONObject("occurrence") ?: return

        val datePattern = Regex("""^\d{1,2}[/\-]\d{1,2}[/\-]\d{4}$""")
        val timePattern = Regex("""^\d{1,2}:\d{2}""")

        // If time_to contains a date value, move it to date_to
        val timeTo = occ.optString("time_to", "").trim()
        if (timeTo.isNotBlank() && datePattern.matches(timeTo)) {
            val dateTo = occ.optString("date_to", "").trim()
            if (dateTo.isBlank()) occ.put("date_to", timeTo)
            occ.put("time_to", JSONObject.NULL)
        }

        // Strip "hrs" suffix from time fields
        listOf("time_from", "time_to").forEach { key ->
            val v = occ.optString(key, "").trim()
            if (v.isNotBlank() && v != "null") {
                val cleaned = v.replace(Regex("""(?i)\s*hrs?\.?\s*$"""), "").trim()
                if (cleaned != v) occ.put(key, cleaned)
            }
        }

        // If date_from accidentally got a time value, swap it
        val dateFrom = occ.optString("date_from", "").trim()
        if (dateFrom.isNotBlank() && timePattern.matches(dateFrom)) {
            val timeFrom = occ.optString("time_from", "").trim()
            if (timeFrom.isBlank()) occ.put("time_from", dateFrom)
            occ.put("date_from", JSONObject.NULL)
        }
    }

    // ── Regex pre-extraction ──────────────────────────────────────────────────

    private fun regexExtract(text: String): Map<String, Any> {
        val hints = mutableMapOf<String, Any>()

        val dates = Regex("""\b(\d{1,2}[/\-]\d{1,2}[/\-]\d{4})\b""").findAll(text).toList()
        if (dates.isNotEmpty()) hints["date_of_incident"] = dates.first().value

        val timeRegex = Regex("""\b(\d{1,2}:\d{2})(?:\s*[Hh][Rr][Ss]?)?\b""")
        val times = timeRegex.findAll(text)
            .filter { m ->
                val before = text.substring(maxOf(0, m.range.first - 3), m.range.first)
                !before.contains("/") && !before.contains("-")
            }
            .toList()
        if (times.isNotEmpty()) hints["time_of_incident"] = times.first().value

        Regex("""(?:FIR\s*No\.?|प्र\.सू\.रि\.सं\.)\s*[:\-]?\s*(\d{3,6})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.let { hints["fir_number"] = it }

        val sections = mutableListOf<String>()
        Regex(
            """(?:u/s|section|dhara|धारा)\s+(\d{2,3}(?:\(\d+\))?(?:\s*[,/]\s*\d{2,3}(?:\(\d+\))?)*)""",
            RegexOption.IGNORE_CASE
        ).findAll(text).forEach { m ->
            m.groupValues[1].split(Regex("[,/\\s]+")).forEach { s ->
                val t = s.trim()
                if (t.matches(Regex("""\d{2,3}(\(\d+\))?"""))) sections.add(t)
            }
        }
        if (sections.isNotEmpty()) hints["ipc_sections"] = sections

        Regex("""\b([6-9]\d{9})\b""").find(text)?.groupValues?.get(1)?.let {
            hints["contact"] = it
        }

        return hints
    }
    fun release() {
        ocr.release()
        llm.release()
    }
}

