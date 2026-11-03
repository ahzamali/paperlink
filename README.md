# PaperInk PDF (Paperlink)

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-36%20(Android%2015)-brightgreen.svg)](https://developer.android.com/about/versions/15)
[![Language](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)

A modern, fast, privacy-focused Android PDF reader, annotator, and organizer crafted with **Kotlin** and **Jetpack Compose (Material 3)**. Styled with an Adobe Acrobat-inspired red-and-white aesthetic, PaperInk PDF brings continuous vertical rendering, fluid multi-touch gestures, offline document organization, secure password protection handling, and in-place annotation flattening to mobile devices.

---

## Highlights & Features

### 📖 High-Performance Document Viewer
- **Continuous Vertical Scrolling**: Seamless page-to-page reading with asynchronous, memory-managed page rendering.
- **Floating Page Indicator**: Minimalist pill overlay displaying current page and total page count with fast jump capabilities.
- **Fluid Gestures**:
  - Multi-touch pinch-to-zoom (up to 5x magnification).
  - Smooth 2D drag panning constrained to content boundaries.
  - Double-tap toggle to quickly switch between fitted view and zoomed inspection.
- **Reading Modes**:
  - **Day / Normal**: Crisp, high-contrast default presentation.
  - **Night / Dark Mode**: Inverted color matrix to reduce eye strain in low-light environments.
  - **Sepia Mode**: Warm, book-like paper tone for extended reading sessions.

### 🔒 Password-Protected PDF Support
- **Automatic Detection**: Detects password-protected documents via Android 15 `SecurityException` and low-level `/Encrypt` markers before rendering.
- **Secure Password Prompt**: Material 3 modal dialog with document title banner, password visibility toggle, and instant error feedback.
- **Android 15 Decryption**: Leverages `PdfRenderer(pfd, LoadParams.Builder().setPassword(password).build())` on modern devices with fallback reflection for earlier versions.
- **Zero-Password Cache**: Transparently decrypts and stages a clean working cache copy (`write(..., removePasswordProtection = true)`), allowing instant subsequent viewing, searching, and printing without re-prompting.
- **Strict Privacy Guarantee**: Passwords are used strictly for in-flight decryption and are **immediately cleared from memory**—passwords are never saved to disk, Room database, `SharedPreferences`, or UI state.
- **Visual Status**: Distinct lock badges on document cards across the library.

### ✏️ Annotation Suite & In-Place Flattening
- **Freehand Pen & Highlighter**:
  - Configurable stroke widths and palette presets.
  - Semi-transparent blend modes for natural document text highlighting.
- **Sticky Text Notes**: Draggable notes with custom content that can be placed anywhere on the page canvas.
- **Full History**: Linear Undo and Redo stack for stroke operations.
- **PDF Flattening ("Save")**: Direct Android canvas flattening that permanently burns drawing strokes and notes into the PDF binary so annotations render across all third-party PDF viewers.

### 🔍 In-Document Search
- Search plain text, bookmarks, and user-created sticky notes across all pages.
- Jump-to-match navigation with highlighted match indicators and search result counters.

### 🗜️ PDF Compression Presets
- Compress large documents directly within the app using four tailored presets:
  - **Low Compression** (90% quality) – Best for text and high-res line art.
  - **Balanced** (75% quality) – Ideal balance between file size and readability.
  - **High Compression** (50% quality) – Noticeable reduction for quick email sharing.
  - **Maximum Compression** (35% quality) – Extreme size reduction for bandwidth-constrained scenarios.
- Direct **Save to Storage** and **Share** intents for compressed outputs.

### 🖨️ Native Printing
- Seamless integration with Android's native `PrintManager` and `PrintDocumentAdapter` to print documents directly to physical Wi-Fi printers or export to standard Android PDF print formats.

### 📂 Offline-First Library & Organization
- **Local Persistence**: Powered by SQLite & Room Database for fast offline indexing of documents, custom folders, bookmarks, and annotation history.
- **Folder Management**: Create custom folders, organize files, and easily filter between storage files and saved library documents.
- **Recent Documents**: Quick-access carousel for recently opened files.

### ☁️ Firebase Ready
- Pre-configured with the Google Services Gradle plugin and Firebase BoM (`35.1.0`).
- Ready for Firebase Analytics and Crashlytics integration.

---

## Architecture & Technology Stack

PaperInk PDF follows modern Android architectural recommendations (Unidirectional Data Flow and MVVM):

- **Language**: [Kotlin](https://kotlinlang.org/) (2.0.21)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material 3](https://m3.material.io/)
- **PDF Core**: Android native [PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer) (enhanced with Android 15 SDK 36 APIs)
- **Database & Cache**: [Room Persistence Library](https://developer.android.com/training/data-storage/room)
- **Concurrency**: Kotlin Coroutines & `StateFlow`
- **Testing**: [Robolectric](https://robolectric.org/) + [JUnit 4](https://junit.org/junit4/)

---

## Project Structure

```
paperink-pdf/
├── .github/
│   └── workflows/
│       ├── android-ci.yml         # Automated CI: unit tests & debug APK artifact build
│       └── release.yml            # Automated GitHub Release & APK publishing on git tags
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/
│   │   │   │   ├── data/          # Room DB, entities, DAOs, DocumentRepository
│   │   │   │   ├── pdf/           # PdfEngine (rendering, encryption detection, compression, printing)
│   │   │   │   ├── ui/
│   │   │   │   │   ├── components/# PasswordPromptDialog, AnnotationToolbar, CompressDialog, etc.
│   │   │   │   │   ├── screens/   # ReaderScreen, LibraryScreen
│   │   │   │   │   ├── theme/     # Material 3 Acrobat red-and-white color schemes & typography
│   │   │   │   │   └── viewmodel/ # ReaderViewModel, LibraryViewModel
│   │   │   │   └── MainActivity.kt# Edge-to-edge entry point
│   │   │   ├── res/               # Vector icons, themes, strings, XML resources
│   │   │   └── AndroidManifest.xml
│   │   └── test/                  # Robolectric unit tests (PdfEngine, protection, models)
│   ├── build.gradle.kts           # Module-level Gradle configuration
│   └── google-services.json       # Firebase services configuration (optional/local)
├── gradle/
│   └── libs.versions.toml         # Centralized dependency version catalog
├── LICENSE                        # Apache License 2.0
├── README.md                      # Documentation
├── build.gradle.kts               # Root-level build configuration
└── settings.gradle.kts            # Project settings & plugin repositories
```

---

## Getting Started

### Prerequisites
- **JDK 21** (e.g. Eclipse Temurin or OpenJDK 21)
- **Android Studio Ladybug (2024.2.1)** or newer
- **Android SDK Platform 36** (Android 15)

### Clone the Repository
```bash
git clone https://github.com/ahzamali/paperlink.git
cd paperlink
```

### Firebase Setup (Optional)
If you wish to enable Firebase Analytics or Crashlytics:
1. Create a project in the [Firebase Console](https://console.firebase.google.com/).
2. Add an Android app with package name `com.example.paperinkpdf` (or your custom package ID).
3. Download `google-services.json` and place it in the `app/` folder.

---

## Building & Testing

### Run Tests
Execute the unit and Robolectric test suite:
```bash
# On Linux / macOS
./gradlew testDebugUnitTest

# On Windows
gradlew.bat testDebugUnitTest
```

### Assemble Debug APK
Compile the debug application package:
```bash
# On Linux / macOS
./gradlew assembleDebug

# On Windows
gradlew.bat assembleDebug
```
The output APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## Continuous Integration & Automated Releases

PaperInk PDF comes with fully configured GitHub Actions workflows:

1. **Android CI (`.github/workflows/android-ci.yml`)**:
   - Triggers on every push and pull request to `main`.
   - Sets up JDK 21 and Android SDK 36.
   - Executes all Robolectric and JUnit unit tests.
   - Compiles the debug APK and uploads it as an artifact (`paperlink-debug-apk`).

2. **Automated Releases (`.github/workflows/release.yml`)**:
   - Triggers whenever a version tag matching `v*` (e.g. `v1.0.2-beta`) is pushed to GitHub.
   - Compiles the APK and publishes it directly as a downloadable asset (`paperink-pdf-<version>.apk`).
   - Automatically marks releases matching `*beta*` or `*alpha*` as Pre-releases on GitHub.

To create and publish a new release:
```bash
git tag -a v1.0.3-beta -m "Release v1.0.3-beta"
git push origin v1.0.3-beta
```

---

## License

PaperInk PDF is licensed under the **Apache License, Version 2.0**. See the [LICENSE](LICENSE) file for details.

```
Copyright 2026 Ahzam Ali

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
