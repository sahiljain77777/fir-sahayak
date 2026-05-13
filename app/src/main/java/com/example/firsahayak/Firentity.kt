package com.example.firsahayak

import org.json.JSONArray
import org.json.JSONObject

/**
 * Strongly-typed data model that mirrors every field in the official BNS FIR form.
 * Used for verification UI and PDF auto-fill.
 */
data class FirEntity(
    // ── Header ────────────────────────────────────────────────────────────────
    val district: String = "",
    val year: String = "",
    val policeStation: String = "",
    val firNumber: String = "",
    val dateOfFiling: String = "",
    val timeOfFiling: String = "",

    // ── Acts & Sections ───────────────────────────────────────────────────────
    val acts: List<ActEntry> = emptyList(),

    // ── Occurrence ────────────────────────────────────────────────────────────
    val occurrence: OccurrenceDetails = OccurrenceDetails(),

    // ── Type of Information ───────────────────────────────────────────────────
    val typeOfInformation: String = "",   // Written | Oral | Telephone

    // ── Place of Occurrence ───────────────────────────────────────────────────
    val place: PlaceOfOccurrence = PlaceOfOccurrence(),

    // ── Complainant ───────────────────────────────────────────────────────────
    val complainant: Complainant = Complainant(),

    // ── Accused ───────────────────────────────────────────────────────────────
    val accused: List<AccusedEntry> = emptyList(),

    // ── Witnesses ─────────────────────────────────────────────────────────────
    val witnesses: List<String> = emptyList(),

    // ── Delay reason ─────────────────────────────────────────────────────────
    val delayReason: String = "",

    // ── Property ─────────────────────────────────────────────────────────────
    val propertyStolen: List<PropertyEntry> = emptyList(),
    val totalPropertyValueRs: String = "",

    // ── FIR Contents ─────────────────────────────────────────────────────────
    val firContents: String = "",

    // ── Severity ─────────────────────────────────────────────────────────────
    val severity: SeverityInfo = SeverityInfo(),

    // ── Meta ─────────────────────────────────────────────────────────────────
    val uncertainFields: List<String> = emptyList(),
    val languageDetected: String = "",
    val transcript: String = ""
)

data class ActEntry(
    val actName: String = "",
    val sections: List<String> = emptyList()
)

data class OccurrenceDetails(
    val dayType: String = "",            // INTERVENING DAY / DAY / NIGHT
    val dateFrom: String = "",
    val dateTo: String = "",
    val timeFrom: String = "",
    val timeTo: String = "",
    val infoReceivedAtPsDate: String = "",
    val infoReceivedAtPsTime: String = "",
    val gdEntryNumber: String = "",
    val gdEntryDatetime: String = ""
)

data class PlaceOfOccurrence(
    val directionFromPs: String = "",
    val distanceFromPsKm: String = "",
    val address: String = "",
    val beatNumber: String = "",
    val outsidePsLimit: Boolean = false,
    val outsidePsName: String = "",
    val outsideDistrict: String = ""
)

data class Complainant(
    val name: String = "",
    val fathersOrHusbandsName: String = "",
    val dateOfBirth: String = "",
    val nationality: String = "INDIA",
    val passportNumber: String = "",
    val passportIssueDate: String = "",
    val passportIssuePlace: String = "",
    val occupation: String = "",
    val address: String = "",
    val contact: String = ""
)

data class AccusedEntry(
    val name: String = "",
    val fathersName: String = "",
    val address1: String = "",
    val address2: String = ""
)

data class PropertyEntry(
    val slNo: String = "",
    val description: String = "",
    val estimatedValueRs: String = ""
)

data class SeverityInfo(
    val level: String = "low",
    val cognisable: Boolean = false,
    val bailable: Boolean = true,
    val urgencyScore: Int = 0,
    val reasoning: String = ""
)

// ── Extension: parse JSONObject → FirEntity ───────────────────────────────────

