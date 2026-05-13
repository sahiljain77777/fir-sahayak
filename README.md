# FIR Sahayak: On-Device AI for Police FIR Digitisation in India


## Gemma 4 Good Hackathon — Technical Write-Up
**Track: Digital Equity & Inclusivity**

---

## The Problem

India registers over **5.8 million FIRs annually** (NCRB, 2022), making FIR processing one of the largest operational workflows in the country's justice system. Yet most police stations still rely on handwritten registers and entirely manual documentation — a gap repeatedly highlighted in Crime and Criminal Tracking Networks and Systems (CCTNS) implementation audits, which cite inaccurate data entry and fragmented records as core barriers to effective policing.

**CCTNS** — *Crime and Criminal Tracking Networks and Systems* — is the Ministry of Home Affairs' flagship mission-mode project to connect all ~17,000 police stations in India through a national crime database. Despite years of investment, digitisation at the station level remains uneven, with many stations continuing manual form-filling due to connectivity and infrastructure constraints.

This creates a structural gap in India's justice system:

- Officers must transition from the 160-year-old IPC framework to the new Bharatiya Nyaya Sanhita (BNS), requiring rapid adaptation to entirely new legal sections
- Paper records prevent searchable databases, cross-case analytics, and repeat-offender detection
- Citizens have no visibility into whether the correct legal sections were actually applied

**FIR Sahayak bridges this gap** — transforming FIR processing into an intelligent, fully offline digital workflow using on-device AI.

---

## What It Does

FIR Sahayak is an Android app that uses **Gemma 4 E2B on-device** to automate the most labour-intensive part of FIR processing: **structured entity extraction and form-filling**. Whether the source is a handwritten paper FIR or a spoken complaint, the app identifies and extracts every named legal entity — complainant, accused, place of occurrence, date and time, incident narrative, and applicable BNS sections — and populates them into structured, editable fields. This entity extraction layer is what turns an unstructured complaint (written or spoken) into a machine-readable legal record that can be searched, validated, and exported.

1. **Two input modes** — scanned/photographed FIR PDF or spoken complaint in Hindi, English, or Hinglish
2. **Automatic entity extraction** — every key legal field (complainant name, accused details, sections invoked, timeline, place of occurrence) is extracted and displayed as labelled, structured fields — eliminating manual form transcription entirely
3. **BNS section assessment** — PDF mode cross-references extracted sections against a local severity database; audio mode infers applicable BNS sections directly from spoken narrative
4. **Per-section severity breakdown** — cognisability, bailability, maximum punishment, and urgency score in plain language (see Legal Rule Engines below)
5. **Verification UI** — every extracted field is editable before finalisation; uncertain fields highlighted in amber for officer review
6. **Official-format FIR PDF generation** — auto-populated from extracted entities, reducing manual typing to near-zero
7. **Fully offline** — no data leaves the device at any point

---

## A Day With and Without FIR Sahayak

| Scenario | Without FIR Sahayak | With FIR Sahayak |
|---|---|---|
| **Complaint arrives** | Officer transcribes spoken complaint by hand | Officer records audio; app extracts entities in real time |
| **Legal section selection** | Officer manually looks up IPC/BNS section books | App infers BNS sections from narrative and explains severity |
| **Form filling** | Officer types all fields into physical register or CCTNS terminal | All fields pre-populated from entity extraction; officer reviews and confirms |
| **Ambiguous sections** | Relies on personal experience; errors common during IPC→BNS transition | Unrecognised sections flagged in amber for manual review |
| **PDF / record generation** | Re-typed by clerk; takes 20–40 minutes per FIR | Auto-generated in official format within seconds |
| **Searchability** | Paper register — no search, no cross-referencing | Structured JSON entity record, ready for CCTNS integration |
| **Complainant transparency** | Complainant has no way to verify sections applied | Plain-language severity breakdown visible immediately |
| **Rural / offline stations** | Cannot use cloud-based tools due to connectivity | Fully functional offline after one-time model download |

---

## Why This Is a Digital Equity & Inclusivity Problem

| Equity Dimension | How FIR Sahayak Addresses It |
|---|---|
| Infrastructure gap | Full entity extraction on a mid-range Android phone — no server needed |
| Legal literacy | BNS sections inferred and explained in plain language |
| Economic | No subscription, no cloud fees — runs on ~₹12,000 phones (~$125) |
| Privacy | Fully on-device — assault, DV, and atrocity case details never leave the phone |
| Geographic | Works offline after one-time model download — viable at rural stations |
| BNS transition | Audio path infers BNS 2023 directly; PDF path validates extracted sections |
| Transparency | Complainants can independently verify which sections were filed and what they mean |
| Language | Hindi/English/Hinglish voice; Devanagari + Latin OCR |

