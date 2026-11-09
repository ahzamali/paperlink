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
- **Smart Context Budgeting & Local RAG**: Resolves "context too long" errors by strictly budgeting context to 3,500 characters (~850 tokens). Dynamically switches between **Current Page Focus**, **Local RAG Keyword Matching**, and **Document Overview Sampling** to handle documents of any size.
- **Auto-Recovery on Context Limits**: Automatically detects sequence limit errors, resets KV cache, and retries with condensed context.
- **Quick Action Prompts**:
  - 📄 **Page Summary & Doc Summary**: Fast page-level or full-document summaries.
  - 🔑 **Key Takeaways**: Extracts core insights and conclusions.
  - 📋 **Action Items**: Extracts actionable next steps, deadlines, and recommendations.
  - 💬 **Interactive Q&A**: Multi-turn conversational chat with streaming tokens and one-tap copy-to-clipboard.
- **Always-Visible AI FAB**: Accessible from any page in the reader, styled in the Nordic Ocean theme.
- **Background Model Manager**: In-app download manager for `gemma-4-E2B-it.litertlm` (~2.6 GB) with live byte counters, download progress, SharedPreferences persistence across app restarts, scoped storage compliance, and support for pre-loaded ADB files.

#### 🎨 Nordic Ocean Design System
- Clean Arctic Sky & Ocean Blue color harmony with AAA contrast compliance.
- Responsive Material 3 Bottom Sheets and Floating Action Buttons.

#### 🧪 Testing & Verification
- **54 automated tests** passing with 100% pass rate (`:app:testDebugUnitTest`), covering core reader operations, PDF shadows, OCR engine span serialization, Room database migrations, multi-page PDF text extraction (up to 47 pages), search highlights, and AI Assistant context budgeting & soft keyboard navigation.
