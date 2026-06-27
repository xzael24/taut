package com.taut.app.ui.screens.weigh

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.taut.app.ui.theme.ACTION_GREEN
import com.taut.app.ui.theme.ComponentSizing
import com.taut.app.ui.theme.Spacing
import com.taut.app.ui.theme.TouchTargets
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Post-Save Confirmation overlay (§2.2 POST-SAVE CONFIRMATION)
 *
 * Shown after successful save:
 * - Success message with checkmark
 * - SMS delivery info
 * - Two actions: [Timbang Lagi] [Ke Beranda]
 *
 * TTS plays: "Transaksi berhasil disimpan. SMS terkirim ke nomor nasabah."
 */
@Composable
fun TransactionSavedScreen(
    onTimbangLagi: () -> Unit,
    onKeBeranda: () -> Unit,
    viewModel: WeighViewModel? = null
) {
    val context = LocalContext.current

    // SMS permission launcher (Android 13+)
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Izin SMS ditolak — notifikasi tidak dikirim", Toast.LENGTH_LONG).show()
        }
    }

    // Request SMS permission on screen load for Android 13+
    androidx.compose.runtime.LaunchedEffect(Unit) {
        smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
    }

    val customerPhone = if (viewModel != null) {
        viewModel.customerPhone.collectAsState().value
    } else ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.L),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Success icon
        Text(
            text = "\u2705",  // ✅
            style = MaterialTheme.typography.displayLarge,
            fontSize = 64.sp
        )

        Spacer(modifier = Modifier.height(Spacing.M))

        // Success title
        Text(
            text = "Transaksi Tersimpan!",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Spacing.S))

        // SMS info
        val smsPhone = if (customerPhone.isNotBlank()) customerPhone else "0812xxxxxxx"
        Text(
            text = "SMS terkirim ke $smsPhone",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.S))

        // Offline fallback text
        Text(
            text = "Akan dikirim saat online",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        // Action buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            // Timbang Lagi (primary)
            Button(
                onClick = {
                    viewModel?.reset()
                    onTimbangLagi()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TouchTargets.primaryButtonHeight),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ACTION_GREEN
                ),
                shape = RoundedCornerShape(ComponentSizing.cornerRadius)
            ) {
                Text(
                    text = "\u2696\uFE0F Timbang Lagi",  // ⚖️ Timbang Lagi
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Ke Beranda (secondary)
            OutlinedButton(
                onClick = {
                    viewModel?.reset()
                    onKeBeranda()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TouchTargets.secondaryButtonHeight),
                shape = RoundedCornerShape(ComponentSizing.cornerRadius),
                border = ButtonDefaults.outlinedButtonBorder
            ) {
                Text(
                    text = "Ke Beranda",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.XXL))
    }
}
