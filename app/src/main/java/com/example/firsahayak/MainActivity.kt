package com.example.firsahayak

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONObject
import com.example.firsahayak.ui.theme.FirSahayakTheme
import java.io.File

// ── Colour palette ────────────────────────────────────────────────────────────
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
 * MainActivity.kt — Single-activity entry point and Compose host.
 *
 * Responsibilities:
 *   • Registers ActivityResultLaunchers for PDF file picker and
 *     RECORD_AUDIO permission before the activity is created
 *   • Pre-warms ML Kit Latin and Devanagari recognisers on startup
 *     to reduce first-OCR latency
 *   • Hosts FirSahayakApp composable which routes between all screens
 *     based on FirViewModel.UiState
 *   • sharePdf() — exposes the generated PDF file via FileProvider
 *     using ACTION_SEND so it can be shared to any installed app
 *
 * Screen routing (in FirSahayakApp):
 *   TranscriptReview → full-screen takeover
 *   Verification     → full-screen takeover
 *   PdfReady         → full-screen takeover
 *   Success          → full-screen takeover (PDF path result)
 *   All other states → Scaffold with InputPanel + ResultPanel
 *
 * ResultPanel handles: Idle, NeedDownload, Loading, Downloading,
 * Streaming (live token display), Failure.
 */

class MainActivity : ComponentActivity() {

    private val viewModel: FirViewModel by viewModels()

    private val pdfPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.analyseFromPdf(it) } }

    private val audioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.analyseFromAudio()
        else viewModel.setError("Microphone permission denied. Please grant it in Settings.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Pre-warm ML Kit models
        com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
        )
        com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions.Builder().build()
        )

        viewModel.checkAndInitialize()

        setContent {
            FirSahayakTheme {
                FirSahayakApp(
                    viewModel        = viewModel,
                    onPickPdf        = { pdfPicker.launch("application/pdf") },
                    onStartAudio     = { audioPermission.launch(Manifest.permission.RECORD_AUDIO) },
                    onDownloadModel  = { viewModel.downloadModel(this@MainActivity) },
                    onSharePdf       = { file -> sharePdf(file) }
                )
            }
        }
    }

    private fun sharePdf(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share / Download FIR PDF"))
    }
}