---
## Technical Architecture

**System Overview**

```
┌─────────────────────────────────────────────────────────────┐
│                        Android Device                       │
│                                                             │
│  ┌────────────────────────┐   ┌───────────────────────────┐ │
│  │     PDF / Camera Input │   │        Audio Input        │ │
│  │  (Existing paper FIRs) │   │  (Live complaint narration│ │
│  │                        │   │   Hindi/English/Hinglish) │ │
│  └───────────┬────────────┘   └──────────────┬────────────┘ │
│              │                               │              │
│              ▼                               ▼              │
│  ┌───────────────────────┐    ┌──────────────────────────┐  │
│  │      ML Kit OCR       │    │     Android STT Engine   │  │
│  │  (Hindi + Latin OCR)  │    │  (hi-IN primary,         │  │
│  └───────────┬───────────┘    │   en-IN fallback)        │  │
│              │                └──────────────┬───────────┘  │
│              ▼                               │              │
│  ┌───────────────────────┐                   ▼              │
│  │  OCR Act/Section Merge│    ┌──────────────────────────┐  │
│  │  Cleaner + Digit      │    │  Transcript Review Screen│  │
│  │  Normalizer           │    │  (Editable before        │  │
│  └───────────┬───────────┘    │   sending to Gemma)      │  │
│              │                └──────────────┬───────────┘  │
│              └──────────────┬────────────────┘              │
│                             │                               │
│                             ▼                               │
│                 ┌──────────────────────┐                    │
│                 │    Gemma 4 E2B       │                    │
│                 │  (LiteRT on-device   │                    │
│                 │   CPU inference)     │                    │
│                 │  + Token Streaming   │                    │
│                 └──────────┬───────────┘                    │
│                            │                                │
│                            ▼                                │
│                 ┌──────────────────────┐                    │
│                 │   Robust JSON Parser │                    │
│                 │  (Repair + sanitise  │                    │
│                 │   malformed output)  │                    │
│                 └──────────┬───────────┘                    │
│                            │                                │
│             ┌──────────────▼─────────────────┐              │
│             │     Local Rule Engines         │              │
│             │  ┌─────────────────────────┐   │              │
│             │  │ BNS Severity DB         │   │              │
│             │  │ (350+ sections)         │   │              │
│             │  ├─────────────────────────┤   │              │
│             │  │ OtherActs Severity DB   │   │              │
│             │  │ (Dowry/POCSO/SC-ST/IT/  │   │              │
│             │  │  Arms/NDPS/UAPA/DV...)  │   │              │
│             │  └─────────────────────────┘   │              │
│             └────────────────────────────────┘              │
│                            │                                │
│              ┌─────────────┴──────────────┐                 │
│              │                            │                 │
│              ▼ (PDF path)                 ▼ (Audio path)    │
│  ┌───────────────────────┐   ┌────────────────────────┐     │
│  │   FIR Result Screen   │   │   Verification UI      │     │
│  │  (Section breakdown,  │   │  (Every field editable,│     │
│  │   structured display) │   │  uncertain highlighted)│     │
│  └───────────────────────┘   └───────────┬────────────┘     │
│                                          │                  │
│                                          ▼                  │
│                              ┌────────────────────────┐     │
│                              │   FIR PDF Generator    │     │
│                              │ (Official FIR Draft    │     │  
│                              │            Template)   │     │
│                              └────────────────────────┘     │
│                                                             │
│                                                             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```


**PDF path:** PDF/Camera → ML Kit OCR → Spatial Sort + Digit Normalizer → Gemma 4 E2B (streaming) → JSON Parser → Local Rule Engines → FIR Result Screen

**Audio path:** Audio Input → Android STT → Transcript Review Screen → Gemma 4 E2B (streaming) → JSON Parser → Local Rule Engines → Verification UI → FIR PDF Generator

---

## Challenges and Solutions

### 1. OCR on Government Forms — Why Standard ML Kit Fails Here

FIR forms are multi-column printed tables. Standard ML Kit processes text in bounding-box order, which interleaves rows across columns — "District | Year | P.S." on one row gets fragmented and merged with unrelated rows. Additionally, ML Kit occasionally misreads printed Latin digits as Bengali Unicode characters (e.g., `8` as `৪`, which is `4`), causing section numbers like `85` to be extracted as `45` — a silent legal error.

