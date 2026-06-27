package com.taut.app.ui.screens.weigh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.taut.app.ui.theme.ACTION_GREEN
import com.taut.app.ui.theme.BG_TERTIARY
import com.taut.app.ui.theme.ComponentSizing
import com.taut.app.ui.theme.Spacing
import com.taut.app.ui.theme.TEXT_SECONDARY
import com.taut.app.ui.theme.TouchTargets
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Step 1: Timbang (Weigh) — §9.3
 *
 * Flow: Home → [⚖️ Timbang] → WeightEntry → CategorySelection → Confirmation
 *
 * Fields:
 * - Nama Nasabah: search/select from existing or type new name
 * - Foto Barang: optional photo (<10kg) / required (>10kg)
 * - Berat: numeric input, kg suffix
 *
 * Step indicator at bottom: [1/3: Timbang]
 */
@Composable
fun WeightEntryScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    viewModel: WeighViewModel? = null
) {
    val context = LocalContext.current

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Kamera siap digunakan", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Izin kamera ditolak — foto tidak tersedia", Toast.LENGTH_LONG).show()
        }
    }

    // SMS permission launcher (Android 13+)
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Izin SMS ditolak — notifikasi SMS tidak akan dikirim", Toast.LENGTH_LONG).show()
        }
    }

    // WeighIn state — synced with ViewModel
    val customerName by viewModel?.customerName?.collectAsState() ?: remember { mutableStateOf("") }
    val weightGrams by viewModel?.weightGrams?.collectAsState() ?: remember { mutableStateOf(0L) }

    var weightInput by remember { mutableStateOf("") }
    var weightInGrams by remember { mutableStateOf(0L) }
    var showDropdown by remember { mutableStateOf(false) }

    // Customer search suggestions from repository via ViewModel
    val searchResults by remember(customerName) {
        if (viewModel != null && customerName.length >= 2) {
            viewModel.searchCustomers(customerName)
        } else {
            kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        }
    }.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.L)
    ) {
        Spacer(modifier = Modifier.height(Spacing.M))

        // Screen title
        Text(
            text = "Timbang Barang",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(Spacing.L))

        // Step 1 content
        // Nasabah name field
        Text(
            text = "Nama Nasabah",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.XXS))
        Box {
            OutlinedTextField(
                value = customerName,
                onValueChange = {
                    viewModel?.setCustomerName(it)
                    showDropdown = it.length >= 2
                },
                placeholder = {
                    Text(
                        text = "\uD83D\uDD0D Ketik atau pilih nama...",
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

            // Dropdown for customer suggestions
            if (showDropdown && searchResults.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 56.dp),
                    shape = RoundedCornerShape(Spacing.XXS),
                    color = BG_TERTIARY,
                    shadowElevation = 8.dp
                ) {
                    LazyColumn {
                        items(searchResults) { customer ->
                            Text(
                                text = customer.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel?.setCustomerName(customer.name)
                                        viewModel?.setCustomerPhone(customer.phoneNumber)
                                        showDropdown = false
                                    }
                                    .padding(Spacing.M)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.L))

        // Phone field
        Text(
            text = "No. Telepon (opsional)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.XXS))
        val customerPhone = viewModel?.customerPhone?.collectAsState()?.value ?: ""
        OutlinedTextField(
            value = customerPhone,
            onValueChange = { viewModel?.setCustomerPhone(it) },
            placeholder = { Text("08xxxxxxxxx") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Spacing.XXS),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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

        Spacer(modifier = Modifier.height(Spacing.L))

        // Photo placeholder (optional / mandatory if >10kg)
        Text(
            text = "Foto Barang (opsional)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.XXS))
        Box(
            modifier = Modifier
                .size(100.dp)
                .border(
                    width = 2.dp,
                    color = Color(0xFF303050),
                    shape = RoundedCornerShape(Spacing.XXS)
                )
                .background(BG_TERTIARY, RoundedCornerShape(Spacing.XXS))
                .clickable {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "\uD83D\uDCF7", fontSize = 28.sp)  // 📷
                Text(
                    text = "Tap untuk foto",
                    style = MaterialTheme.typography.bodySmall,
                    color = TEXT_SECONDARY
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.L))

        // Weight input
        Text(
            text = "Berat (kg)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.XXS))
        OutlinedTextField(
            value = weightInput,
            onValueChange = { input ->
                weightInput = input
                val kg = input.toFloatOrNull() ?: 0f
                weightInGrams = (kg * 1000).toLong()
                viewModel?.setWeightGrams(weightInGrams)
            },
            placeholder = { Text("0.0") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            suffix = {
                Text(
                    text = "kg",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TEXT_SECONDARY
                )
            },
            shape = RoundedCornerShape(Spacing.XXS),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

        Spacer(modifier = Modifier.weight(1f))

        // Step indicator + bottom bar
        Text(
            text = "[1/3: Timbang]",
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
                    text = "\u2190 Kembali",  // ← Kembali
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
                enabled = weightInGrams > 0
            ) {
                Text(
                    text = "[1/3: Lanjut \u2192]",  // [1/3: Lanjut →]
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.M))
    }
}