// ════════════════════════════════════════════════════════════════════════════
// ROOT COMPOSABLE — routes between screens
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirSahayakApp(
    viewModel       : FirViewModel,
    onPickPdf       : () -> Unit,
    onStartAudio    : () -> Unit,
    onDownloadModel : () -> Unit,
    onSharePdf      : (File) -> Unit,
       // ADD THIS

) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    val activeInputTab by viewModel.activeInputTab.collectAsStateWithLifecycle()
    var selectedTab    by rememberSaveable { mutableStateOf(0) }


    // ── Sync tab with input source so audio stays on tab 1 ───────────────
    // When audio analysis starts, lock to tab 1
    LaunchedEffect(activeInputTab) {
        selectedTab = activeInputTab
    }

    if (uiState is FirViewModel.UiState.TranscriptReview) {
        val state = uiState as FirViewModel.UiState.TranscriptReview
        TranscriptReviewScreen(
            transcript = state.transcript,
            onConfirm  = { editedTranscript -> viewModel.confirmTranscript(editedTranscript) },
            onReRecord = { viewModel.reRecord() }
        )
        return
    }

    // ── Verification screen takes over the whole UI ───────────────────────────
    if (uiState is FirViewModel.UiState.Verification) {
        FirVerificationScreen(
            initial   = (uiState as FirViewModel.UiState.Verification).entity,
            onConfirm = { confirmedEntity -> viewModel.generatePdf(confirmedEntity) },
            onBack    = { viewModel.backFromVerification() }
        )
        return
    }

    // ── PDF Ready screen ──────────────────────────────────────────────────────
    if (uiState is FirViewModel.UiState.PdfReady) {
        val state = uiState as FirViewModel.UiState.PdfReady
        PdfReadyScreen(
            entity     = state.entity,
            pdfFile    = state.pdfFile,
            onDownload = { onSharePdf(state.pdfFile) },
            onReset    = { viewModel.reset() }
        )
        return
    }

    if (uiState is FirViewModel.UiState.Success) {
        val s = uiState as FirViewModel.UiState.Success
        PdfResultScreen(
            result      = s.result,
            onBack      = { viewModel.reset() }
        )
        return
    }




    Scaffold(
        topBar         = { FirTopBar() },
        containerColor = BgColor
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            InputPanel(
                selectedTab     = selectedTab,
                onTabChange     = { selectedTab = it },
                uiState         = uiState,
                onPickPdf       = onPickPdf,
                onStartAudio    = onStartAudio,
                onDownloadModel = onDownloadModel,
            )
            HorizontalDivider(color = BorderColor)
            ResultPanel(
                uiState   = uiState,
                modifier  = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                onSharePdf = onSharePdf
            )

        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// PDF READY SCREEN
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun PdfReadyScreen(
    entity: FirEntity,
    pdfFile: File,
    onDownload: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Success banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFD5F0DE))
                .border(1.dp, Color(0xFF90D4A8), RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("✓  FIR PDF Generated", fontWeight = FontWeight.Bold, color = GreenColor, fontSize = 16.sp)
                Text(
                    pdfFile.name,
                    fontSize = 11.sp,
                    color = GreenColor,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Quick summary card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceColor)
                .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("CASE SUMMARY", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = MutedColor)
            HorizontalDivider(color = BorderColor)
            SummaryRow("FIR No.", entity.firNumber)
            SummaryRow("Police Station", entity.policeStation)
            SummaryRow("District", entity.district)
            SummaryRow("Date of Filing", entity.dateOfFiling)
            SummaryRow("Complainant", entity.complainant.name)
            SummaryRow("Accused", entity.accused.firstOrNull()?.name ?: "—")
            SummaryRow("Acts & Sections", entity.acts.flatMap { it.sections }.joinToString(", ").ifBlank { "—" })

            val sev = entity.severity
            val sevColor = when (sev.level.lowercase()) {
                "critical" -> AccentRed
                "high"     -> AccentRed
                "medium"   -> AmberColor
                else       -> GreenColor
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Severity:", fontSize = 11.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(sevColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(sev.level.uppercase(), fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, color = sevColor,
                        fontFamily = FontFamily.Monospace)
                }
                Text("${sev.urgencyScore}/10", fontSize = 11.sp, color = MutedColor)
            }
        }

        // ── Section breakdown ─────────────────────────────────────────────────────
        SectionBreakdownCard(entity)

        // Download button
        Button(
            onClick  = onDownload,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape    = RoundedCornerShape(8.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = AccentBlue)
        ) {
            Text(
                "⬇  DOWNLOAD / SHARE FIR PDF",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize   = 13.sp,
                letterSpacing = 0.5.sp,
                color         = Color.White
            )
        }

        // Analyse another
        OutlinedButton(
            onClick  = onReset,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape    = RoundedCornerShape(8.dp)
        ) {
            Text("← Analyse Another Complaint", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MutedColor)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = MutedColor, modifier = Modifier.weight(0.4f))
        Text(
            value.ifBlank { "—" },
            fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextColor,
            modifier = Modifier.weight(0.6f)
        )
    }
}




