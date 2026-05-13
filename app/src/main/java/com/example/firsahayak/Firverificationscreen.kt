package com.example.firsahayak

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Colour palette (keep in sync with MainActivity) ───────────────────────────
private val BgColor      = Color(0xFFF5F2EB)
private val SurfaceColor = Color(0xFFFFFFFF)
private val BorderColor  = Color(0xFFD4CFC4)
private val TextColor    = Color(0xFF1A1814)
private val MutedColor   = Color(0xFF6B6457)
private val AccentRed    = Color(0xFFC0392B)
private val AccentBlue   = Color(0xFF1A3A5C)
private val GreenColor   = Color(0xFF1A6B3A)
private val AmberColor   = Color(0xFFB85C00)
private val WarningBg    = Color(0xFFFDF6EC)
private val WarningBorder = Color(0xFFF0D9B5)

/**
 * Full-screen verification step shown after Gemma extracts entities.
 * Every field is editable. Fields flagged as uncertain are highlighted.
 * When user taps "Confirm & Generate FIR PDF" the confirmed entity is returned.
 */
@Composable
fun FirVerificationScreen(
    initial: FirEntity,
    onConfirm: (FirEntity) -> Unit,
    onBack: () -> Unit
) {
    // ── Mutable state for every field ─────────────────────────────────────────
    var district       by remember { mutableStateOf(initial.district) }
    var year           by remember { mutableStateOf(initial.year) }
    var policeStation  by remember { mutableStateOf(initial.policeStation) }
    var firNumber      by remember { mutableStateOf(initial.firNumber) }
    var dateOfFiling   by remember { mutableStateOf(initial.dateOfFiling) }
    var timeOfFiling   by remember { mutableStateOf(initial.timeOfFiling) }

    // Acts — editable as flat string "ACT_NAME: sec1,sec2"
    var actsText by remember {
        mutableStateOf(initial.acts.joinToString("\n") { a ->
            "${a.actName}: ${a.sections.joinToString(", ")}"
        })
    }

    // Occurrence
    var dayType        by remember { mutableStateOf(initial.occurrence.dayType) }
    var dateFrom       by remember { mutableStateOf(initial.occurrence.dateFrom) }
    var dateTo         by remember { mutableStateOf(initial.occurrence.dateTo) }
    var timeFrom       by remember { mutableStateOf(initial.occurrence.timeFrom) }
    var timeTo         by remember { mutableStateOf(initial.occurrence.timeTo) }
    var infoDate       by remember { mutableStateOf(initial.occurrence.infoReceivedAtPsDate) }
    var infoTime       by remember { mutableStateOf(initial.occurrence.infoReceivedAtPsTime) }
    var gdNumber       by remember { mutableStateOf(initial.occurrence.gdEntryNumber) }
    var gdDatetime     by remember { mutableStateOf(initial.occurrence.gdEntryDatetime) }

    var typeOfInfo     by remember { mutableStateOf(initial.typeOfInformation) }

    // Place
    var direction      by remember { mutableStateOf(initial.place.directionFromPs) }
    var distance       by remember { mutableStateOf(initial.place.distanceFromPsKm) }
    var placeAddress   by remember { mutableStateOf(initial.place.address) }
    var beatNumber     by remember { mutableStateOf(initial.place.beatNumber) }

    // Complainant
    var compName       by remember { mutableStateOf(initial.complainant.name) }
    var compFather     by remember { mutableStateOf(initial.complainant.fathersOrHusbandsName) }
    var compDob        by remember { mutableStateOf(initial.complainant.dateOfBirth) }
    var compNat        by remember { mutableStateOf(initial.complainant.nationality) }
    var compPassport   by remember { mutableStateOf(initial.complainant.passportNumber) }
    var compOccupation by remember { mutableStateOf(initial.complainant.occupation) }
    var compAddress    by remember { mutableStateOf(initial.complainant.address) }
    var compContact    by remember { mutableStateOf(initial.complainant.contact) }

    // Accused — list of mutable states
    var accusedList    by remember { mutableStateOf(
        if (initial.accused.isEmpty()) listOf(AccusedEntry()) else initial.accused
    ) }

    // Witnesses
    var witnessesText  by remember { mutableStateOf(initial.witnesses.joinToString("\n")) }

    // Delay
    var delayReason    by remember { mutableStateOf(initial.delayReason.ifEmpty { "NO DELAY" }) }

    // Property
    var propertyList   by remember { mutableStateOf(
        if (initial.propertyStolen.isEmpty()) listOf(PropertyEntry()) else initial.propertyStolen
    ) }
    var totalValue     by remember { mutableStateOf(initial.totalPropertyValueRs) }

    // FIR Contents
    var firContents    by remember { mutableStateOf(initial.firContents) }

    val uncertain = initial.uncertainFields.toSet()

    // ─────────────────────────────────────────────────────────────────────────

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
            Column {
                Text("Verify Extracted Data", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Review every field. Edit anything incorrect. Fields with ⚠ need attention.",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // ── Transcript (read-only) ─────────────────────────────────────────
        if (initial.transcript.isNotBlank()) {
            VerifySection("Audio Transcript") {
                Text(
                    initial.transcript,
                    fontSize = 13.sp, color = MutedColor,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(BgColor)
                        .padding(10.dp)
                )
            }
        }

        // ── Case Header ───────────────────────────────────────────────────────
        VerifySection("1. Case Identifiers") {
            VerifyField("District", district, uncertain.contains("district")) { district = it }
            VerifyField("Year", year, uncertain.contains("year")) { year = it }
            VerifyField("Police Station (P.S.)", policeStation, uncertain.contains("police_station")) { policeStation = it }
            VerifyField("FIR Number", firNumber, uncertain.contains("fir_number")) { firNumber = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    VerifyField("Date of Filing", dateOfFiling, uncertain.contains("date_of_filing")) { dateOfFiling = it }
                }
                Box(Modifier.weight(1f)) {
                    VerifyField("Time of Filing", timeOfFiling, uncertain.contains("time_of_filing")) { timeOfFiling = it }
                }
            }
        }

        // ── Acts & Sections ───────────────────────────────────────────────────
        VerifySection("2. Act(s) & Sections") {
            Text(
                "Format: ACT NAME: section1, section2 (one act per line)",
                fontSize = 10.sp, color = MutedColor, fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = actsText,
                onValueChange = { actsText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                label = { Text("Acts & Sections") },
                colors = verifyFieldColors(uncertain.contains("ipc_sections") || uncertain.contains("acts"))
            )
        }

        // ── Occurrence ────────────────────────────────────────────────────────
        VerifySection("3. Occurrence of Offence") {
            // Day type selector
            Text("Day Type", fontSize = 11.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DAY", "NIGHT", "INTERVENING DAY").forEach { option ->
                    FilterChip(
                        selected = dayType == option,
                        onClick = { dayType = option },
                        label = { Text(option, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    VerifyField("Date From", dateFrom, uncertain.contains("date_of_incident")) { dateFrom = it }
                }
                Box(Modifier.weight(1f)) {
                    VerifyField("Date To", dateTo, false) { dateTo = it }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    VerifyField("Time From", timeFrom, uncertain.contains("time_of_incident")) { timeFrom = it }
                }
                Box(Modifier.weight(1f)) {
                    VerifyField("Time To", timeTo, false) { timeTo = it }
                }
            }
            VerifyField("Info Received at P.S. — Date", infoDate, false) { infoDate = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    VerifyField("Info Received Time", infoTime, false) { infoTime = it }
                }
                Box(Modifier.weight(1f)) {
                    VerifyField("GD Entry No.", gdNumber, false) { gdNumber = it }
                }
            }
            VerifyField("GD Entry Date/Time", gdDatetime, false) { gdDatetime = it }
        }

        // ── Type of Information ───────────────────────────────────────────────
        VerifySection("4. Type of Information") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Written", "Oral", "Telephone").forEach { option ->
                    FilterChip(
                        selected = typeOfInfo.equals(option, ignoreCase = true),
                        onClick = { typeOfInfo = option },
                        label = { Text(option, fontSize = 11.sp) }
                    )
                }
            }
        }

        // ── Place of Occurrence ───────────────────────────────────────────────
        VerifySection("5. Place of Occurrence") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    VerifyField("Direction from P.S.", direction, uncertain.contains("incident_location")) { direction = it }
                }
                Box(Modifier.weight(1f)) {
                    VerifyField("Distance (km)", distance, false) { distance = it }
                }
            }
            VerifyField("Address", placeAddress, uncertain.contains("incident_location")) { placeAddress = it }
            VerifyField("Beat No.", beatNumber, false) { beatNumber = it }
        }

        // ── Complainant ───────────────────────────────────────────────────────
        VerifySection("6. Complainant / Informant") {
            VerifyField("Full Name", compName, uncertain.contains("complainant")) { compName = it }
            VerifyField("Father's / Husband's Name", compFather, false) { compFather = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    VerifyField("Date of Birth", compDob, false, keyboardType = KeyboardType.Number) { compDob = it }
                }
                Box(Modifier.weight(1f)) {
                    VerifyField("Nationality", compNat, false) { compNat = it }
                }
            }
            VerifyField("Passport No. (if any)", compPassport, false) { compPassport = it }
            VerifyField("Occupation", compOccupation, false) { compOccupation = it }
            VerifyField("Address", compAddress, uncertain.contains("complainant")) { compAddress = it }
            VerifyField("Contact / Mobile", compContact, false, keyboardType = KeyboardType.Phone) { compContact = it }
        }

        // ── Accused ───────────────────────────────────────────────────────────
        VerifySection("7. Accused Details") {
            accusedList.forEachIndexed { index, acc ->
                AccusedVerifyCard(
                    index = index,
                    entry = acc,
                    isUncertain = uncertain.contains("accused"),
                    onUpdate = { updated ->
                        accusedList = accusedList.toMutableList().also { it[index] = updated }
                    },
                    onRemove = if (accusedList.size > 1) ({
                        accusedList = accusedList.toMutableList().also { it.removeAt(index) }
                    }) else null
                )
                if (index < accusedList.lastIndex) Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { accusedList = accusedList + AccusedEntry() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Another Accused", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }

        // ── Witnesses ─────────────────────────────────────────────────────────
        VerifySection("8. Witnesses") {
            Text("One name per line", fontSize = 10.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = witnessesText,
                onValueChange = { witnessesText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                label = { Text("Witness Names") }
            )
        }

        // ── Reasons for delay ─────────────────────────────────────────────────
        VerifySection("8. Reason for Delay in Reporting") {
            VerifyField("Delay Reason", delayReason, false) { delayReason = it }
        }

        // ── Property ─────────────────────────────────────────────────────────
        VerifySection("9. Property Stolen / Involved") {
            propertyList.forEachIndexed { index, prop ->
                PropertyVerifyCard(
                    index = index,
                    entry = prop,
                    onUpdate = { updated ->
                        propertyList = propertyList.toMutableList().also { it[index] = updated }
                    },
                    onRemove = if (propertyList.size > 1) ({
                        propertyList = propertyList.toMutableList().also { it.removeAt(index) }
                    }) else null
                )
                if (index < propertyList.lastIndex) Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { propertyList = propertyList + PropertyEntry(slNo = "${propertyList.size + 1}") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Property Item", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            VerifyField(
                "Total Value of Property (Rs.)", totalValue, false,
                keyboardType = KeyboardType.Number
            ) { totalValue = it }
        }

        // ── FIR Contents ──────────────────────────────────────────────────────
        VerifySection("12. FIR Contents") {
            OutlinedTextField(
                value = firContents,
                onValueChange = { firContents = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                label = { Text("Incident Description / FIR Contents") },
                colors = verifyFieldColors(uncertain.contains("fir_contents"))
            )
        }

        // ── Action Buttons ────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceColor)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    onConfirm(buildFirEntity(
                        district, year, policeStation, firNumber, dateOfFiling, timeOfFiling,
                        actsText, dayType, dateFrom, dateTo, timeFrom, timeTo,
                        infoDate, infoTime, gdNumber, gdDatetime, typeOfInfo,
                        direction, distance, placeAddress, beatNumber,
                        compName, compFather, compDob, compNat, compPassport, compOccupation, compAddress, compContact,
                        accusedList, witnessesText, delayReason,
                        propertyList, totalValue, firContents,
                        initial.severity, initial.uncertainFields, initial.languageDetected, initial.transcript
                    ))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenColor)
            ) {
                Text(
                    "✓  CONFIRM & GENERATE FIR PDF",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("← Back to Analysis", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MutedColor)
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun VerifySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceColor)
            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            title.uppercase(), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = AccentBlue
        )
        HorizontalDivider(color = BorderColor)
        content()
    }
}

