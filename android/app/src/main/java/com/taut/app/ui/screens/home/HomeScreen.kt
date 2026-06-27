package com.taut.app.ui.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.taut.app.ui.theme.ACTION_BLUE
import com.taut.app.ui.theme.ACTION_GRAY
import com.taut.app.ui.theme.ACTION_GREEN
import com.taut.app.ui.theme.ACTION_YELLOW
import com.taut.app.ui.theme.Spacing
import com.taut.app.ui.theme.TouchTargets

/**
 * Home Screen (§9.2)
 *
 * 4 large tiles in 2×2 grid:
 * - ⚖️ Timbang (green) — primary action
 * - 💰 Harga (blue)
 * - 📋 Riwayat (yellow)
 * - ⚙️ Atur (gray)
 *
 * Below tiles: today's summary card.
 * Top-right: sync status indicator.
 */
@Composable
fun HomeScreen(
    onTimbang: () -> Unit,
    onHarga: () -> Unit,
    onRiwayat: () -> Unit,
    onAtur: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val summary by viewModel.todaySummary.collectAsStateWithLifecycle()
    val bankName = viewModel.bankName
    val greeting = viewModel.greeting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.L)
    ) {
        Spacer(modifier = Modifier.height(Spacing.M))

        // Header
        Text(
            text = bankName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(Spacing.XS))

        // Sub-header with dynamic greeting
        Text(
            text = greeting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Spacing.XL))

        // 2×2 Tile Grid
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                modifier = Modifier.fillMaxWidth()
            ) {
                HomeTile(
                    label = "Timbang",
                    icon = "\u2696\uFE0F",  // ⚖️
                    backgroundColor = ACTION_GREEN,
                    onClick = onTimbang,
                    modifier = Modifier.weight(1f)
                )
                HomeTile(
                    label = "Harga",
                    icon = "\uD83D\uDCB0",  // 💰
                    backgroundColor = ACTION_BLUE,
                    onClick = onHarga,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                modifier = Modifier.fillMaxWidth()
            ) {
                HomeTile(
                    label = "Riwayat",
                    icon = "\uD83D\uDCCB",  // 📋
                    backgroundColor = ACTION_YELLOW,
                    onClick = onRiwayat,
                    modifier = Modifier.weight(1f)
                )
                HomeTile(
                    label = "Atur",
                    icon = "\u2699\uFE0F",  // ⚙️
                    backgroundColor = ACTION_GRAY,
                    onClick = onAtur,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.L))

        // Today's Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(Spacing.XS)
        ) {
            Column(
                modifier = Modifier.padding(Spacing.M)
            ) {
                Text(
                    text = "Hari ini",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.XS))
                Text(
                    text = summary?.let {
                        "%.1f kg | Rp%,d | %d transaksi".format(
                            it.totalWeight / 1000.0,
                            it.totalValue / 100,
                            it.transactionCount
                        )
                    } ?: "0 transaksi | 0 kg | Rp0",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.M))
    }
}

/**
 * Home tile component (§4.1, §6).
 * Each tile: icon (40dp) + text label (18sp) + background color.
 * Size: 100dp preferred (80dp minimum).
 */
@Composable
private fun HomeTile(
    label: String,
    icon: String,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(TouchTargets.homeTile)
            .width(TouchTargets.homeTile),  // square tile
        shape = RoundedCornerShape(Spacing.XS),
        color = backgroundColor,
        tonalElevation = 0.dp  // flat design, no elevation
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.XS),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = icon,
                fontSize = 28.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.XXS))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}