// ════════════════════════════════════════════════════════════════════════════
// TOP BAR
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirTopBar() {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)).background(AccentRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text("FIR", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                }
                Column {
                    Text("FIR Sahayak", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("AI Legal Document Analyser", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = AccentBlue),
    )
}

// ════════════════════════════════════════════════════════════════════════════
// INPUT PANEL
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun InputPanel(
    selectedTab    : Int,
    onTabChange    : (Int) -> Unit,
    uiState        : FirViewModel.UiState,
    onPickPdf      : () -> Unit,
    onStartAudio   : () -> Unit,
    onDownloadModel: () -> Unit,
) {
    val isLoading = uiState is FirViewModel.UiState.Loading
            || uiState is FirViewModel.UiState.Streaming

    Column(
        modifier = Modifier.fillMaxWidth().background(SurfaceColor).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("INPUT MODE", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp, color = MutedColor)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabButton("PDF Document",  selected = selectedTab == 0, modifier = Modifier.weight(1f)) { onTabChange(0) }
            TabButton("Audio / Voice", selected = selectedTab == 1, modifier = Modifier.weight(1f)) { onTabChange(1) }
        }

        if (selectedTab == 0) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(BgColor).border(2.dp, BorderColor, RoundedCornerShape(8.dp))
                    .clickable(enabled = !isLoading) { onPickPdf() }.padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📄", fontSize = 32.sp)
                    Text("Tap to select PDF", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextColor)
                    Text("FIR, legal notices, charge sheets", fontSize = 12.sp, color = MutedColor)
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(BgColor).border(1.dp, BorderColor, RoundedCornerShape(8.dp)).padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(32.dp))
                            .background(if (isLoading) BorderColor else AccentRed)
                            .clickable(enabled = !isLoading) { onStartAudio() },
                        contentAlignment = Alignment.Center
                    ) { Text("🎙", fontSize = 26.sp) }
                    Text(if (isLoading) "Processing…" else "Tap to speak FIR complaint",
                        fontSize = 13.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
                    Text("Hindi / English / Hinglish supported", fontSize = 11.sp, color = MutedColor)
                }
            }
        }

        Button(
            onClick  = { if (selectedTab == 0) onPickPdf() else onStartAudio() },
            enabled  = !isLoading,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape    = RoundedCornerShape(6.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = AccentRed, disabledContainerColor = BorderColor)
        ) {
            Text(
                when { isLoading -> "ANALYSING…"; selectedTab == 0 -> "ANALYSE DOCUMENT"; else -> "ANALYSE AUDIO" },
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, fontSize = 13.sp
            )
        }

        if (uiState is FirViewModel.UiState.NeedDownload) {
            InfoBox(
                "Gemma model (~1.5 GB) is required for AI analysis. Tap Download to install it on your device. A Wi-Fi connection is recommended.",
                bgColor = Color(0xFFFDF6EC), borderColor = Color(0xFFF0D9B5), textColor = Color(0xFF7A4F00)
            )
            Button(
                onClick  = onDownloadModel,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = AmberColor),
                shape    = RoundedCornerShape(6.dp)
            ) { Text("DOWNLOAD GEMMA MODEL (~1.5 GB)", fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
        }

        InfoBox("On-Device · ML Kit OCR (Hindi/English) · Android STT · Gemma 4 E2B")
    }
}

