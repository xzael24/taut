package com.taut.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taut.app.ui.theme.ACTION_GRAY
import com.taut.app.ui.theme.ACTION_GREEN
import com.taut.app.ui.theme.ACTION_RED
import com.taut.app.ui.theme.BG_TERTIARY
import com.taut.app.ui.theme.ComponentSizing
import com.taut.app.ui.theme.Spacing
import com.taut.app.ui.theme.TEXT_SECONDARY
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect

/**
 * PIN Entry Screen — first-time PIN + daily verification.
 *
 * 6-digit PIN pad (1..9, 0, ⌫) with masked digit circles.
 * Uses PinEntryViewModel for PIN verification.
 */
@Composable
fun PinEntryScreen(
    onPinVerified: () -> Unit,
    viewModel: PinEntryViewModel = hiltViewModel()
) {
    val pin by viewModel.pin.collectAsStateWithLifecycle()
    val verificationState by viewModel.verificationState.collectAsStateWithLifecycle()

    // Navigate away when PIN is verified
    LaunchedEffect(verificationState) {
        if (verificationState is PinVerificationState.Verified) {
            onPinVerified()
        }
    }

    val errorMessage = (verificationState as? PinVerificationState.Error)?.message

    // Request notification permission on Android 13+ (API 33+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — non-critical; app works without */ }
    SideEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.L),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App title
        Text(
            text = "TAUT",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(Spacing.XXS))

        Text(
            text = "Masukkan PIN",
            style = MaterialTheme.typography.bodyLarge,
            color = TEXT_SECONDARY
        )

        Spacer(modifier = Modifier.height(Spacing.XL))

        // PIN digit indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 4) {
                Box(
                    modifier = Modifier
                        .size(ComponentSizing.pinDotSize)
                        .clip(CircleShape)
                        .background(
                            if (i < pin.length) ACTION_GREEN else BG_TERTIARY
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (i < pin.length) {
                        Text(
                            text = "●",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Error message
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(Spacing.S))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = ACTION_RED
            )
        }

        // Verifying indicator
        if (verificationState is PinVerificationState.Verifying) {
            Spacer(modifier = Modifier.height(Spacing.S))
            Text(
                text = "Memverifikasi...",
                style = MaterialTheme.typography.bodySmall,
                color = TEXT_SECONDARY
            )
        }

        Spacer(modifier = Modifier.height(Spacing.XL))

        // Numeric keypad: rows of 3
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )

        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(modifier = Modifier.size(ComponentSizing.keypadButtonSize))
                    } else {
                        Surface(
                            onClick = {
                                if (key == "⌫") {
                                    viewModel.deleteLastDigit()
                                } else {
                                    viewModel.appendDigit(key)
                                }
                            },
                            modifier = Modifier.size(ComponentSizing.keypadButtonSize),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (key == "⌫") "⌫" else key,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(Spacing.S))
        }
    }
}