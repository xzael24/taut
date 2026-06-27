package com.taut.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ======== TYPOGRAPHY SYSTEM (§3.2 Type Scale) ========
// Uses system default Android font (Roboto/Noto Sans) — no custom fonts.
// This ensures maximum compatibility, smallest APK, and fast rendering on low-RAM devices.
//
// Naming convention: PascalCase following Material3 role names:
//   Display, Headline, Title, Body, Label, Number
//   Size suffixes: XL, L, M, S

// ── Display (screen titles) ────────────────────────────────────────────
/**
 * Display XL — 36sp Bold — Price display "Rp15.000"
 */
val NumberXL = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 36.sp,
    lineHeight = 44.sp
)

/**
 * Display L — 28sp Bold — Screen titles "Timbang Barang"
 */
val DisplayL = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 36.sp
)

/**
 * Display M — 24sp Bold — Section headers, transaction totals
 */
val DisplayM = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 32.sp
)

// ── Headline (sub-headers, category names) ────────────────────────────
/**
 * Headline M — 20sp Bold — Card titles, prominent labels
 */
val HeadlineM = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 28.sp
)

// ── Title (smaller headings) ──────────────────────────────────────────
/**
 * Title L — 24sp Bold — Weight display, step indicator
 */
val NumberL = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 32.sp
)

/**
 * Title M — 18sp Medium — Sub-headers, category names
 */
val TitleM = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp,
    lineHeight = 24.sp
)

/**
 * Title S — 16sp Medium — Sub-section headers
 */
val TitleS = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 22.sp
)

// ── Body (primary / secondary reading text) ────────────────────────────
/**
 * Body L — 18sp Regular — Primary body text (default)
 */
val BodyL = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 18.sp,
    lineHeight = 26.sp
)

/**
 * Body M — 16sp Regular — Secondary text, timestamps
 */
val BodyM = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 22.sp
)

/**
 * Body S — 14sp Regular — Detail text, metadata
 */
val BodyS = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp
)

// ── Label (field labels, button text, captions) ──────────────────────
/**
 * Label M — 14sp Medium — Field labels, button text
 */
val LabelM = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp
)

/**
 * Label S — 12sp Medium — Small labels, badge text
 */
val LabelS = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp
)

/**
 * Label XS — 12sp Regular — Footnotes, metadata (minimum size)
 */
val LabelXS = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp
)

// ── Material3 Typography Configuration ───────────────────────────────
/**
 * TAUT Typography — Material3 Typography configuration.
 *
 * Maps our design system roles to Material3 text roles.
 * Each M3 role gets a unique mapping to preserve the full type scale.
 *
 * Custom number styles (NumberXL, NumberL) are used directly
 * via style = TautTypography.xxx where roles match their purpose,
 * or as standalone TextStyle references for specialized displays.
 */
val TautTypography = Typography(
    displayLarge = DisplayL,          // 28sp Bold — Splash title
    displayMedium = DisplayM,         // 24sp Bold — Screen titles
    displaySmall = HeadlineM,         // 20sp Bold — Card titles

    headlineLarge = DisplayM,         // 24sp Bold — Section headers
    headlineMedium = TitleM,          // 18sp Medium — Sub-headers
    headlineSmall = TitleS,           // 16sp Medium — Sub-section labels

    titleLarge = DisplayM,            // 24sp Bold — Screen title on Home/Settings
    titleMedium = TitleM,             // 18sp Medium — Card headers
    titleSmall = TitleS,              // 16sp Medium — Small card titles

    bodyLarge = BodyL,                // 18sp Regular — Primary reading
    bodyMedium = BodyM,               // 16sp Regular — Secondary text
    bodySmall = BodyS,                // 14sp Regular — Detail, metadata

    labelLarge = LabelM,              // 14sp Medium — Field labels, buttons
    labelMedium = LabelS,             // 12sp Medium — Small labels, inputs
    labelSmall = LabelXS              // 12sp Regular — Captions, footnotes
)