// ════════════════════════════════════════════════════════════════════════════
// RESULT PANEL
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun ResultPanel(
    uiState: FirViewModel.UiState,
    modifier: Modifier = Modifier,
    onSharePdf: (File) -> Unit = {}
) {
    Box(modifier = modifier.padding(16.dp)) {
        when (uiState) {
            is FirViewModel.UiState.Idle,
            is FirViewModel.UiState.NeedDownload -> {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("⚖️", fontSize = 48.sp)
                    Text("No document analysed yet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MutedColor)
                    Text(
                        "Select a PDF or use voice input, then tap Analyse.\n\nFor audio: speak your FIR complaint in Hindi, English, or Hinglish. You'll be able to verify every extracted field before the PDF is generated.",
                        fontSize = 13.sp, color = MutedColor, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp), lineHeight = 20.sp
                    )
                }
            }

            is FirViewModel.UiState.Loading -> {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(44.dp))
                    Text(uiState.message, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                        color = MutedColor, textAlign = TextAlign.Center)
                    Text("This may take 30–120 seconds.", fontSize = 11.sp, color = MutedColor.copy(alpha = 0.6f))
                }
            }

            is FirViewModel.UiState.Downloading -> {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⬇️", fontSize = 40.sp)
                    Text("Downloading Gemma model…", fontWeight = FontWeight.Medium, color = TextColor)
                    LinearProgressIndicator(
                        progress = { uiState.percent / 100f },
                        modifier = Modifier.fillMaxWidth(0.8f), color = AccentBlue)
                    Text("${uiState.percent}%  ·  ~1.5 GB total",
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MutedColor)
                }
            }


            is FirViewModel.UiState.Streaming -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Status + spinner row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        CircularProgressIndicator(
                            color       = AccentBlue,
                            modifier    = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            uiState.message,
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 12.sp,
                            color      = MutedColor
                        )
                    }

                    // Token stream box — only shown when tokens exist
                    if (uiState.tokens.isNotBlank()) {
                        val tokenScrollState = rememberScrollState()

                        // Auto-scroll to bottom as tokens arrive
                        LaunchedEffect(uiState.tokens) {
                            tokenScrollState.animateScrollTo(tokenScrollState.maxValue)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1A1814))
                                .padding(12.dp)
                                .heightIn(min = 80.dp, max = 280.dp)
                                .verticalScroll(tokenScrollState)   // ← separate scroll state
                        ) {
                            Text(
                                uiState.tokens,
                                fontSize   = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color      = Color(0xFF00FF88),
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Text(
                        "Results will appear automatically when complete.",
                        fontSize   = 10.sp,
                        color      = MutedColor.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace,
                        modifier   = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            is FirViewModel.UiState.Failure -> {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFDE0DE)).border(1.dp, Color(0xFFF5B7B1), RoundedCornerShape(8.dp)).padding(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Error", fontWeight = FontWeight.Bold, color = AccentRed, fontSize = 16.sp)
                        Text(uiState.error, fontSize = 13.sp, color = AccentRed)
                    }
                }
            }

            // Legacy PDF analysis result (raw JSON view)
            is FirViewModel.UiState.Success ->
                FirResultView(result = uiState.result, transcript = uiState.transcript)

            // These two cases are handled above by full-screen takeover, but needed for exhaustive when
            is FirViewModel.UiState.Verification -> {}
            is FirViewModel.UiState.PdfReady     -> {}
            is FirViewModel.UiState.TranscriptReview -> {}
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// LEGACY RAW RESULT VIEW (PDF analysis)
// ════════════════════════════════════════════════════════════════════════════

fun String?.cleanTime(): String? {
    if (isNullOrBlank()) return null
    return this.replace(Regex("""(?i)\s*hrs?\.?\s*$"""), "").trim().ifBlank { null }
}
@Composable
fun FirResultView(result: JSONObject, transcript: String?) {
    // PDF path: use DB severity for header badge
    val sevLevel = result.toFirEntity().severity.level

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("FIR Analysis Results", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextColor)
            SeverityBadge(level = sevLevel)
        }

        if (!transcript.isNullOrBlank()) {
            ResultCard("Audio Transcript") {
                Text(transcript, fontSize = 13.sp, color = MutedColor, fontStyle = FontStyle.Italic, lineHeight = 20.sp)
            }
        }

        ResultCard("Case Identifiers") {
            FieldRow("FIR Number",     result.optString("fir_number"))
            FieldRow("Police Station", result.optString("police_station"))
            FieldRow("District",       result.optString("district"))
            FieldRow("State",          result.optString("state"))
        }

        val occ = result.optJSONObject("occurrence")
        if (occ != null) {
            ResultCard("Timeline") {
                FieldRow("Date From",      occ.optString("date_from"))
                FieldRow("Date To",        occ.optString("date_to"))
                FieldRow("Time From",      occ.optString("time_from").cleanTime())
                FieldRow("Time To",        occ.optString("time_to").cleanTime())
                FieldRow("Date of Filing", result.optString("date_of_filing"))
                FieldRow("Time of Filing", result.optString("time_of_filing").cleanTime())
            }
        }

        val comp = result.optJSONObject("complainant")
        ResultCard("Complainant") {
            FieldRow("Name",    comp?.optString("name"))
            FieldRow("Age / DOB / Year of Birth", comp?.optString("date_of_birth"))
            FieldRow("Address", comp?.optString("address"))
            FieldRow("Contact", comp?.optString("contact"))
        }

        val pl = result.optJSONObject("place_of_occurrence")
        ResultCard("Location & Officer") {
            FieldRow("Address", pl?.optString("address"))
            FieldRow("Direction from P.S.", pl?.optString("direction_from_ps"))
            FieldRow("Distance", pl?.optString("distance_from_ps_km")?.let { "$it km" })
        }

        // ── Section Breakdown Card (DB-computed severity + per-section definitions) ──
        // PDF path: DB-computed severity for section breakdown card
        val entity = result.toFirEntity(source="pdf")
        SectionBreakdownCard(entity)

        val accusedArr = result.optJSONArray("accused")
        if (accusedArr != null && accusedArr.length() > 0) {
            ResultCard("Accused (${accusedArr.length()})") {
                (0 until accusedArr.length()).forEach { i ->
                    val a = accusedArr.optJSONObject(i)
                    AccusedItem(i + 1, a?.optString("name")?.takeIf { it.isNotBlank() } ?: "Unknown", null, a?.optString("address_1"))
                    if (i < accusedArr.length() - 1) Spacer(Modifier.height(6.dp))
                }
            }
        }

        result.optString("fir_contents").takeIf { it.isNotBlank() }?.let { summary ->
            ResultCard("FIR Contents") {
                Text(summary, fontSize = 14.sp, color = TextColor, lineHeight = 22.sp)
            }
        }

        // Severity is now shown inside SectionBreakdownCard above

        Spacer(Modifier.height(24.dp))
    }
}