fun JSONObject.toFirEntity(transcript: String = "", source: String = "audio"): FirEntity {
    fun s(key: String) = optString(key, "").let { if (it == "null") "" else it }
    fun jo(key: String) = optJSONObject(key) ?: JSONObject()
    fun ja(key: String) = optJSONArray(key) ?: JSONArray()

    // Acts — parse raw acts from Gemma, then remap any IPC sections → BNS 2023
    val rawActs = mutableListOf<ActEntry>()
    val actsArr = ja("acts")
    for (i in 0 until actsArr.length()) {
        val a = actsArr.optJSONObject(i) ?: continue
        val secs = mutableListOf<String>()
        val secsArr = a.optJSONArray("sections") ?: JSONArray()
        for (j in 0 until secsArr.length()) {
            val rawSection = secsArr.optString(j)
            val cleanedSection = rawSection
                .replace(Regex("(?i)(BNS|IPC|u/s|section|sec\\.|s\\.)"), "")
                .replace("\\s".toRegex(), "")
                .uppercase()
                .trim()

            if (cleanedSection.isNotBlank()) {
                secs.add(cleanedSection)
            }
        }
        rawActs.add(ActEntry(actName = a.optString("act_name", ""), sections = secs))
    }
    // Also collect any legacy flat ipc_sections array Gemma may return
    val legacySecs = mutableListOf<String>()
    val ipcArr = ja("ipc_sections")
    for (i in 0 until ipcArr.length()) legacySecs.add(ipcArr.optString(i))


    val actsToProcess = if (rawActs.isNotEmpty()) rawActs
    else if (legacySecs.isNotEmpty()) listOf(ActEntry("THE BHARATIYA NYAYA SANHITA (BNS), 2023", legacySecs))
    else emptyList()

    // ── KEY CHANGE: skip IPC→BNS remapping for PDF, only do it for audio ──
    val acts: List<ActEntry>
    val extraUncertain: List<String>

    if (source == "pdf") {
        // PDF: sections are already in correct acts — preserve them as-is.
        // Only remap acts that are explicitly IPC (shouldn't happen in BNS-era FIRs
        // but handle defensively).
        val finalActs = mutableListOf<ActEntry>()
        val unknownSecs = mutableListOf<String>()

        for (act in actsToProcess) {
            val isBns = act.actName.contains("BNS", ignoreCase = true) ||
                    act.actName.contains("भारतीय न्याय संहिता", ignoreCase = true) ||
                    act.actName.contains("Bharatiya Nyaya Sanhita", ignoreCase = true)
            val isOtherKnownAct = OtherActsSeverityDb.isNonBnsAct(act.actName)

            when {
                isBns || isOtherKnownAct -> {
                    // Keep as-is — sections belong to this act
                    finalActs.add(act)
                }
                else -> {
                    // Unknown act name — keep but flag
                    finalActs.add(act)
                    unknownSecs.addAll(act.sections.map { "section:$it in unknown act '${act.actName}'" })
                }
            }
        }
        acts = finalActs
        extraUncertain = unknownSecs
    } else {
        // Audio: Gemma may return IPC sections — remap to BNS first
        acts = actsToProcess
        extraUncertain = emptyList()
    }





    // Occurrence
    val occ = jo("occurrence")
    val occurrence = OccurrenceDetails(
        dayType = occ.optString("day_type", "").nullToEmpty(),
        dateFrom = occ.optString("date_from", "").nullToEmpty(),
        dateTo = occ.optString("date_to", "").nullToEmpty(),
        timeFrom = occ.optString("time_from", "").nullToEmpty(),
        timeTo = occ.optString("time_to", "").nullToEmpty(),
        infoReceivedAtPsDate = occ.optString("info_received_at_ps_date", "").nullToEmpty(),
        infoReceivedAtPsTime = occ.optString("info_received_at_ps_time", "").nullToEmpty(),
        gdEntryNumber = occ.optString("gd_entry_number", "").nullToEmpty(),
        gdEntryDatetime = occ.optString("gd_entry_datetime", "").nullToEmpty()
    )

    // Place
    val pl = jo("place_of_occurrence")
    val place = PlaceOfOccurrence(
        directionFromPs = pl.optString("direction_from_ps", "").nullToEmpty().uppercase(),
        distanceFromPsKm = pl.optString("distance_from_ps_km", "").nullToEmpty(),
        address = pl.optString("address", "").nullToEmpty().uppercase(),
        beatNumber = pl.optString("beat_number", "").nullToEmpty(),
        outsidePsLimit = pl.optBoolean("outside_ps_limit", false),
        outsidePsName = pl.optString("outside_ps_name", "").nullToEmpty(),
        outsideDistrict = pl.optString("outside_district", "").nullToEmpty()
    )

    // Complainant
    val c = jo("complainant")
    val complainant = Complainant(
        name = c.optString("name", "").nullToEmpty().uppercase(),
        fathersOrHusbandsName = c.optString("fathers_or_husbands_name", "").nullToEmpty().uppercase(),
        dateOfBirth = c.optString("date_of_birth", "").nullToEmpty(),
        nationality = c.optString("nationality", "INDIA").nullToEmpty().ifEmpty { "INDIA" }.uppercase(),
        passportNumber = c.optString("passport_number", "").nullToEmpty(),
        passportIssueDate = c.optString("passport_issue_date", "").nullToEmpty(),
        passportIssuePlace = c.optString("passport_issue_place", "").nullToEmpty(),
        occupation = c.optString("occupation", "").nullToEmpty().uppercase(),
        address = c.optString("address", "").nullToEmpty().uppercase(),
        contact = c.optString("contact", "").nullToEmpty()
    )

    // Accused
    val accusedList = mutableListOf<AccusedEntry>()
    val accArr = ja("accused")
    for (i in 0 until accArr.length()) {
        val a = accArr.optJSONObject(i) ?: continue
        accusedList.add(AccusedEntry(
            name = a.optString("name", "").nullToEmpty().uppercase(),
            fathersName = a.optString("fathers_name", "").nullToEmpty().uppercase(),
            address1 = a.optString("address_1", "").nullToEmpty().uppercase(),
            address2 = a.optString("address_2", "").nullToEmpty().uppercase()
        ))
    }

    // Witnesses
    val witList = mutableListOf<String>()
    val witArr = ja("witnesses")
    for (i in 0 until witArr.length()) {
        witArr.optJSONObject(i)?.optString("name", "")?.nullToEmpty()?.let { if (it.isNotBlank()) witList.add(it) }
    }

    // Property
    val propList = mutableListOf<PropertyEntry>()
    val propArr = ja("property_stolen")
    for (i in 0 until propArr.length()) {
        val p = propArr.optJSONObject(i) ?: continue
        propList.add(PropertyEntry(
            slNo = p.optString("sl_no", "${i + 1}").nullToEmpty(),
            description = p.optString("description", "").nullToEmpty(),
            estimatedValueRs = p.optString("estimated_value_rs", "").nullToEmpty()
        ))
    }

    // Uncertain fields — merge Gemma list + any sections BNS mapper could not resolve
    val uncList = mutableListOf<String>()
    val uncArr = ja("uncertain_fields")
    for (i in 0 until uncArr.length()) uncArr.optString(i).let { if (it.isNotBlank()) uncList.add(it) }
    uncList.addAll(extraUncertain.map { "section:$it (unknown BNS mapping)" })

    // ── Severity: computed locally from BnsSeverityDb — overrides Gemma's score ──
    // Gemma's severity is unreliable (e.g. 318(4)+336(3)+340(2)+61(2) scored 3/10).
    // We extract all BNS sections from converted acts and compute rule-based severity.
    uncList.addAll(extraUncertain.map { "section:$it (Unknown BNS mapping)" })

    val allBnsSections = acts.flatMap { it.sections }
    val dbResult = BnsSeverityDb.computeSeverity(allBnsSections)

    // Build human-readable reasoning from matched DB entries
    val dbReasoning = if (dbResult.matchedEntries.isNotEmpty()) {
        dbResult.matchedEntries.joinToString("; ") { "BNS ${it.section}: ${it.shortTitle}" }
    } else {
        jo("severity").optString("reasoning", "").nullToEmpty()
    }

    val severity = SeverityInfo(
        level        = BnsSeverityDb.levelName(dbResult.level),
        cognisable   = dbResult.cognisable,
        bailable     = dbResult.bailable,
        urgencyScore = dbResult.urgencyScore,
        reasoning    = dbReasoning
    )

    return FirEntity(
        district = s("district"),
        year = s("year"),
        policeStation = s("police_station"),
        firNumber = s("fir_number"),
        dateOfFiling = s("date_of_filing"),
        timeOfFiling = s("time_of_filing"),
        acts = acts,
        occurrence = occurrence,
        typeOfInformation = s("type_of_information"),
        place = place,
        complainant = complainant,
        accused = accusedList,
        witnesses = witList,
        delayReason = s("delay_reason"),
        propertyStolen = propList,
        totalPropertyValueRs = s("total_property_value_rs"),
        firContents = s("fir_contents"),
        severity = severity,
        uncertainFields = uncList,
        languageDetected = s("language_detected"),
        transcript = transcript
    )
}

