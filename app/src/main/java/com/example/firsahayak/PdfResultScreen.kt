package com.example.firsahayak

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.io.File

private val BgColor      = Color(0xFFF5F2EB)
private val SurfaceColor = Color(0xFFFFFFFF)
private val BorderColor  = Color(0xFFD4CFC4)
private val TextColor    = Color(0xFF1A1814)
private val MutedColor   = Color(0xFF6B6457)
private val AccentBlue   = Color(0xFF1A3A5C)
private val AmberColor   = Color(0xFFB85C00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfResultScreen(
    result : JSONObject,
    onBack : () -> Unit
) {
    val entity = result.toFirEntity()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { /* ... Header Title ... */ },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Back", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AccentBlue)
            )
        },
        containerColor = BgColor
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionBreakdownCard(entity)

                // Case identifiers
                ResultCard("Case Identifiers") {
                    FieldRow("FIR Number",     entity.firNumber)
                    FieldRow("Police Station", entity.policeStation)
                    FieldRow("District",       entity.district)
                    FieldRow("Year",           entity.year)
                }

                // Timeline
                ResultCard("Timeline") {
                    FieldRow("Date of Filing",   entity.dateOfFiling)
                    FieldRow("Time of Filing",   entity.timeOfFiling)
                    FieldRow("Date of Incident", entity.occurrence.dateFrom)
                    FieldRow("Time of Incident", entity.occurrence.timeFrom)
                    FieldRow("Day Type",         entity.occurrence.dayType)
                }

                // Complainant
                ResultCard("Complainant") {
                    FieldRow("Name",       entity.complainant.name)
                    FieldRow("Date/Year of Birth",        entity.complainant.dateOfBirth)
                    FieldRow("Occupation", entity.complainant.occupation)
                    FieldRow("Address",    entity.complainant.address)
                    FieldRow("Contact",    entity.complainant.contact)
                }

                // Place
                ResultCard("Place of Occurrence") {
                    FieldRow("Address", entity.place.address)
                    FieldRow("Direction / Distance",
                        listOf(entity.place.directionFromPs, entity.place.distanceFromPsKm)
                            .filter { it.isNotBlank() }.joinToString(", "))
                    FieldRow("Beat No.", entity.place.beatNumber)
                }

                // Accused
                if (entity.accused.isNotEmpty()) {
                    ResultCard("Accused (${entity.accused.size})") {
                        entity.accused.forEachIndexed { i, acc ->
                            AccusedItem(i + 1, acc.name, null, acc.address1)
                            if (i < entity.accused.lastIndex) Spacer(Modifier.height(6.dp))
                        }
                    }
                }

                // FIR Contents
                if (entity.firContents.isNotBlank()) {
                    ResultCard("FIR Contents") {
                        Text(entity.firContents,
                            fontSize = 13.sp,
                            color = TextColor,
                            lineHeight = 20.sp)
                    }
                }

                // Uncertain fields warning
                if (entity.uncertainFields.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFDF6EC))
                            .border(1.dp, Color(0xFFF0D9B5), RoundedCornerShape(6.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("⚠  Fields requiring manual verification",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = AmberColor)
                            Text(entity.uncertainFields.joinToString(", "),
                                fontSize = 11.sp,
                                color = AmberColor)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            // Simplified Bottom Action Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceColor,
                shadowElevation = 8.dp
            ) {
                PaddingValues(16.dp)
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("CLOSE AND ANALYSE ANOTHER",
                        fontFamily    = FontFamily.Monospace,
                        fontWeight    = FontWeight.Bold,
                        fontSize      = 12.sp,
                        color         = Color.White,
                        letterSpacing = 0.5.sp)
                }
            }
        }
    }
}