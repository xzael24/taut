package com.taut.app.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.taut.app.data.local.entity.TransactionEntity
import com.taut.app.ui.theme.ACTION_GREEN
import com.taut.app.ui.theme.DIVIDER_DARK
import com.taut.app.ui.theme.Spacing
import com.taut.app.ui.theme.TEXT_SECONDARY
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Transaction History Screen — Riwayat
 *
 * Tabs: [Hari Ini] [Minggu Ini] [Bulan Ini]
 * Shows scrollable transaction list with color-coded category strips.
 * Summary card at top: total transactions, total weight, total value.
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val summaryText by viewModel.summaryText.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.L)
    ) {
        Spacer(modifier = Modifier.height(Spacing.M))

        Text(
            text = "Riwayat",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(Spacing.S))

        // Summary card
        Text(
            text = summaryText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(Spacing.M))

        // Filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.S)
        ) {
            HistoryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ACTION_GREEN.copy(alpha = 0.2f),
                        selectedLabelColor = ACTION_GREEN
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.M))

        if (transactions.isEmpty()) {
            Text(
                text = "Belum ada transaksi",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.XL),
                textAlign = TextAlign.Center
            )
        } else {
            // Transaction list
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(transactions, key = { it.id }) { transaction ->
                    TransactionListItem(transaction)
                    HorizontalDivider(
                        color = DIVIDER_DARK,
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionListItem(transaction: TransactionEntity) {
    val dateFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm")
        .withZone(ZoneId.of("Asia/Jakarta"))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.S)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text(
                text = transaction.id.take(8) + "...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = dateFormat.format(Instant.ofEpochMilli(transaction.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = TEXT_SECONDARY
            )
        }
        Spacer(modifier = Modifier.height(Spacing.XXS))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text(
                text = "%.1f kg".format(transaction.totalWeight / 1000.0),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Rp%,d".format(transaction.totalValue / 100),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (transaction.status == "synced") ACTION_GREEN
                        else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
