package com.taut.app.ui.screens.weigh

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.taut.app.ui.theme.ACTION_GREEN
import com.taut.app.ui.theme.ComponentSizing
import com.taut.app.ui.theme.DIVIDER_DARK
import com.taut.app.ui.theme.Spacing
import com.taut.app.ui.theme.TEXT_SECONDARY
import com.taut.app.ui.theme.TouchTargets
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Step 3: Konfirmasi & Simpan (Confirm & Save) — §9.5
 *
 * Shows all transaction details before saving:
 * - Nasabah name + phone
 * - Category
 * - Weight
 * - Price per kg
 * - Total (large, green, 36sp bold)
 *
 * Data sourced from WeighViewModel (shared across weigh flow).
 * Step indicator at bottom: [3/3: Simpan]
 */
@Composable
fun ConfirmationScreen(
    onSave: () -> Unit,
    onBack: () -> Unit,
    viewModel: WeighViewModel? = null
) {
    val customerName by viewModel?.customerName?.collectAsState() ?: remember { mutableStateOf("") }
    val customerPhone by viewModel?.customerPhone?.collectAsState() ?: remember { mutableStateOf("") }
    val weightGrams by viewModel?.weightGrams?.collectAsState() ?: remember { mutableStateOf(0L) }
    val selectedCategory by viewModel?.selectedCategory?.collectAsState() ?: remember { mutableStateOf<com.taut.app.data.local.entity.WasteCategoryEntity?>(null) }

    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    val customerDisplay = if (customerName.isNotBlank()) {
        if (customerPhone.isNotBlank()) "$customerName ($customerPhone)" else customerName
    } else {
        "Berjalan (Walk-in)"
    }

    val weightDisplay = if (weightGrams >= 1000) {
        "%.1f kg".format(weightGrams / 1000.0)
    } else {
        "$weightGrams g"
    }

    val categoryName = selectedCategory?.nameId ?: "—"
    val pricePerKg = selectedCategory?.let { "Rp%,d/kg".format(it.unitPrice / 100) } ?: "—"
    val totalValue = selectedCategory?.let { cat ->
        val totalVal = if (weightGrams >= 1000) {
            (weightGrams / 1000) * cat.unitPrice
        } else {
            (weightGrams * cat.unitPrice) / 1000
        }
        "Rp%,d".format(totalVal / 100)
    } ?: "Rp0"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.L)
    ) {
        Spacer(modifier = Modifier.height(Spacing.M))

        // Screen title
        Text(
            text = "Konfirmasi Setoran",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(Spacing.L))

        // Transaction detail card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(Spacing.XXS)
                )
                .padding(Spacing.M)
        ) {
            // Nasabah
            DetailRow(label = "Nasabah", value = customerDisplay)
            HorizontalDivider(
                color = DIVIDER_DARK,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = Spacing.S)
            )

            // Category
            DetailRow(label = "Jenis", value = categoryName)
            HorizontalDivider(
                color = DIVIDER_DARK,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = Spacing.S)
            )

            // Weight
            DetailRow(label = "Berat", value = weightDisplay)
            HorizontalDivider(
                color = DIVIDER_DARK,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = Spacing.S)
            )

            // Price per kg
            DetailRow(label = "Harga", value = pricePerKg)

            HorizontalDivider(
                color = DIVIDER_DARK,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = Spacing.S)
            )

            // Total (large, green)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = totalValue,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = ACTION_GREEN,
                    textAlign = TextAlign.End
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Step indicator
        Text(
            text = "[3/3: Simpan]",
            style = MaterialTheme.typography.bodySmall,
            color = TEXT_SECONDARY,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.XS))

        // Save error state
        if (isSaving) {
            Text(
                text = "Menyimpan...",
                style = MaterialTheme.typography.bodySmall,
                color = ACTION_GREEN,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // Bottom action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.M)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(TouchTargets.secondaryButtonHeight),
                shape = RoundedCornerShape(ComponentSizing.cornerRadius),
                border = ButtonDefaults.outlinedButtonBorder,
                enabled = !isSaving
            ) {
                Text(
                    text = "\u2190 Kembali",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = {
                    if (viewModel != null) {
                        isSaving = true
                        viewModel.saveTransaction { success ->
                            isSaving = false
                            if (success) onSave()
                        }
                    } else {
                        onSave()
                    }
                },
                modifier = Modifier
                    .weight(2f)
                    .height(TouchTargets.primaryButtonHeight),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ACTION_GREEN
                ),
                shape = RoundedCornerShape(ComponentSizing.cornerRadius),
                enabled = !isSaving && selectedCategory != null
            ) {
                Text(
                    text = "\u2705 Simpan",  // ✅ Simpan
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.M))
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
