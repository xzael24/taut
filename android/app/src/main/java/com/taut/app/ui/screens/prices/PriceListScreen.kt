package com.taut.app.ui.screens.prices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.taut.app.data.local.entity.WasteCategoryEntity
import com.taut.app.ui.theme.BG_TERTIARY
import com.taut.app.ui.theme.DIVIDER_DARK
import com.taut.app.ui.theme.Spacing
import com.taut.app.ui.theme.TEXT_SECONDARY

/**
 * Price List Screen — Harga Sampah
 *
 * Shows all waste categories with current prices.
 * Categories displayed with real photos + name + price per kg.
 * Shows last updated timestamp.
 */
@Composable
fun PriceListScreen(
    onBack: () -> Unit,
    viewModel: PriceListViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.L)
    ) {
        Spacer(modifier = Modifier.height(Spacing.M))

        Text(
            text = "Harga Sampah",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(Spacing.S))

        Text(
            text = viewModel.lastUpdatedDisplay,
            style = MaterialTheme.typography.bodySmall,
            color = TEXT_SECONDARY
        )

        Spacer(modifier = Modifier.height(Spacing.L))

        if (categories.isEmpty()) {
            Text(
                text = "Daftar harga akan ditampilkan di sini.\n\nBelum ada kategori tersedia.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(categories, key = { it.id }) { category ->
                    CategoryPriceRow(category, viewModel.formatPrice(category.unitPrice))
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
private fun CategoryPriceRow(
    category: WasteCategoryEntity,
    formattedPrice: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Photo placeholder (real photo in production with Coil)
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(Spacing.XXS))
                .background(BG_TERTIARY),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📸",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.padding(start = Spacing.M))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.nameId,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            category.nameEn?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TEXT_SECONDARY
                )
            }
        }

        Text(
            text = formattedPrice,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