private fun String.nullToEmpty() = if (this == "null" || this == "N/A" || this == "n/a") "" else this

// ── Extension: FirEntity → JSONObject (for re-serialization) ─────────────────

fun FirEntity.toJsonObject(): JSONObject {
    val root = JSONObject()

    root.put("district", district)
    root.put("year", year)
    root.put("police_station", policeStation)
    root.put("fir_number", firNumber)
    root.put("date_of_filing", dateOfFiling)
    root.put("time_of_filing", timeOfFiling)

    val actsArr = JSONArray()
    acts.forEach { act ->
        val ao = JSONObject()
        ao.put("act_name", act.actName)
        val secsArr = JSONArray()
        act.sections.forEach { secsArr.put(it) }
        ao.put("sections", secsArr)
        actsArr.put(ao)
    }
    root.put("acts", actsArr)

    val occ = JSONObject().apply {
        put("day_type", occurrence.dayType)
        put("date_from", occurrence.dateFrom)
        put("date_to", occurrence.dateTo)
        put("time_from", occurrence.timeFrom)
        put("time_to", occurrence.timeTo)
        put("info_received_at_ps_date", occurrence.infoReceivedAtPsDate)
        put("info_received_at_ps_time", occurrence.infoReceivedAtPsTime)
        put("gd_entry_number", occurrence.gdEntryNumber)
        put("gd_entry_datetime", occurrence.gdEntryDatetime)
    }
    root.put("occurrence", occ)

    root.put("type_of_information", typeOfInformation)

    val pl = JSONObject().apply {
        put("direction_from_ps", place.directionFromPs)
        put("distance_from_ps_km", place.distanceFromPsKm)
        put("address", place.address)
        put("beat_number", place.beatNumber)
        put("outside_ps_limit", place.outsidePsLimit)
        put("outside_ps_name", place.outsidePsName)
        put("outside_district", place.outsideDistrict)
    }
    root.put("place_of_occurrence", pl)

    val comp = JSONObject().apply {
        put("name", complainant.name)
        put("fathers_or_husbands_name", complainant.fathersOrHusbandsName)
        put("date_of_birth", complainant.dateOfBirth)
        put("nationality", complainant.nationality)
        put("passport_number", complainant.passportNumber)
        put("passport_issue_date", complainant.passportIssueDate)
        put("passport_issue_place", complainant.passportIssuePlace)
        put("occupation", complainant.occupation)
        put("address", complainant.address)
        put("contact", complainant.contact)
    }
    root.put("complainant", comp)

    val accArr = JSONArray()
    accused.forEach { acc ->
        accArr.put(JSONObject().apply {
            put("name", acc.name)
            put("fathers_name", acc.fathersName)
            put("address_1", acc.address1)
            put("address_2", acc.address2)
        })
    }
    root.put("accused", accArr)

    val witArr = JSONArray()
    witnesses.forEach { witArr.put(JSONObject().apply { put("name", it) }) }
    root.put("witnesses", witArr)

    root.put("delay_reason", delayReason)

    val propArr = JSONArray()
    propertyStolen.forEach { p ->
        propArr.put(JSONObject().apply {
            put("sl_no", p.slNo)
            put("description", p.description)
            put("estimated_value_rs", p.estimatedValueRs)
        })
    }
    root.put("property_stolen", propArr)
    root.put("total_property_value_rs", totalPropertyValueRs)
    root.put("fir_contents", firContents)

    val sev = JSONObject().apply {
        put("level", severity.level)
        put("cognisable", severity.cognisable)
        put("bailable", severity.bailable)
        put("urgency_score", severity.urgencyScore)
        put("reasoning", severity.reasoning)
    }
    root.put("severity", sev)

    val uncArr = JSONArray()
    uncertainFields.forEach { uncArr.put(it) }
    root.put("uncertain_fields", uncArr)
    root.put("language_detected", languageDetected)

    return root
}