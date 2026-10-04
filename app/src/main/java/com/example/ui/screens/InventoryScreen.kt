package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Product
import com.example.ui.PosViewModel

@Composable
fun InventoryScreen(
    viewModel: PosViewModel,
    onOpenAddProductDialog: () -> Unit,
    onOpenAdjustStockDialog: (Product) -> Unit,
    onOpenBarcodeLookup: () -> Unit,
    onOpenPrintBarcode: (Product) -> Unit
) {
    val products by viewModel.productsList.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var filterLowStockOnly by remember { mutableStateOf(false) }

    val totalProducts = products.size
    val totalUnits = products.sumOf { it.stockLevel }
    val lowStockCount = products.count { it.isLowStock }
    val totalValuation = products.sumOf { it.stockLevel * it.price }

    val filteredList = remember(products, searchQuery, selectedCategory, filterLowStockOnly) {
        products.filter { p ->
            val matchesCategory = selectedCategory == "All" || p.category.equals(selectedCategory, true)
            val matchesSearch = searchQuery.isBlank() ||
                    p.name.contains(searchQuery, ignoreCase = true) ||
                    p.sku.contains(searchQuery, ignoreCase = true) ||
                    (p.barcode != null && p.barcode.contains(searchQuery))
            val matchesLowStock = !filterLowStockOnly || p.isLowStock
            matchesCategory && matchesSearch && matchesLowStock
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenAddProductDialog,
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("add_product_fab")
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Product", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Inventory Summary KPI Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    KpiStat(label = "Catalog", value = "$totalProducts items")
                    KpiStat(label = "Total Units", value = "$totalUnits")
                    KpiStat(
                        label = "Low Stock",
                        value = "$lowStockCount",
                        valueColor = if (lowStockCount > 0) Color(0xFFD97706) else Color(0xFF16A34A)
                    )
                    KpiStat(label = "Valuation", value = "GH₵ ${String.format("%.0f", totalValuation)}")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Low Stock Warning Banner
            if (lowStockCount > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { filterLowStockOnly = !filterLowStockOnly }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = "Alert", tint = Color(0xFFD97706))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$lowStockCount items at or below reorder threshold. Tap to ${if (filterLowStockOnly) "show all" else "filter"}.",
                            color = Color(0xFF92400E),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Search Bar & Scan Barcode for Quick Lookup
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter SKU, name, UPC") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Text("✕", fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("inventory_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = onOpenBarcodeLookup,
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = "Scan")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Category Filter Row
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("All", "Provisions", "Pharmaceuticals", "Groceries", "Cosmetics")) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat && !filterLowStockOnly,
                        onClick = {
                            selectedCategory = cat
                            filterLowStockOnly = false
                        },
                        label = { Text(cat) }
                    )
                }
                item {
                    FilterChip(
                        selected = filterLowStockOnly,
                        onClick = { filterLowStockOnly = !filterLowStockOnly },
                        label = { Text("⚠️ Low Stock ($lowStockCount)") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Inventory Items List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("inventory_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredList, key = { it.sku }) { product ->
                    InventoryProductCard(
                        product = product,
                        onAdjustStock = { onOpenAdjustStockDialog(product) },
                        onPrintBarcode = { onOpenPrintBarcode(product) }
                    )
                }

                if (filteredList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No inventory items match filter.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryProductCard(
    product: Product,
    onAdjustStock: () -> Unit,
    onPrintBarcode: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inventory_item_${product.sku}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "SKU: ${product.sku} | ${product.category} | UPC: ${product.barcode ?: "Generated"}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!product.expiryDate.isNullOrBlank()) {
                        Text(
                            text = "EXP: ${product.expiryDate}",
                            fontSize = 11.sp,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Current stock badge
                val stockBadgeColor = when {
                    product.isOutOfStock -> Color(0xFFEF4444)
                    product.isLowStock -> Color(0xFFD97706)
                    else -> Color(0xFF16A34A)
                }
                val stockBadgeBg = when {
                    product.isOutOfStock -> Color(0xFFFEE2E2)
                    product.isLowStock -> Color(0xFFFEF3C7)
                    else -> Color(0xFFDCFCE7)
                }

                Box(
                    modifier = Modifier
                        .background(stockBadgeBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${product.stockLevel} ${product.unit}",
                        color = stockBadgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pricing & Margins and Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text("Price", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("GH₵ ${String.format("%.2f", product.price)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Column {
                        Text("Cost", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("GH₵ ${String.format("%.2f", product.costPrice)}", fontSize = 13.sp)
                    }
                    Column {
                        Text("Margin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${String.format("%.1f", product.grossMarginPct)}%",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Option to print barcode label
                    OutlinedButton(
                        onClick = onPrintBarcode,
                        modifier = Modifier.testTag("print_barcode_btn_${product.sku}")
                    ) {
                        Icon(imageVector = Icons.Default.QrCode, contentDescription = "Print Barcode", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Barcode", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onAdjustStock,
                        modifier = Modifier.testTag("adjust_stock_btn_${product.sku}")
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Adjust", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stock", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiStat(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.onSurface
        )
    }
}
