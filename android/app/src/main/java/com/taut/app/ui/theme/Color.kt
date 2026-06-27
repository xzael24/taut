package com.taut.app.ui.theme

import androidx.compose.ui.graphics.Color

// ======== BACKGROUNDS (§2.1 Primary Palette) ========
val BG_PRIMARY = Color(0xFF1A1A2E)          // Dark gray-black — main background (dark mode)
val BG_SECONDARY = Color(0xFF16213E)         // Dark blue-gray — card/section background
val BG_TERTIARY = Color(0xFF0F3460)          // Medium gray — input fields, interactive areas

// ======== TEXT (§2.1) ========
val TEXT_PRIMARY = Color(0xFFFFFFFF)         // White — primary text on dark backgrounds
val TEXT_SECONDARY = Color(0xFFB0B0B0)       // Light gray — sub-text, labels, timestamps
val TEXT_DISABLED = Color(0xFF606060)        // Dark gray — disabled text/buttons

// ======== ACTION COLORS (§2.2 Semantic Palette) ========
val ACTION_GREEN = Color(0xFF00C853)         // Success, "Timbang" tile, save, synced
val ACTION_BLUE = Color(0xFF2196F3)          // Information, "Harga" tile, links
val ACTION_YELLOW = Color(0xFFFFD600)        // Warnings, "Riwayat" tile, pending
val ACTION_RED = Color(0xFFFF5252)           // Errors, destructive, failed sync
val ACTION_GRAY = Color(0xFF9E9E9E)          // "Atur" tile, neutral actions
val ACTION_ORANGE = Color(0xFFFF9800)        // In-progress, loading

// ======== STATUS COLORS (§2.4) ========
val STATUS_SYNCED = Color(0xFF00C853)        // Green — synced
val STATUS_PENDING = Color(0xFF9E9E9E)       // Gray — pending sync
val STATUS_SYNCING = Color(0xFFFFD600)       // Yellow — syncing
val STATUS_FAILED = Color(0xFFFF5252)        // Red — sync failed
val STATUS_CONFIRMED = Color(0xFF69F0AE)     // Light green — server confirmed
val STATUS_VOID = Color(0xFFFF5252)          // Red — void/cancelled

// ======== WASTE CATEGORY COLORS (§2.3) ========
val CATEGORY_KERTAS = Color(0xFF8D6E63)      // Light brown — paper
val CATEGORY_PLASTIK = Color(0xFFFFC107)     // Yellow — plastic
val CATEGORY_KACA = Color(0xFF26A69A)        // Green — glass
val CATEGORY_LOGAM = Color(0xFF607D8B)       // Blue-gray — metal
val CATEGORY_TEKSTIL = Color(0xFFAB47BC)     // Purple — textile
val CATEGORY_ELEKTRONIK = Color(0xFF00897B)  // Dark teal — e-waste
val CATEGORY_CAMPURAN = Color(0xFF795548)    // Brown — mixed

// ======== LIGHT MODE OVERRIDES (§10.2) ========
val BG_PRIMARY_LIGHT = Color(0xFFF5F5F5)
val BG_SECONDARY_LIGHT = Color(0xFFFFFFFF)
val BG_TERTIARY_LIGHT = Color(0xFFE0E0E0)
val TEXT_PRIMARY_LIGHT = Color(0xFF1A1A2E)
val TEXT_SECONDARY_LIGHT = Color(0xFF616161)
val DIVIDER_LIGHT = Color(0xFFE0E0E0)

// ======== MISC ========
val DIVIDER_DARK = Color(0xFF303050)          // 1dp divider line
val BANNER_INFO = Color(0xFF1B5E20)           // Dark green — info banner bg
val BANNER_WARNING = Color(0xFFE65100)        // Dark orange — warning banner bg
val BANNER_ERROR = Color(0xFFB71C1C)          // Dark red — error banner bg
