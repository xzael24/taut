package com.taut.app.ui.navigation

/**
 * Screen routes for TAUT navigation.
 *
 * Based on §3.1.2 Screen Map from architecture.md:
 * - Splash → Auth → PIN → Home → Transaction Flow / Prices / History / Settings
 *
 * All routes are simple strings; no deep linking for MVP.
 */
sealed class Screen(val route: String) {

    /** Splash screen — app logo, loading indicator */
    data object Splash : Screen("/splash")

    /** PIN entry — kiosk mode operator authentication */
    data object PinEntry : Screen("/auth/pin")

    /** Home — 4 large tiles (Timbang, Harga, Riwayat, Atur) */
    data object Home : Screen("/home")

    /** Step 1: Weight Entry — Timbang Barang */
    data object WeightEntry : Screen("/weigh")

    /** Step 2: Category Selection — Pilih Jenis Sampah */
    data object CategorySelection : Screen("/weigh/category")

    /** Step 3: Confirmation — Konfirmasi Setoran */
    data object Confirmation : Screen("/weigh/confirm")

    /** Transaction saved confirmation overlay */
    data object TransactionSaved : Screen("/weigh/saved")

    /** Price List — Harga Sampah */
    data object PriceList : Screen("/prices")

    /** Transaction History — Riwayat */
    data object History : Screen("/history")

    /** Settings — Atur */
    data object Settings : Screen("/settings")
}
