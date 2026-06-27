package com.taut.app.ui.navigation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.taut.app.data.local.AuthDataStore
import com.taut.app.ui.screens.auth.PinEntryScreen
import com.taut.app.ui.screens.home.HomeScreen
import com.taut.app.ui.screens.home.SplashScreen
import com.taut.app.ui.screens.history.HistoryScreen
import com.taut.app.ui.screens.prices.PriceListScreen
import com.taut.app.ui.screens.settings.SettingsScreen
import com.taut.app.ui.screens.weigh.CategorySelectionScreen
import com.taut.app.ui.screens.weigh.ConfirmationScreen
import com.taut.app.ui.screens.weigh.TransactionSavedScreen
import com.taut.app.ui.screens.weigh.WeightEntryScreen
import com.taut.app.ui.screens.weigh.WeighViewModel

/**
 * Main navigation graph for TAUT.
 *
 * Transaction flow follows 3-step flow (§2.2 from user-flows):
 * 1. Timbang (Weight) → 2. Pilih (Category) → 3. Simpan (Confirm & Save)
 *
 * All interactive elements follow touch rules:
 * - No swipe gestures (tap and scroll only)
 * - No hamburger menu (navigation via home-screen tiles)
 * - Maximum 3 steps per primary action
 * - Destructive actions require confirmation dialog
 *
 * WeighFlow uses a shared [WeighViewModel] scoped to the Activity
 * so state persists across the multi-step weigh transaction.
 *
 * Auth persistence: if the operator has an active session in [AuthDataStore],
 * the splash screen skips PIN entry and navigates directly to Home.
 */
@Composable
fun TautNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route
) {
    // Shared WeighViewModel scoped to Activity for multi-step weigh flow
    val activity = LocalContext.current as ComponentActivity
    val weighViewModel: WeighViewModel = viewModel(activity)

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Splash → (skip PIN if session valid) → PIN or Home
        composable(Screen.Splash.route) {
            val context = LocalContext.current.applicationContext
            val authDataStore = remember { AuthDataStore(context as Context) }
            var hasSession by remember { mutableStateOf<Boolean?>(null) }

            LaunchedEffect(Unit) {
                hasSession = authDataStore.getIsLoggedInSync()
            }

            SplashScreen(
                onReady = {
                    val destination = if (hasSession == true) {
                        Screen.Home.route
                    } else {
                        Screen.PinEntry.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.PinEntry.route) {
            PinEntryScreen(
                onPinVerified = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.PinEntry.route) { inclusive = true }
                    }
                }
            )
        }

        // Home — 4-tile dashboard
        composable(Screen.Home.route) {
            HomeScreen(
                onTimbang = { navController.navigate(Screen.WeightEntry.route) },
                onHarga = { navController.navigate(Screen.PriceList.route) },
                onRiwayat = { navController.navigate(Screen.History.route) },
                onAtur = { navController.navigate(Screen.Settings.route) }
            )
        }

        // Transaction Flow — Step 1: Weight Entry
        composable(Screen.WeightEntry.route) {
            WeightEntryScreen(
                viewModel = weighViewModel,
                onContinue = { navController.navigate(Screen.CategorySelection.route) },
                onBack = { navController.popBackStack() }
            )
        }

        // Transaction Flow — Step 2: Category Selection
        composable(Screen.CategorySelection.route) {
            CategorySelectionScreen(
                viewModel = weighViewModel,
                onContinue = { navController.navigate(Screen.Confirmation.route) },
                onBack = { navController.popBackStack() }
            )
        }

        // Transaction Flow — Step 3: Confirmation & Save
        composable(Screen.Confirmation.route) {
            ConfirmationScreen(
                viewModel = weighViewModel,
                onSave = {
                    navController.navigate(Screen.TransactionSaved.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Transaction Saved — overlay
        composable(Screen.TransactionSaved.route) {
            TransactionSavedScreen(
                viewModel = weighViewModel,
                onTimbangLagi = {
                    navController.navigate(Screen.WeightEntry.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onKeBeranda = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        // Price List
        composable(Screen.PriceList.route) {
            PriceListScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // History
        composable(Screen.History.route) {
            HistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Settings
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
