package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiAdvisorService
import com.example.data.model.Product
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun AddProductDialog(
    initialBarcode: String? = null,
    onProductSaved: (Product) -> Unit,
    onRequestBarcodeScan: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Provisions") }
    var priceText by remember { mutableStateOf("") }
    var costPriceText by remember { mutableStateOf("") }
    var stockText by remember { mutableStateOf("20") }
    var reorderText by remember { mutableStateOf("5") }
    var barcode by remember { mutableStateOf(initialBarcode ?: "") }
    var unit by remember { mutableStateOf("pcs") }
    var expiryDate by remember { mutableStateOf("") }

    var isAiAnalyzing by remember { mutableStateOf(false) }
    var aiNote by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val categories = listOf("Provisions", "Pharmaceuticals", "Groceries", "Cosmetics", "General")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Product (Ghana Store)",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Product Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (sku.isBlank() && it.isNotBlank()) {
                            val prefix = when (category) {
                                "Provisions" -> "PRO"
                                "Pharmaceuticals" -> "PHA"
                                "Cosmetics" -> "COS"
                                "Groceries" -> "GRO"
                                else -> "GEN"
                            }
                            sku = "$prefix-${Random.nextInt(100, 999)}"
                        }
                        if (barcode.isBlank() && sku.isNotBlank()) {
                            barcode = Product.generateBarcode(sku)
                        }
                    },
                    label = { Text("Product Name *") },
                    placeholder = { Text("e.g. Paracetamol 500mg or Milo 400g") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_product_name_input"),
                    singleLine = true
                )

                // Cost & Price in GH₵
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = costPriceText,
                        onValueChange = { costPriceText = it },
                        label = { Text("Cost (GH₵)") },
                        placeholder = { Text("15.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_product_cost_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Price (GH₵) *") },
                        placeholder = { Text("22.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_product_price_input"),
                        singleLine = true
                    )
                }

                // AI Auto-Categorize & Pricing Assistant Button (Unpaid AI)
                FilledTonalButton(
                    onClick = {
                        if (name.isBlank()) {
                            errorMessage = "Enter product name first for Ghanaian AI classification."
                            return@FilledTonalButton
                        }
                        isAiAnalyzing = true
                        errorMessage = null
                        coroutineScope.launch {
                            val cost = costPriceText.toDoubleOrNull() ?: 0.0
                            val suggestion = AiAdvisorService.classifyProductWithAi(name, cost)
                            category = suggestion.category
                            if (priceText.isBlank() || priceText == "0.0") {
                                priceText = suggestion.suggestedPrice.toString()
                            }
                            reorderText = suggestion.suggestedReorderThreshold.toString()
                            unit = suggestion.unit
                            aiNote = "${suggestion.notes} (Margin: ${suggestion.targetMarginPct}%)"
                            isAiAnalyzing = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_categorize_button"),
                    enabled = !isAiAnalyzing
                ) {
                    if (isAiAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Analyzing...")
                    } else {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "AI", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Auto-Categorize (Ghana Market)")
                    }
                }

                if (aiNote != null) {
                    Text(
                        text = aiNote!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Category Selection Chips
                Text(text = "Category", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = {
                                category = cat
                                val prefix = when (cat) {
                                    "Provisions" -> "PRO"
                                    "Pharmaceuticals" -> "PHA"
                                    "Cosmetics" -> "COS"
                                    "Groceries" -> "GRO"
                                    else -> "GEN"
                                }
                                sku = "$prefix-${Random.nextInt(100, 999)}"
                                if (barcode.isBlank() || barcode.startsWith("200")) {
                                    barcode = Product.generateBarcode(sku)
                                }
                            },
                            label = { Text(cat) }
                        )
                    }
                }

                // SKU & Unit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it.uppercase() },
                        label = { Text("SKU *") },
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("add_product_sku_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        placeholder = { Text("tin/blister/box") },
                        modifier = Modifier.weight(1.1f),
                        singleLine = true
                    )
                }

                // Stock & Reorder Alert
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = stockText,
                        onValueChange = { stockText = it },
                        label = { Text("Initial Stock *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_product_stock_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = reorderText,
                        onValueChange = { reorderText = it },
                        label = { Text("Reorder Alert") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Expiry Date (Essential for Pharmaceuticals & Dairy Provisions in Ghana)
                OutlinedTextField(
                    value = expiryDate,
                    onValueChange = { expiryDate = it },
                    label = { Text("Expiry Date (YYYY-MM-DD)") },
                    placeholder = { Text("e.g. 2027-12-31") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Barcode / UPC Field with Auto-Generate Barcode Button
                Column {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Barcode / UPC") },
                        trailingIcon = {
                            IconButton(onClick = onRequestBarcodeScan) {
                                Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_product_barcode_input"),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "No barcode? We auto-generate one.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                val s = if (sku.isNotBlank()) sku else "GH-${Random.nextInt(100, 999)}"
                                if (sku.isBlank()) sku = s
                                barcode = Product.generateBarcode(s)
                            },
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.QrCode, contentDescription = "Generate", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Generate Barcode", fontSize = 11.sp)
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || sku.isBlank() || priceText.isBlank()) {
                        errorMessage = "Please enter Name, SKU, and Price."
                        return@Button
                    }
                    val price = priceText.toDoubleOrNull() ?: run {
                        errorMessage = "Invalid price format."
                        return@Button
                    }
                    val costPrice = costPriceText.toDoubleOrNull() ?: 0.0
                    val stock = stockText.toIntOrNull() ?: 0
                    val reorder = reorderText.toIntOrNull() ?: 5
                    val finalBarcode = if (barcode.isNotBlank()) barcode else Product.generateBarcode(sku)

                    onProductSaved(
                        Product(
                            sku = sku.trim().uppercase(),
                            name = name.trim(),
                            category = category,
                            price = price,
                            costPrice = costPrice,
                            stockLevel = stock,
                            reorderThreshold = reorder,
                            barcode = finalBarcode,
                            unit = unit.trim().ifEmpty { "pcs" },
                            expiryDate = expiryDate.trim().ifEmpty { null }
                        )
                    )
                },
                modifier = Modifier.testTag("save_product_button")
            ) {
                Text("Save & Add to Store")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
