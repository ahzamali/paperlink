# Release Notes — PaperInk PDF v2.0.2-beta

**Release Version:** `v2.0.2-beta`  
**Version Code:** `8`  
**Package:** `com.paperink`  
**Date:** November 9, 2026  

---

### Overview

PaperInk PDF **v2.0.2-beta** delivers critical enhancements for large document handling (47+ pages), multi-page PDF text extraction and stream parsing, soft keyboard interaction with two-stage back/down navigation in the AI Assistant window, and an expanded test suite of **54 automated unit and Robolectric tests**.

---

### Highlights & Key Improvements

#### 📄 Multi-Page PDF Engine & Text Stream Parsing Fixes
- **PDF Page Object vs. Pages Tree Disambiguation**: Resolved a critical issue where `/Type /Pages` (the PDF Pages root tree node) was mistakenly matched as a `/Page` dictionary by the non-backtracking page indexer. Individual `/Page` objects are now strictly verified against character boundaries, ensuring page 0 is never skipped and all pages (up to 47+ pages) are extracted in accurate sequential order.
- **Extended Lookback Window (4096 Bytes)**: Stream dictionary lookback increased from 1024 to 4096 bytes to reliably catch object stream headers on PDFs containing extensive font metrics, resource dictionaries, and metadata.
- **Sequential Stream Fallback**: In the event that complex non-standard PDFs omit standard `/Contents` references in page trees, the engine automatically matches unassigned content streams sequentially, ensuring zero blank pages.

#### 🤖 47-Page Document Digest & Chunk-Level Local RAG
- **Full Document Digest Sampling**: In multi-page documents (e.g. 47 pages), the AI Assistant automatically loads in `DOCUMENT_OVERVIEW` mode, generating a complete page-by-page index (`[Page 1]: ... [Page 47]:`) within the strict on-device model context budget (`MAX_CONTEXT_CHARS <= 5000`).
- **10-Page Batch Scoping**: Large documents are automatically partitioned into 10-page batch ranges (`P. 1–10`, `P. 11–20`, ..., `P. 41–47`), allowing readers to focus Gemma's analysis on specific document chapters.
- **Chunk-Level Keyword Local RAG**: Natural language questions score all paragraphs across the entire 47-page document to extract the top-matching sections while safeguarding against context overflow errors.

#### ⌨️ Soft Keyboard & Two-Stage IME Navigation
- **Responsive Sheet Layout**: Configured `android:windowSoftInputMode="adjustResize"` on `.MainActivity` and applied `SOFT_INPUT_ADJUST_RESIZE` to the `ModalBottomSheet` dialog window. When the keyboard opens, the input field smoothly moves above the keyboard and the sheet dynamically expands (`fillMaxHeight(if (isImeVisible) 1f else 0.85f)`).
- **Two-Stage Back / Down Navigation**: 
  - Dynamic `ModalBottomSheetProperties(shouldDismissOnBackPress = !isImeVisible)` and `BackHandler(enabled = isImeVisible)` ensure that pressing Back or the Down chevron while the soft keyboard is open **hides the keyboard first**.
  - Only on a subsequent back gesture is the chat window closed, eliminating accidental dismissals.
- **Safety Back-Up in Reader**: `BackHandler` in `ReaderScreen` closes the AI Assistant sheet before allowing navigation back to the library.

#### 🧪 Expanded Test Suite (54 Tests, 100% Passing)
- **`PdfEngineTextExtractionTest`** (8 tests): Validates multi-page text extraction on 1-page, 5-page, and 47-page documents, empty page handling, large dictionary lookback, and real-time bounding highlight keyword search.
- **`AiAssistantTest`** (12 tests): Covers 47-page document digest generation, 10-page batch scoping, single-page scoping, emergency fallback budgets, and IME dismiss invariants.
- **`ReaderViewModelAiIntegrationTest`** (3 tests): Validates state management, blank page filtering, and back press handling priorities.
- **`ExampleRobolectricTest` & `OcrEngineTest`** (31 tests): Covers Room database OCR cache persistence, cascade deletion, annotation serialization, and LiteRT-LM model status checks.
