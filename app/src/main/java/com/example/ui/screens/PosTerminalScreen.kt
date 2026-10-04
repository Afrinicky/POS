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
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.model.CartItem
import com.example.data.model.Product
import com.example.ui.PosViewModel

@Composable
fun PosTerminalScreen(
    viewModel: PosViewModel,
    onOpenScanner: () -> Unit,
    onOpenLowStockAlertModal: () -> Unit,
    onOpenMoMoDirectDialog: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showCartSheet by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }

    val products by viewModel.productsList.collectAsStateWithLifecycle()
    val lowStockList by viewModel.lowStockProducts.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems
    val subtotal by viewModel.subtotal
    val tax by viewModel.tax
    val discount by viewModel.discount
    val totalAmount by viewModel.totalAmount

    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        products.filter { p ->
            val matchesCategory = selectedCategory == "All" || p.category.equals(selectedCategory, true)
            val matchesSearch = searchQuery.isBlank() ||
                    p.name.contains(searchQuery, ignoreCase = true) ||
                    p.sku.contains(searchQuery, ignoreCase = true) ||
                    (p.barcode != null && p.barcode.contains(searchQuery))
            matchesCategory && matchesSearch
        }
    }

    val categories = listOf("All", "Provisions", "Pharmaceuticals", "Groceries", "Cosmetics")

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Low Stock Alert Banner (Persistent if any items low/out)
            if (lowStockList.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onOpenLowStockAlertModal() }
                        .testTag("terminal_low_stock_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = "Alert", tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LOW STOCK ALERT: ${lowStockList.size} items require restocking!",
                                color = Color(0xFF92400E),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "View ➜",
                            color = Color(0xFFB45309),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Search Bar & Scan Barcode Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search item, SKU, or UPC barcode") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Text("✕", fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pos_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = onOpenScanner,
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("pos_scan_barcode_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = "Scan")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Ghanaian Retail Category Filters
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        modifier = Modifier.testTag("cat_filter_$cat")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Products Catalog List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("pos_product_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = if (cartItems.isNotEmpty()) 90.dp else 16.dp)
            ) {
                items(filteredProducts, key = { it.sku }) { product ->
                    val inCart = cartItems.find { it.product.sku == product.sku }?.quantity ?: 0
                    ProductCatalogItemCard(
                        product = product,
                        inCartCount = inCart,
                        onAddToCart = { viewModel.addToCart(product) },
                        onRemoveFromCart = { viewModel.removeFromCart(product.sku) }
                    )
                }

                if (filteredProducts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No matching items found.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Floating Bottom Cart Bar (when items are in cart)
        if (cartItems.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .testTag("floating_cart_bar"),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.clickable { showCartSheet = true }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "Cart",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${cartItems.sumOf { it.quantity }} items in cart",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "Total: GH₵ ${String.format("%.2f", totalAmount)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row {
                        OutlinedButton(
                            onClick = { showCartSheet = true },
                            modifier = Modifier.testTag("view_cart_button")
                        ) {
                            Text("View Cart")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { showCheckoutDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("checkout_button")
                        ) {
                            Text("Pay GH₵ ${String.format("%.2f", totalAmount)}")
                        }
                    }
                }
            }
        }

        // Full Cart Modal Sheet
        if (showCartSheet) {
            CartDetailsDialog(
                cartItems = cartItems,
                subtotal = subtotal,
                tax = tax,
                discount = discount,
                totalAmount = totalAmount,
                onAddQty = { viewModel.addToCart(it.product) },
                onMinusQty = { viewModel.removeFromCart(it.product.sku) },
                onApplyDiscount = { viewModel.applyDiscountPct(it) },
                onClearCart = { viewModel.clearCart() },
                onProceedToCheckout = {
                    showCartSheet = false
                    showCheckoutDialog = true
                },
                onDismiss = { showCartSheet = false }
            )
        }

        // Checkout & Payment Method Dialog (Ghana MoMo & Cash)
        if (showCheckoutDialog) {
            CheckoutPaymentDialog(
                totalAmount = totalAmount,
                onOpenMoMoDirectPayment = {
                    showCheckoutDialog = false
                    onOpenMoMoDirectDialog()
                },
                onProcessPayment = { paymentMethod, amountTendered, momoRef ->
                    viewModel.processCheckout(
                        paymentMethod = paymentMethod,
                        amountTendered = amountTendered,
                        momoRef = momoRef
                    )
                    showCheckoutDialog = false
                },
                onDismiss = { showCheckoutDialog = false }
            )
        }
    }
}

@Composable
fun ProductCatalogItemCard(
    product: Product,
    inCartCount: Int,
    onAddToCart: () -> Unit,
    onRemoveFromCart: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_card_${product.sku}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Badge
            val (badgeBg, badgeEmoji) = when (product.category.lowercase()) {
                "pharmaceuticals" -> Color(0xFFE0F2FE) to "💊"
                "provisions" -> Color(0xFFFEF3C7) to "🥫"
                "cosmetics" -> Color(0xFFFFE4E6) to "💄"
                else -> Color(0xFFDCFCE7) to "🛒"
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(badgeBg, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = badgeEmoji, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = product.sku,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("•", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val stockColor = when {
                        product.isOutOfStock -> Color(0xFFEF4444)
                        product.isLowStock -> Color(0xFFD97706)
                        else -> Color(0xFF16A34A)
                    }
                    val stockLabel = when {
                        product.isOutOfStock -> "Out of Stock"
                        product.isLowStock -> "Low: ${product.stockLevel} left"
                        else -> "${product.stockLevel} ${product.unit}"
                    }
                    Text(
                        text = stockLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = stockColor
                    )
                    if (!product.expiryDate.isNullOrBlank()) {
                        Text("•", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "Exp: ${product.expiryDate.take(7)}",
                            fontSize = 11.sp,
                            color = Color(0xFFDC2626)
                        )
                    }
                }

                Text(
                    text = "GH₵ ${String.format("%.2f", product.price)} / ${product.unit}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Stepper or Add to Cart Button
            if (inCartCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    IconButton(
                        onClick = onRemoveFromCart,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = "$inCartCount",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    IconButton(
                        onClick = onAddToCart,
                        enabled = !product.isOutOfStock && inCartCount < product.stockLevel,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                Button(
                    onClick = onAddToCart,
                    enabled = !product.isOutOfStock,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("add_btn_${product.sku}")
                ) {
                    Text(if (product.isOutOfStock) "Sold Out" else "+ Add")
                }
            }
        }
    }
}

@Composable
fun CartDetailsDialog(
    cartItems: List<CartItem>,
    subtotal: Double,
    tax: Double,
    discount: Double,
    totalAmount: Double,
    onAddQty: (CartItem) -> Unit,
    onMinusQty: (CartItem) -> Unit,
    onApplyDiscount: (Double) -> Unit,
    onClearCart: () -> Unit,
    onProceedToCheckout: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Current Order Cart", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                TextButton(onClick = onClearCart) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    items(cartItems) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.product.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(
                                    text = "GH₵ ${String.format("%.2f", item.product.price)} each",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onMinusQty(item) }, modifier = Modifier.size(28.dp)) {
                                    Icon(imageVector = Icons.Default.Remove, contentDescription = "Minus", modifier = Modifier.size(14.dp))
                                }
                                Text(text = "${item.quantity}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
                                IconButton(
                                    onClick = { onAddQty(item) },
                                    enabled = item.quantity < item.product.stockLevel,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Plus", modifier = Modifier.size(14.dp))
                                }
                            }

                            Text(
                                text = "GH₵ ${String.format("%.2f", item.lineTotal)}",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .width(76.dp)
                                    .padding(start = 8.dp),
                                fontSize = 13.sp
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Discount presets
                Text(text = "Customer Discount", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.0 to "0%", 0.05 to "5%", 0.10 to "10%").forEach { (pct, label) ->
                        FilterChip(
                            selected = false,
                            onClick = { onApplyDiscount(pct) },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal:", fontSize = 13.sp)
                    Text("GH₵ ${String.format("%.2f", subtotal)}", fontSize = 13.sp)
                }
                if (discount > 0.0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Discount:", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        Text("-GH₵ ${String.format("%.2f", discount)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tax / Levy (3% Flat):", fontSize = 13.sp)
                    Text("GH₵ ${String.format("%.2f", tax)}", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Due:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "GH₵ ${String.format("%.2f", totalAmount)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onProceedToCheckout,
                modifier = Modifier.testTag("cart_proceed_checkout")
            ) {
                Text("Proceed to Payment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Back to Shop")
            }
        }
    )
}

@Composable
fun CheckoutPaymentDialog(
    totalAmount: Double,
    onOpenMoMoDirectPayment: () -> Unit,
    onProcessPayment: (paymentMethod: String, amountTendered: Double, momoRef: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf("Cash") }
    var cashTenderedText by remember { mutableStateOf(String.format("%.2f", totalAmount)) }
    var manualRefText by remember { mutableStateOf("") }

    val tenderedDouble = cashTenderedText.toDoubleOrNull() ?: totalAmount
    val changeDue = (tenderedDouble - totalAmount).coerceAtLeast(0.0)

    val paymentOptions = listOf(
        "Cash" to Icons.Default.LocalAtm,
        "Store Credit" to Icons.Default.Person,
        "Card / POS" to Icons.Default.CreditCard
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Checkout & Payment (Ghana)", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Total Due Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("TOTAL AMOUNT DUE", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = "GH₵ ${String.format("%.2f", totalAmount)}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // PRIMARY ACTION: DIRECT MOBILE MONEY PUSH TO PHONE
                Button(
                    onClick = onOpenMoMoDirectPayment,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("open_direct_momo_flow_button")
                ) {
                    Icon(imageVector = Icons.Default.PhoneAndroid, contentDescription = "MoMo", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pay with MoMo (Direct Prompt to Phone)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                HorizontalDivider()

                // Offline Cash & In-store alternatives
                Text("Or select in-store payment method:", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    paymentOptions.forEach { (method, icon) ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = { Text(method, fontSize = 11.sp) },
                            leadingIcon = { Icon(imageVector = icon, contentDescription = method, modifier = Modifier.size(14.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Cash presets in Ghanaian Cedis
                if (selectedMethod == "Cash") {
                    Text("Cash Tendered (GH₵ Notes)", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            totalAmount to "Exact",
                            50.0 to "GH₵50",
                            100.0 to "GH₵100",
                            200.0 to "GH₵200"
                        ).forEach { (amount, label) ->
                            OutlinedButton(
                                onClick = { cashTenderedText = String.format("%.2f", amount) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label, fontSize = 11.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = cashTenderedText,
                        onValueChange = { cashTenderedText = it },
                        label = { Text("Tendered Amount (GH₵)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Change Due
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Change to Return:", fontWeight = FontWeight.Medium)
                            Text(
                                text = "GH₵ ${String.format("%.2f", changeDue)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else if (selectedMethod == "Store Credit") {
                    OutlinedTextField(
                        value = manualRefText,
                        onValueChange = { manualRefText = it },
                        label = { Text("Customer Name / Account Reference") },
                        placeholder = { Text("e.g. Papa Kofi / Account #44") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onProcessPayment(selectedMethod, tenderedDouble, manualRefText.ifBlank { null })
                },
                modifier = Modifier.testTag("confirm_payment_button")
            ) {
                Text("Complete & Print Receipt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
