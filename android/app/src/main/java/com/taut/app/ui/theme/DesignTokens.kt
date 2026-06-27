package com.taut.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * TAUT Design Tokens — Spacing, Sizing, and Component Tokens
 *
 * Based on §5 Layout & Spacing System and §10 Appendix.
 * Base unit: 8dp. All spacing is multiples of 8dp.
 */

// ======== SPACING (§5.2 Spacing Scale) ========
object Spacing {
    val XXS = 4.dp    // Inline spacing (icon-to-text)
    val XS = 8.dp     // Base unit, tight spacing
    val S = 12.dp     // Between related elements
    val M = 16.dp     // Standard padding/margin
    val L = 24.dp     // Section separation, screen margins
    val XL = 32.dp    // Major section breaks
    val XXL = 48.dp   // Before/after primary action buttons
}

// ======== SCREEN MARGINS (§5.3) ========
object ScreenMargins {
    val horizontal = Spacing.L        // 24dp left/right
    val top = Spacing.M               // 16dp below status bar
    val bottom = Spacing.M            // 16dp above navigation
    val gridGap = Spacing.M           // 16dp between grid items
}

// ======== TOUCH TARGETS (§5.4) ========
object TouchTargets {
    val homeTile = 100.dp             // Preferred home screen tile
    val homeTileMin = 80.dp           // Minimum home screen tile
    val categoryTile = 96.dp          // Category selection tile
    val primaryButtonHeight = 64.dp   // "Simpan", "Lanjut"
    val secondaryButtonHeight = 48.dp // "Kembali"
    val listRowHeight = 64.dp         // Transaction list row
    val keypadButton = 56.dp          // Numeric keypad button
    val minTouchTarget = 56.dp        // Absolute minimum (exceeds WCAG 44dp)
}

// ======== COMPONENT SIZING (§6) ========
object ComponentSizing {
    val primaryButtonHeight = 64.dp
    val secondaryButtonHeight = 48.dp
    val tileMinSize = 80.dp
    val tilePreferredSize = 100.dp
    val tileGridGap = 16.dp
    val cornerRadius = 8.dp
    val borderWidth = 1.dp
    val borderWidthThick = 3.dp
    val colorBarWidth = 4.dp
    val pinKeyWidth = 72.dp
    val pinKeyHeight = 64.dp
    val pinDotSize = 20.dp
    val pinDotGap = 12.dp
    val keypadButtonSize = 72.dp
    val searchHeight = 56.dp
    val iconSize = 40.dp
    val statusIconSize = 16.dp
}

// ======== GRID (§5.5 Layout Grid) ========
object GridLayout {
    val columns = 12
    val columnWidth = 8.dp
    val gutter = 8.dp
    val screenMargin = Spacing.L
}
