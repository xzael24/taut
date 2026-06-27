package com.taut.app.ui.screens.weigh

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taut.app.data.local.entity.WasteCategoryEntity
import com.taut.app.ui.theme.ACTION_GREEN
import com.taut.app.ui.theme.BG_SECONDARY
import com.taut.app.ui.theme.BG_TERTIARY
import com.taut.app.ui.theme.ComponentSizing
import com.taut.app.ui.theme.Spacing
import com.taut.app.ui.theme.TEXT_SECONDARY
import com.taut.app.ui.theme.TouchTargets

/**
 * Step 2: Pilih Kategori (Category Selection) — §9.4
 *
 * 2-column grid of waste categories loaded from Room DB.
 * Each tile: photo placeholder + name (18sp bold) + price per kg (14sp).
 *
 * Search field at top for filtering.
 * Step indicator at bottom: [2/3: Pilih]
 */
@Composable
fun CategorySelectionScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    viewModel: WeighViewModel? = null
) {
    var searchQuery by remember { mutableStateOf("") }

    // Categories from DB via ViewModel (search-aware)
    val categories by remember(searchQuery) {
        if (viewModel != null) {
            if (searchQuery.isBlank()) {
                viewModel.categories
            } else {
                viewModel.searchCategories(searchQuery)
            }
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    // Currently selected category
    val selectedCategory by remember {
        if (viewModel != null) viewModel.selectedCategory
        else {
            kotlinx.coroutines.flow.MutableStateFlow<WasteCategoryEntity?>(null)
        }
    }.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.L)
    ) {
        Spacer(modifier = Modifier.height(Spacing.M))

        // Screen title
        Text(
            text = "Pilih Jenis Sampah",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(Spacing.M))

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "\uD83D\uDD0D Cari kategori...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF808080)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Spacing.XXS),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ACTION_GREEN,
                unfocusedBorderColor = Color(0xFF303050),
                cursorColor = ACTION_GREEN,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = BG_TERTIARY,
                unfocusedContainerColor = BG_TERTIARY
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(Spacing.M))

        // Category grid (2 columns)
        if (categories.isEmpty()) {
            Text(
                text = "Tidak ada kategori yang tersedia.\n\nSilakan hubungi admin untuk menambah kategori.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(Spacing.XL),
                textAlign = TextAlign.Center
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                verticalArrangement = Arrangement.spacedBy(Spacing.M),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.M),
                modifier = Modifier.weight(1f)
            ) {
                items(categories, key = { it.id }) { category ->
                    CategoryTile(
                        category = category,
                        isSelected = selectedCategory?.id == category.id,
                        onClick = { viewModel?.selectCategory(category) }
                    )
                }
            }
        }

        // Step indicator + bottom bar
        Text(
            text = "[2/3: Pilih]",
            style = MaterialTheme.typography.bodySmall,
            color = TEXT_SECONDARY,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.XS))

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
                border = ButtonDefaults.outlinedButtonBorder
            ) {
                Text(
                    text = "\u2190 Kembali",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .weight(2f)
                    .height(TouchTargets.primaryButtonHeight),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ACTION_GREEN
                ),
                shape = RoundedCornerShape(ComponentSizing.cornerRadius),
                enabled = selectedCategory != null
            ) {
                Text(
                    text = "[2/3: Lanjut \u2192]",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.M))
    }
}

/**
 * Category tile for step 2 grid (§6.4).
 * Shows: photo placeholder, name, price per kg.
 * Selected state: green border + tinted background.
 */
@Composable
private fun CategoryTile(
    category: WasteCategoryEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) {
        ACTION_GREEN.copy(alpha = 0.15f)
    } else {
        BG_SECONDARY
    }

    val borderModifier = if (isSelected) {
        Modifier.border(
            width = ComponentSizing.borderWidthThick,
            color = ACTION_GREEN,
            shape = RoundedCornerShape(ComponentSizing.cornerRadius)
        )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .background(bgColor, RoundedCornerShape(ComponentSizing.cornerRadius))
            .padding(Spacing.S)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Photo placeholder (real photo via Coil in production)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        categoryCategoryColor(category.categoryGroup).copy(alpha = 0.3f),
                        RoundedCornerShape(Spacing.XXS)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\uD83D\uDCC8",  // 📸 placeholder
                    fontSize = 32.sp
                )
            }

            Spacer(modifier = Modifier.height(Spacing.XS))

            // Category name
            Text(
                text = category.nameId,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            // Price per kg
            Text(
                text = "Rp%,d/kg".format(category.unitPrice / 100),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Map category group string to a color for the tile background. */
@Composable
private fun categoryCategoryColor(group: String): Color = when (group.lowercase()) {
    "kardus", "kertas" -> com.taut.app.ui.theme.CATEGORY_KERTAS
    "plastik" -> com.taut.app.ui.theme.CATEGORY_PLASTIK
    "kaca" -> com.taut.app.ui.theme.CATEGORY_KACA
    "logam", "besi" -> com.taut.app.ui.theme.CATEGORY_LOGAM
    "tekstil" -> com.taut.app.ui.theme.CATEGORY_TEKSTIL
    "elektronik" -> com.taut.app.ui.theme.CATEGORY_ELEKTRONIK
    "campuran" -> com.taut.app.ui.theme.CATEGORY_CAMPURAN
    else -> BG_TERTIARY
}
