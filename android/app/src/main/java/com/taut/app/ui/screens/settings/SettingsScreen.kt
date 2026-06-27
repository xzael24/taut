package com.taut.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.taut.app.ui.theme.DIVIDER_DARK
import com.taut.app.ui.theme.Spacing
import com.taut.app.ui.theme.TEXT_SECONDARY

/**
 * Settings Screen — Atur
 *
 * Contains:
 * - Profile info (operator name, bank name)
 * - Operator switch (kiosk mode)
 * - PIN change
 * - Sync Now button
 * - Dashboard URL
 * - Theme toggle (dark/light)
 * - TTS on/off
 * - App version
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val operatorName by viewModel.operatorName.collectAsState()
    val bankName by viewModel.bankName.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.L)
    ) {
        Spacer(modifier = Modifier.height(Spacing.M))

        Text(
            text = "Atur",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(Spacing.L))

        // Profile section
        Text(
            text = "Profil",
            style = MaterialTheme.typography.labelSmall,
            color = TEXT_SECONDARY
        )
        Spacer(modifier = Modifier.height(Spacing.XS))
        SettingsRow(label = "Operator", value = operatorName)
        Spacer(modifier = Modifier.height(Spacing.S))
        SettingsRow(label = "Bank Sampah", value = bankName)

        HorizontalDivider(
            color = DIVIDER_DARK,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = Spacing.M)
        )

        // App info section
        Text(
            text = "Informasi Aplikasi",
            style = MaterialTheme.typography.labelSmall,
            color = TEXT_SECONDARY
        )
        Spacer(modifier = Modifier.height(Spacing.XS))
        SettingsRow(label = "Versi", value = viewModel.appVersion)
        Spacer(modifier = Modifier.height(Spacing.S))
        SettingsRow(label = "Dashboard", value = viewModel.dashboardUrl)

        Spacer(modifier = Modifier.height(Spacing.M))

        // More settings placeholder
        Text(
            text = "Pengaturan lainnya akan ditampilkan di sini.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