// ════════════════════════════════════════════════════════════════════════════
// REUSABLE COMPOSABLES
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun TabButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(38.dp), shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AccentBlue else SurfaceColor,
            contentColor   = if (selected) Color.White else MutedColor),
        border = if (!selected) ButtonDefaults.outlinedButtonBorder else null
    ) { Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun InfoBox(text: String, bgColor: Color = Color(0xFFEEF2F7), borderColor: Color = Color(0xFFB8C9DC), textColor: Color = AccentBlue) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
        .background(bgColor).border(1.dp, borderColor, RoundedCornerShape(6.dp)).padding(10.dp)) {
        Text(text, fontSize = 12.sp, color = textColor, lineHeight = 18.sp)
    }
}

@Composable
fun ResultCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
        .background(SurfaceColor).border(1.dp, BorderColor, RoundedCornerShape(8.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp, color = MutedColor)
        HorizontalDivider(color = BorderColor)
        content()
    }
}

@Composable
fun FieldRow(label: String, value: String?) {
    val clean = value?.takeIf { it.isNotBlank() && it.lowercase() !in listOf("null","n/a","none","not found","na") }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MutedColor)
        Spacer(Modifier.height(2.dp))
        if (clean != null) Text(clean, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextColor)
        else Text("not found", fontSize = 13.sp, color = BorderColor, fontStyle = FontStyle.Italic)
    }
}

@Composable
fun AccusedItem(index: Int, name: String, age: String?, address: String?) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
        .background(BgColor).border(1.dp, BorderColor, RoundedCornerShape(6.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$index. $name", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextColor)
        if (!age.isNullOrBlank() && age.lowercase() != "null")
            Text("Age: $age", fontSize = 11.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
        if (!address.isNullOrBlank() && address.lowercase() != "null")
            Text(address, fontSize = 11.sp, color = MutedColor)
    }
}

@Composable
fun FlowRowPills(items: List<String>, bgColor: Color = Color(0xFFDCE8F5), textColor: Color = AccentBlue) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { pill ->
                    Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bgColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(pill, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = textColor)
                    }
                }
            }
        }
    }
}

@Composable
fun SeverityBadge(level: String) {
    val (bg, fg) = when (level.lowercase()) {
        "critical" -> Color(0xFF7B0000) to Color.White
        "high"     -> Color(0xFFFDE0DE) to AccentRed
        "medium"   -> Color(0xFFFDECD6) to AmberColor
        else       -> Color(0xFFD5F0DE) to GreenColor
    }
    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(bg)
        .padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(level.uppercase(), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = fg)
    }
}

@Composable
fun MetaBadge(label: String, color: Color, bg: Color) {
    Box(modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(bg)
        .padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = color)
    }
}
