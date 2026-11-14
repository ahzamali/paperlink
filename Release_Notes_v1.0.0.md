# Release Notes — PaperInk PDF v1.0.0 (Official Release)

**Release Version:** `v1.0.0`  
**Version Code:** `9`  
**Package:** `com.paperink`  
**Date:** November 14, 2026  

---

### Overview

PaperInk PDF **v1.0.0** is the first official production release of PaperInk PDF—a modern, fast, privacy-focused Android PDF reader, annotator, and organizer built with **Kotlin** and **Jetpack Compose (Material 3)**.

Designed around a 100% offline, privacy-first philosophy, PaperInk PDF ensures that all rendering, text extraction, password decryption, OCR processing, and document intelligence execute exclusively on local hardware with zero cloud dependencies and zero data telemetry.

---

### Highlights & Key Features

#### 🛡️ 100% Private & Local-First Processing
- **Zero Cloud Network Calls**: All operations run locally on device silicon.
- **First-Run Welcome Flash Dialog**: Welcomes new users on first launch with an interactive introduction to privacy assurances, PDF merging & compression tools, and the experimental local AI assistant. Users can also access this guide anytime via the Library menu.

#### 📖 High-Performance Document Reader
- **Continuous Vertical Scrolling**: Smooth, memory-managed asynchronous page rendering.
- **Fluid Gestures**: 2D constrained panning, multi-touch pinch-to-zoom (up to 5x), and double-tap zoom toggle.
- **Reading Modes**: High-contrast Day mode, inverted Night/Dark mode, and warm book-like Sepia mode.
- **In-Document Search**: High-speed keyword search across plain text, sticky notes, and OCR layers with page jumpers and match counts.

#### ✏️ Annotation Suite & Permanent In-Place Flattening
- **Pen & Highlighter**: Configurable stroke widths and palette presets with semi-transparent highlighter blending.
- **Movable Sticky Notes**: Draggable notes placed anywhere on the document canvas.
- **Undo / Redo Stack**: Up to 50 levels of linear history.
- **Permanent PDF Burning**: Android canvas rendering burns strokes and notes into the PDF binary so annotations display in any standard third-party viewer.

#### 📑 PDF Utilities: Merge & Smart Compression
- **Multi-PDF Merging**: Combine two or more documents with custom ordering directly to device storage.
- **Smart Compression Presets**: Four tailored quality presets (Low 90%, Balanced 75%, High 50%, Maximum 35%) reducing file sizes by up to 75% via direct `/Filter /DCTDecode` streaming.

#### 🔒 Secure Password-Protected PDF Support
- **Automatic Detection**: Detects encrypted documents via Android 15 `SecurityException` and `/Encrypt` dictionaries.
- **Zero-Password Cache**: Transparently decrypts working copies without saving passwords to disk, Room database, or UI state.

#### 🔍 On-Device OCR & Searchable PDF Creation
- **Google ML Kit Offline Recognition**: Fully offline Latin script text recognition.
- **Scanned Document Detection**: Prompts user with a non-intrusive banner on image-based PDFs.
- **Room Database Cache (v3)**: Scans persist permanently across sessions with cascade deletion.
- **Searchable PDF Export**: Generates standardized searchable text layer PDFs from scanned images.

#### 🤖 Experimental On-Device AI Assistant (Gemma 4 2B)
- **Experimental Status & Hidden by Default**: Kept disabled by default to maintain an uncluttered, distraction-free reading experience.
- **Reader Menu Toggle**: Easily enabled at any time from the Reader’s 3-dot overflow menu (`⋮`) or the onboarding flash screen.
- **100% Local Inference**: Runs Google Gemma 4 2B (`gemma-4-E2B-it.litertlm`) on local GPU/NPU via `litertlm-android`.
- **Smart Context Budgeting & Local RAG**: Safeguards against context length errors (`MAX_CONTEXT_CHARS <= 5000`) across documents of up to 47+ pages.
- **Soft Keyboard & Two-Stage IME Navigation**: Window resizing and back-handler priorities prevent accidental dismissal while typing.

---

### Automated Test Suite Verification

- **Total Tests:** 56 Automated Tests
- **Status:** 100% Passing (0 failures, 0 errors, 0 skipped)
  - `AiAssistantTest` (12 tests): Multi-page digest, 10-page batch scoping, token budgeting, IME invariants.
  - `ExampleRobolectricTest` (24 tests): Room OCR database cache, migration, annotation serialization, PDF compression & merging.
  - `PdfEngineTextExtractionTest` (8 tests): Multi-page text extraction, bounding highlight search.
  - `OcrEngineTest` (6 tests): Offline ML Kit OCR engine, image rendering, error recovery.
  - `ReaderViewModelAiIntegrationTest` (5 tests): Experimental AI feature flag, first-run onboarding state persistence, back press priorities.
  - `ExampleUnitTest` (1 test): JVM environment integrity.

