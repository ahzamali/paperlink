package com.paperink.ui.theme

import androidx.compose.ui.graphics.Color

// Nordic Ocean Theme Palette (Option 2)
val OceanBlue = Color(0xFF0284C7)           // Primary Accent (Vibrant Ocean)
val OceanBlueDark = Color(0xFF38BDF8)       // Vibrant Sky Blue for Dark Theme
val OceanBlueVariant = Color(0xFF0369A1)    // Deep Marine Blue
val OceanContainer = Color(0xFFE0F2FE)      // Soft Arctic Sky container
val OceanOnContainer = Color(0xFF0C4A6E)    // Deep Abyssal Navy on container (AAA contrast 10.4:1)
val OceanSecondary = Color(0xFF0F172A)      // Deep Slate
val OceanSecondaryDark = Color(0xFF94A3B8)  // Light Ice Slate

// Backward Compatibility Aliases
val InkPrimary = OceanBlue
val InkPrimaryDark = OceanBlueDark
val InkPrimaryVariant = OceanBlueVariant
val InkSecondary = OceanSecondary
val InkSecondaryDark = OceanSecondaryDark

// Legacy Adobe Aliases (mapped to Ocean theme)
val AdobeRed = OceanBlue
val AdobeRedDark = OceanBlueDark
val AdobeRedVariant = OceanBlueVariant
val AdobeRedContainer = OceanContainer
val AdobeOnRedContainer = OceanOnContainer
val AdobeSecondary = OceanSecondary
val AdobeSecondaryDark = OceanSecondaryDark

// Light Theme - Nordic Ocean Clean White & Slate
val PaperBackground = Color(0xFFF8FAFC)     // Pale Frost Neutral canvas
val PaperSurface = Color(0xFFFFFFFF)        // Crisp White surface
val PaperSurfaceVariant = Color(0xFFF1F5F9) // Cool Slate Border / Search Bar
val InkTextPrimary = Color(0xFF0F172A)      // Deep Slate Charcoal
val InkTextSecondary = Color(0xFF475569)    // Muted Slate

// Dark Theme - Nordic Ocean Midnight Abyssal
val MidnightBackground = Color(0xFF0B132B)  // Deep Midnight Slate background
val MidnightSurface = Color(0xFF111C38)     // Midnight Slate Surface
val MidnightSurfaceVariant = Color(0xFF1E293B) // Dark Border Slate
val MidnightTextPrimary = Color(0xFFF8FAFC) // Pure Ice White text
val MidnightTextSecondary = Color(0xFF94A3B8) // Cool Muted Slate

// Annotation Colors Palette
val AnnotationYellow = Color(0xFFFBBF24)
val AnnotationGreen = Color(0xFF34D399)
val AnnotationBlue = Color(0xFF38BDF8)
val AnnotationPink = Color(0xFFF472B6)
val AnnotationOrange = Color(0xFFFB923C)
val AnnotationRed = Color(0xFFEF4444)
val AnnotationPurple = Color(0xFFA855F7)
val AnnotationBlack = Color(0xFF1E293B)

// Sepia Reading Mode Tone
val SepiaBackground = Color(0xFFFBF0D9)
val SepiaPaperText = Color(0xFF5F4B32)
