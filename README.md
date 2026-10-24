# PaperInk PDF (Paperlink)

A modern, fast, and feature-rich Android PDF reader, editor, and organizer built with **Kotlin** and **Jetpack Compose (Material 3)**. Styled with an Adobe Acrobat-inspired red & white aesthetic.

---

## Features

- **Adobe Acrobat-style Viewer**: Continuous vertical scrolling, persistent floating page indicator pill, and full edge-to-edge layout.
- **Fluid Gestures**: Multi-touch pinch-to-zoom (up to 5x), 2D drag panning with bounds clipping, and double-tap zoom toggle.
- **In-PDF Search**: Search text and notes across all pages with jump-to-match navigation and highlighted matches.
- **Full Annotation Engine**:
  - Pen & highlighter drawing with custom ink colors and stroke widths.
  - Sticky text notes with draggable placement.
  - Undo / Redo history for all stroke operations.
  - Direct PDF flattening ("Save" burns annotations directly to the PDF file).
- **PDF Compression & Quality Presets**:
  - Compress existing documents with Low (90%), Balanced (75%), High (50%), and Maximum (35%) presets.
  - Direct "Save to Device" and "Share" options.
- **Reading Modes**: Normal (day), Night / Dark mode, and Sepia mode.
- **Organized Top Navigation**: Clean 3-dot overflow menu housing Print, Dark Mode, PDF Compression, and Share options, keeping Search and Save front and center.
- **Printing**: Native Android `PrintManager` integration for printing documents directly to Wi-Fi or virtual printers.
- **Local Persistence**: Offline-first Room Database caching annotations, bookmarks, documents, and folders.

---

## Project Structure

```
.
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/
│   │   │   │   ├── data/             # Models, Room database, DAO, PDF engine
│   │   │   │   ├── ui/               # Compose screens, components, theme, viewmodels
│   │   │   │   └── MainActivity.kt   # Entry activity
│   │   │   ├── res/                  # Adaptive icons, drawables, strings, XML resources
│   │   │   └── AndroidManifest.xml
│   │   └── test/                     # Robolectric & JVM unit tests
│   └── build.gradle.kts
├── .github/
│   └── workflows/
│       └── android-ci.yml            # Automated CI: tests, build, and APK artifact upload
├── gradle/
│   ├── libs.versions.toml            # Version catalog
│   └── wrapper/                      # Gradle wrapper binaries & properties
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Building Locally

### Prerequisites
- **JDK 21**
- **Android Studio Ladybug** or newer (SDK 36)

### Run Unit & Robolectric Tests
```bash
./gradlew testDebugUnitTest
```

### Build Debug APK
```bash
./gradlew assembleDebug
```
The generated APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Continuous Integration (GitHub Actions)

The `.github/workflows/android-ci.yml` pipeline runs on every push and pull request to `main`:
1. Sets up JDK 21 (Temurin) and Android SDK (API 36).
2. Decodes `debug.keystore.base64` for reproducible builds.
3. Runs all Robolectric and unit tests.
4. Builds the debug APK and publishes it as a downloadable GitHub Actions artifact (`paperlink-debug-apk`).
