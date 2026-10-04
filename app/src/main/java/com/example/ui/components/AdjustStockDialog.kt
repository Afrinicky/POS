package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Product

@Composable
fun AdjustStockDialog(
    product: Product,
    onConfirm: (sku: String, delta: Int, reason: String, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    var deltaText by remember { mutableStateOf("10") }
    var isIncrement by remember { mutableStateOf(true) }
    var selectedReason by remember { mutableStateOf("RESTOCK") }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val reasons = listOf("RESTOCK", "AUDIT", "DAMAGE", "RETURN")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = "Adjust Inventory Level", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = "${product.name} (SKU: ${product.sku})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Current Stock: ${product.stockLevel} ${product.unit}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Operation Type Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isIncrement,
                        onClick = {
                            isIncrement = true
                            selectedReason = "RESTOCK"
                        },
                        label = { Text("+ Add Stock (Restock)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isIncrement,
                        onClick = {
                            isIncrement = false
                            selectedReason = "DAMAGE"
                        },
                        label = { Text("- Deduct Stock") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1, 5, 10, 25).forEach { qty ->
                        OutlinedButton(
                            onClick = { deltaText = qty.toString() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = if (isIncrement) "+$qty" else "-$qty", fontSize = 12.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = deltaText,
                    onValueChange = { deltaText = it },
                    label = { Text("Quantity to ${if (isIncrement) "Add" else "Deduct"}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adjust_stock_qty_input"),
                    singleLine = true
                )

                // Reason picker
                Text("Reason", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    reasons.forEach { r ->
                        FilterChip(
                            selected = selectedReason == r,
                            onClick = { selectedReason = r },
                            label = { Text(r, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Note / Supplier / Audit Memo (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = deltaText.toIntOrNull() ?: 0
                    if (amount <= 0) {
                        errorMsg = "Please enter a valid quantity greater than 0."
                        return@Button
                    }
                    val finalDelta = if (isIncrement) amount else -amount
                    if (!isIncrement && (product.stockLevel + finalDelta < 0)) {
                        errorMsg = "Cannot deduct more stock than available (${product.stockLevel})."
                        return@Button
                    }
                    onConfirm(product.sku, finalDelta, selectedReason, notes)
                },
                modifier = Modifier.testTag("confirm_adjust_stock_button")
            ) {
                Text("Update Stock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
