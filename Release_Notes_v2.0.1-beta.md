# Release Notes — PaperInk PDF v2.0.1-beta

**Release Version:** `v2.0.1-beta`  
**Version Code:** `7`  
**Package:** `com.paperink`  
**Date:** November 9, 2026  

---

### Major Milestone: OCR Search & On-Device Gemma 4 2B Intelligence

PaperInk PDF transitions to **v2.0.1-beta**, introducing on-device OCR text recognition for scanned documents and a fully private, on-device generative AI assistant powered by Google Gemma 4 2B.

---

### Highlights & Key Features

#### 🔍 On-Device OCR & Scanned PDF Search
- **ML Kit Text Recognition**: Powered by Google ML Kit Latin text recognition, executing entirely on `Dispatchers.IO` with zero network overhead.
- **Smart Scanned Document Detection**: Automatically recognizes image-based PDFs when native text streams are empty or sparse.
- **In-PDF Search on Scanned Documents**: Extracts bounding coordinates normalized to `0f..1f`, enabling full-text keyword search and real-time bounding-box highlighting on scanned contracts, books, receipts, and invoices.
- **Persistent Room Cache**: Database upgraded to `version = 3` with `MIGRATION_2_3` creating table `ocr_text_cache` so OCR scans run once and persist permanently across sessions with foreign key cascade deletion.
- **Non-Intrusive Banner**: `OcrBannerCard` provides live scanning progress and auto-prompts to run OCR when in-PDF search returns 0 matches on an image document.
- **Export to Searchable PDF**: Automatically generates standard, searchable text-based PDFs with an invisible text layer embedded over scanned images, universally indexable and selectable across third-party viewers.
- **50% Memory Reduction**: Optimized rendering pipeline allocating base image canvases in `RGB_565` for minimal memory overhead during OCR processing.

#### 🤖 Gemma 4 2B On-Device AI Assistant
- **LiteRT-LM On-Device LLM**: Integrates `com.google.ai.edge.litertlm:litertlm-android:0.18.0` with Kotlin toolchain for on-device reasoning.
- **Hardware Acceleration**: Automatic GPU acceleration using OpenCL with seamless fallback to CPU mode.
- **Document Context Injection**: Automatically injects document text and OCR output into Gemma's prompt window (up to 24,000 characters) for context-grounded answers.
- **Quick Action Prompts**:
  - 📄 **Summarize PDF**: Generates structured summaries with key takeaways and bullet points.
  - 🔑 **Key Takeaways**: Extracts core insights and conclusions.
  - 📋 **Action Items**: Extracts actionable next steps, deadlines, and recommendations.
  - 💬 **Interactive Q&A**: Multi-turn conversational chat with streaming tokens and one-tap copy-to-clipboard.
- **Always-Visible AI FAB**: Accessible from any page in the reader, styled in the Nordic Ocean theme.
- **Background Model Manager**: In-app download manager for `gemma-4-E2B-it.litertlm` (~2.6 GB) with live byte counters, download progress, SharedPreferences persistence across app restarts, scoped storage compliance, and support for pre-loaded ADB files.

#### 🎨 Nordic Ocean Design System
- Clean Arctic Sky & Ocean Blue color harmony with AAA contrast compliance.
- Responsive Material 3 Bottom Sheets and Floating Action Buttons.

#### 🧪 Testing & Verification
- **36 automated tests** passing (`:app:testDebugUnitTest`), covering core reader operations, PDF shadows, OCR engine span serialization, Room database migrations, and AI Assistant states.