**Solution:** A two-pass spatial sort groups OCR output into Y-coordinate bands (40px tolerance at 3× render scale), sorts left-to-right within each band, and outputs pipe-separated structured text. A Devanagari/Bengali digit normalizer catches and corrects all such Unicode misreads before the text reaches Gemma. This approach is tightly matched to the physical structure of Indian government FIR forms in a way generic OCR post-processing is not.

### 2. JSON Reliability from an Edge Model

Gemma 4 E2B at the edge, running with CPU inference, can produce partially-formed JSON under load — especially for long FIRs with many sections. Any single parsing failure would break the entire extraction pipeline.

**Solution:** Greedy decoding (`temperature=0.01, topK=1, topP=0.0`) maximises output determinism so the same input always produces the same structure. A multi-stage JSON repair pipeline applies structural fixes before parsing and falls back field-by-field if the full parse fails, so a malformed section array never corrupts the complainant's name.

### 3. Multi-Act FIR Documents

A real FIR often invokes sections from multiple acts simultaneously — BNS 2023, POCSO 2012, SC/ST Atrocities Act 1989, and others. A single severity database or a generic prompt would either miss sections or mis-score them.

**Solution:** Act-aware section routing with separate severity databases per act (10 acts covered), and explicit prompt instructions to tag each section with its parent act. The local rule engines override Gemma's severity output entirely for scoring — the model is used for extraction, where it excels; deterministic databases handle legal scoring, where precision is non-negotiable.

### 4. Why Gemma 4 E2B on LiteRT Is the Right Choice

The app must run offline on a ₹12,000 Android device, handle mixed Hindi/English/Hinglish input, produce deterministic legal output, and process sensitive police data without any cloud dependency. Gemma 4 E2B via LiteRT is the only model that satisfies all four constraints simultaneously — it runs on CPU, supports multilingual input, is small enough for on-device deployment, and is open enough to be distributed without per-inference cloud costs.

---

## Legal Rule Engines (Fully Local)

The local severity databases cover **350+ BNS 2023 sections** plus 10 additional acts. For each section, the engines output four fields in plain language:

- **Cognisability** — whether police can arrest without a magistrate's warrant (*cognisable*) or require one first (*non-cognisable*); determines how urgently an officer must act
- **Bailability** — whether the accused has a right to bail (*bailable*) or bail is at the court's discretion (*non-bailable*); directly affects detention decisions
- **Maximum punishment** — the ceiling sentence prescribed by law for that section (e.g., "up to 7 years imprisonment")
- **Urgency score (0–10)** — `0` means low urgency (minor/civil in nature); `10` means immediate action required (e.g., heinous offence, victim at risk); helps officers prioritise response

These engines override Gemma's severity output — the model is used for entity extraction, not legal scoring.

**Acts covered:** BNS 2023, Dowry Act 1961, POCSO 2012, SC/ST Atrocities Act 1989, IT Act 2000, Arms Act 1959, NDPS 1985, UAPA 1967, Domestic Violence Act 2005, Juvenile Justice Act 2015.

---

## Technical Stack

| Component | Technology |
|---|---|
| Platform | Android (API 24+) · Kotlin · Jetpack Compose |
| LLM | Gemma 4 E2B · Google AI Edge LiteRT (CPU) |
| OCR | ML Kit — Latin + Devanagari |
| STT | Android SpeechRecognizer API |
| PDF | Android PdfRenderer (input) · PdfDocument (output) |
| Storage | On-device only |

---

## Limitations and Future Work

- Voice input currently uses Android SpeechRecognizer, which requires internet for Hindi STT; **Vosk-based on-device STT** is planned to eliminate this last cloud dependency
- Audio mode infers BNS sections only; multi-act audio inference is a planned extension
- Database export and CCTNS integration not yet implemented — current output is structured on-device display and PDF
- Regional language support (Tamil, Telugu, Bengali, Marathi) planned

---

## Impact

With ~17,000 police stations in India and no backend infrastructure required, distribution via Play Store or APK costs nothing per station. The structured entity extraction layer this app produces today is the foundation on which searchable FIR databases, CCTNS integration, and data-driven policing can be built tomorrow.

---

*Built with Gemma 4 E2B · Google AI Edge LiteRT · ML Kit OCR · Android SpeechRecognizer · Jetpack Compose. All inference on-device. Zero data transmitted or stored externally.*
