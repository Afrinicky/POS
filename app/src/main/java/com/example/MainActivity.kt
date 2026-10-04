package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanner.BarcodeScannerDialog
import com.example.ui.PosScreen
import com.example.ui.PosViewModel
import com.example.ui.components.AddProductDialog
import com.example.ui.components.AdjustStockDialog
import com.example.ui.components.LowStockAlertDialog
import com.example.ui.components.MobileMoneyPaymentDialog
import com.example.ui.components.PrintBarcodeDialog
import com.example.ui.components.ReceiptDialog
import com.example.ui.screens.AiAdvisorScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.PosTerminalScreen
import com.example.ui.screens.SalesHistoryScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PosViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val currentScreen by viewModel.currentScreen
                val products by viewModel.productsList.collectAsStateWithLifecycle()
                val lowStockProducts by viewModel.lowStockProducts.collectAsStateWithLifecycle()
                val cartItems by viewModel.cartItems

                // Handle Toast alerts
                LaunchedEffect(viewModel.toastMessage.value) {
                    viewModel.toastMessage.value?.let { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        viewModel.toastMessage.value = null
                    }
                }

                // BackHandler
                BackHandler(enabled = currentScreen != PosScreen.TERMINAL || viewModel.isScannerOpen.value || viewModel.isMoMoDialogOpen.value || viewModel.isLowStockAlertModalOpen.value) {
                    if (viewModel.isScannerOpen.value) {
                        viewModel.isScannerOpen.value = false
                    } else if (viewModel.isMoMoDialogOpen.value) {
                        viewModel.isMoMoDialogOpen.value = false
                    } else if (viewModel.isLowStockAlertModalOpen.value) {
                        viewModel.isLowStockAlertModalOpen.value = false
                    } else if (currentScreen != PosScreen.TERMINAL) {
                        viewModel.switchScreen(PosScreen.TERMINAL)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = when (currentScreen) {
                                        PosScreen.TERMINAL -> "Ghana Retail POS Terminal"
                                        PosScreen.INVENTORY -> "Store Inventory & Stock"
                                        PosScreen.SALES -> "Sales Records"
                                        PosScreen.AI_ADVISOR -> "Unpaid AI Advisor"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            },
                            actions = {
                                // Low Stock Alert Icon with Badge
                                if (lowStockProducts.isNotEmpty()) {
                                    BadgedBox(
                                        badge = {
                                            Badge(containerColor = Color(0xFFD97706)) {
                                                Text("${lowStockProducts.size}")
                                            }
                                        }
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.isLowStockAlertModalOpen.value = true },
                                            modifier = Modifier.testTag("top_bar_low_stock_alert_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Low Stock Alerts",
                                                tint = Color(0xFFD97706)
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.isScannerOpen.value = true },
                                    modifier = Modifier.testTag("top_app_bar_scanner_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Scan Barcode"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            NavigationBarItem(
                                selected = currentScreen == PosScreen.TERMINAL,
                                onClick = { viewModel.switchScreen(PosScreen.TERMINAL) },
                                icon = {
                                    val count = cartItems.sumOf { it.quantity }
                                    if (count > 0) {
                                        BadgedBox(
                                            badge = { Badge { Text("$count") } }
                                        ) {
                                            Icon(imageVector = Icons.Default.PointOfSale, contentDescription = "Terminal")
                                        }
                                    } else {
                                        Icon(imageVector = Icons.Default.PointOfSale, contentDescription = "Terminal")
                                    }
                                },
                                label = { Text("POS") },
                                modifier = Modifier.testTag("nav_pos_terminal")
                            )

                            NavigationBarItem(
                                selected = currentScreen == PosScreen.INVENTORY,
                                onClick = { viewModel.switchScreen(PosScreen.INVENTORY) },
                                icon = {
                                    val count = lowStockProducts.size
                                    if (count > 0) {
                                        BadgedBox(
                                            badge = { Badge(containerColor = Color(0xFFD97706)) { Text("$count") } }
                                        ) {
                                            Icon(imageVector = Icons.Default.Inventory, contentDescription = "Inventory")
                                        }
                                    } else {
                                        Icon(imageVector = Icons.Default.Inventory, contentDescription = "Inventory")
                                    }
                                },
                                label = { Text("Inventory") },
                                modifier = Modifier.testTag("nav_inventory")
                            )

                            NavigationBarItem(
                                selected = currentScreen == PosScreen.SALES,
                                onClick = { viewModel.switchScreen(PosScreen.SALES) },
                                icon = {
                                    Icon(imageVector = Icons.Default.ReceiptLong, contentDescription = "Sales")
                                },
                                label = { Text("Sales") },
                                modifier = Modifier.testTag("nav_sales")
                            )

                            NavigationBarItem(
                                selected = currentScreen == PosScreen.AI_ADVISOR,
                                onClick = { viewModel.switchScreen(PosScreen.AI_ADVISOR) },
                                icon = {
                                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "AI")
                                },
                                label = { Text("AI Advisor") },
                                modifier = Modifier.testTag("nav_ai_advisor")
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentScreen) {
                            PosScreen.TERMINAL -> {
                                PosTerminalScreen(
                                    viewModel = viewModel,
                                    onOpenScanner = { viewModel.isScannerOpen.value = true },
                                    onOpenLowStockAlertModal = { viewModel.isLowStockAlertModalOpen.value = true },
                                    onOpenMoMoDirectDialog = { viewModel.isMoMoDialogOpen.value = true }
                                )
                            }
                            PosScreen.INVENTORY -> {
                                InventoryScreen(
                                    viewModel = viewModel,
                                    onOpenAddProductDialog = { viewModel.isAddProductOpen.value = true },
                                    onOpenAdjustStockDialog = { prod -> viewModel.editingProductForStock.value = prod },
                                    onOpenBarcodeLookup = { viewModel.isScannerOpen.value = true },
                                    onOpenPrintBarcode = { prod -> viewModel.productToPrintBarcode.value = prod }
                                )
                            }
                            PosScreen.SALES -> {
                                SalesHistoryScreen(
                                    viewModel = viewModel,
                                    onViewReceipt = { sale -> viewModel.activeReceipt.value = sale }
                                )
                            }
                            PosScreen.AI_ADVISOR -> {
                                AiAdvisorScreen(viewModel = viewModel)
                            }
                        }

                        // Mobile Money Direct Prompt Payment Flow
                        if (viewModel.isMoMoDialogOpen.value) {
                            MobileMoneyPaymentDialog(
                                totalAmount = viewModel.totalAmount.value,
                                onPaymentConfirmed = { network, phone, reference ->
                                    viewModel.processCheckout(
                                        paymentMethod = "Mobile Money",
                                        amountTendered = viewModel.totalAmount.value,
                                        momoNetwork = network,
                                        customerPhone = phone,
                                        momoRef = reference
                                    )
                                    viewModel.isMoMoDialogOpen.value = false
                                },
                                onDismiss = { viewModel.isMoMoDialogOpen.value = false }
                            )
                        }

                        // Low Stock Alert Dialog
                        if (viewModel.isLowStockAlertModalOpen.value) {
                            LowStockAlertDialog(
                                lowStockProducts = lowStockProducts,
                                onQuickRestock = { product, delta ->
                                    viewModel.adjustStock(product.sku, delta, "RESTOCK", "Low Stock Alert Restock")
                                },
                                onOpenAdjustStock = { product ->
                                    viewModel.editingProductForStock.value = product
                                    viewModel.isLowStockAlertModalOpen.value = false
                                },
                                onDismiss = { viewModel.isLowStockAlertModalOpen.value = false }
                            )
                        }

                        // Camera Barcode Scanner Modal
                        if (viewModel.isScannerOpen.value) {
                            BarcodeScannerDialog(
                                sampleProducts = products,
                                onBarcodeScanned = { code -> viewModel.handleBarcodeScanned(code) },
                                onDismiss = { viewModel.isScannerOpen.value = false }
                            )
                        }

                        // Digital Thermal Receipt Modal
                        val receipt = viewModel.activeReceipt.value
                        if (receipt != null) {
                            ReceiptDialog(
                                sale = receipt,
                                amountTendered = viewModel.lastAmountTendered.doubleValue,
                                onDismiss = { viewModel.activeReceipt.value = null }
                            )
                        }

                        // Print Barcode Label Modal
                        val productForBarcode = viewModel.productToPrintBarcode.value
                        if (productForBarcode != null) {
                            PrintBarcodeDialog(
                                product = productForBarcode,
                                onDismiss = { viewModel.productToPrintBarcode.value = null }
                            )
                        }

                        // Add Product Dialog
                        if (viewModel.isAddProductOpen.value) {
                            AddProductDialog(
                                initialBarcode = viewModel.prefilledBarcodeForNewProduct.value,
                                onProductSaved = { newProduct -> viewModel.saveProduct(newProduct) },
                                onRequestBarcodeScan = { viewModel.isScannerOpen.value = true },
                                onDismiss = {
                                    viewModel.isAddProductOpen.value = false
                                    viewModel.prefilledBarcodeForNewProduct.value = null
                                }
                            )
                        }

                        // Stock Adjustment Dialog
                        val editingProduct = viewModel.editingProductForStock.value
                        if (editingProduct != null) {
                            AdjustStockDialog(
                                product = editingProduct,
                                onConfirm = { sku, delta, reason, notes ->
                                    viewModel.adjustStock(sku, delta, reason, notes)
                                },
                                onDismiss = { viewModel.editingProductForStock.value = null }
                            )
                        }
                    }
                }
            }
        }
    }
}