@Composable
private fun VerifyField(
    label: String,
    value: String,
    isUncertain: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    val warningLabel = if (isUncertain) "⚠ $label" else label
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(warningLabel, fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = verifyFieldColors(isUncertain)
    )
}

@Composable
private fun verifyFieldColors(isUncertain: Boolean) =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = if (isUncertain) AmberColor else AccentBlue,
        unfocusedBorderColor = if (isUncertain) AmberColor else BorderColor,
        focusedLabelColor = if (isUncertain) AmberColor else AccentBlue,
        unfocusedLabelColor = if (isUncertain) AmberColor else MutedColor,
        unfocusedContainerColor = if (isUncertain) WarningBg else Color.Transparent
    )

@Composable
private fun AccusedVerifyCard(
    index: Int,
    entry: AccusedEntry,
    isUncertain: Boolean,
    onUpdate: (AccusedEntry) -> Unit,
    onRemove: (() -> Unit)?
) {
    var name    by remember(entry) { mutableStateOf(entry.name) }
    var father  by remember(entry) { mutableStateOf(entry.fathersName) }
    var addr1   by remember(entry) { mutableStateOf(entry.address1) }
    var addr2   by remember(entry) { mutableStateOf(entry.address2) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(BgColor)
            .border(1.dp, if (isUncertain) AmberColor else BorderColor, RoundedCornerShape(6.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Accused ${index + 1}", fontWeight = FontWeight.Medium, color = TextColor, fontSize = 13.sp)
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = AccentRed, modifier = Modifier.size(18.dp))
                }
            }
        }
        OutlinedTextField(
            value = name, onValueChange = { name = it; onUpdate(entry.copy(name = it)) },
            label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            colors = verifyFieldColors(isUncertain)
        )
        OutlinedTextField(
            value = father, onValueChange = { father = it; onUpdate(entry.copy(fathersName = it)) },
            label = { Text("Father's Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = addr1, onValueChange = { addr1 = it; onUpdate(entry.copy(address1 = it)) },
            label = { Text("Address") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
    }
}

@Composable
private fun PropertyVerifyCard(
    index: Int,
    entry: PropertyEntry,
    onUpdate: (PropertyEntry) -> Unit,
    onRemove: (() -> Unit)?
) {
    var desc  by remember(entry) { mutableStateOf(entry.description) }
    var value by remember(entry) { mutableStateOf(entry.estimatedValueRs) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(BgColor)
            .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Property Item ${index + 1}", fontWeight = FontWeight.Medium, color = TextColor, fontSize = 13.sp)
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = AccentRed, modifier = Modifier.size(18.dp))
                }
            }
        }
        OutlinedTextField(
            value = desc, onValueChange = { desc = it; onUpdate(entry.copy(description = it)) },
            label = { Text("Description") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = value, onValueChange = { value = it; onUpdate(entry.copy(estimatedValueRs = it)) },
            label = { Text("Est. Value (Rs.)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

// ── Build final FirEntity from all editable state ─────────────────────────────

private fun buildFirEntity(
    district: String, year: String, policeStation: String, firNumber: String,
    dateOfFiling: String, timeOfFiling: String, actsText: String,
    dayType: String, dateFrom: String, dateTo: String, timeFrom: String, timeTo: String,
    infoDate: String, infoTime: String, gdNumber: String, gdDatetime: String,
    typeOfInfo: String, direction: String, distance: String, placeAddress: String, beatNumber: String,
    compName: String, compFather: String, compDob: String, compNat: String,
    compPassport: String, compOccupation: String, compAddress: String, compContact: String,
    accusedList: List<AccusedEntry>, witnessesText: String, delayReason: String,
    propertyList: List<PropertyEntry>, totalValue: String, firContents: String,
    severity: SeverityInfo, uncertainFields: List<String>, languageDetected: String, transcript: String
): FirEntity {
    // Parse acts from "ACT NAME: sec1, sec2" lines
    val acts = actsText.lines().filter { it.isNotBlank() }.map { line ->
        val parts = line.split(":", limit = 2)
        val actName = parts.getOrNull(0)?.trim() ?: ""
        val sections = parts.getOrNull(1)?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        ActEntry(actName = actName, sections = sections)
    }
    val allSections = acts.flatMap { it.sections }
    val dbResult = BnsSeverityDb.computeSeverity(allSections) // THIS WILL TRIGGER YOUR LOGS

    // 3. Create the updated SeverityInfo object
    val updatedSeverity = SeverityInfo(
        level = BnsSeverityDb.levelName(dbResult.level),
        cognisable = dbResult.cognisable,
        bailable = dbResult.bailable,
        urgencyScore = dbResult.urgencyScore,
        reasoning = dbResult.matchedEntries.joinToString("; ") { "BNS ${it.section}: ${it.shortTitle}" }
    )

    val witnesses = witnessesText.lines().map { it.trim() }.filter { it.isNotBlank() }

    return FirEntity(
        district = district, year = year, policeStation = policeStation,
        firNumber = firNumber, dateOfFiling = dateOfFiling, timeOfFiling = timeOfFiling,
        acts = acts,
        occurrence = OccurrenceDetails(dayType, dateFrom, dateTo, timeFrom, timeTo, infoDate, infoTime, gdNumber, gdDatetime),
        typeOfInformation = typeOfInfo,
        place = PlaceOfOccurrence(direction, distance, placeAddress, beatNumber),
        complainant = Complainant(compName, compFather, compDob, compNat, compPassport, "", "", compOccupation, compAddress, compContact),
        accused = accusedList,
        witnesses = witnesses,
        delayReason = delayReason,
        propertyStolen = propertyList,
        totalPropertyValueRs = totalValue,
        firContents = firContents,
        severity = updatedSeverity,
        uncertainFields = dbResult.unmatchedSecs.map { "Section $it not found in BNS DB" },
        languageDetected = languageDetected,
        transcript = transcript
    )
}