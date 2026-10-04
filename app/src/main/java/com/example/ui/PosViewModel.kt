package com.example.ui

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.CartItem
import com.example.data.model.Product
import com.example.data.model.SaleRecord
import com.example.data.repository.InventoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PosScreen {
    TERMINAL,
    INVENTORY,
    SALES,
    AI_ADVISOR
}

class PosViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = InventoryRepository(
        database.productDao(),
        database.stockTransactionDao(),
        database.saleRecordDao()
    )

    val productsList = repository.allProducts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val lowStockProducts = repository.lowStockProducts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val salesList = repository.allSales.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Navigation state
    val currentScreen = mutableStateOf(PosScreen.TERMINAL)

    // Cart State
    private val _cartItems = mutableStateOf<List<CartItem>>(emptyList())
    val cartItems: State<List<CartItem>> = _cartItems

    private val _discountPct = mutableDoubleStateOf(0.0)
    val discountPct: State<Double> = _discountPct

    private val _subtotal = mutableDoubleStateOf(0.0)
    val subtotal: State<Double> = _subtotal

    private val _tax = mutableDoubleStateOf(0.0)
    val tax: State<Double> = _tax

    private val _discount = mutableDoubleStateOf(0.0)
    val discount: State<Double> = _discount

    private val _totalAmount = mutableDoubleStateOf(0.0)
    val totalAmount: State<Double> = _totalAmount

    // Dialog & UI states
    val isScannerOpen = mutableStateOf(false)
    val activeReceipt = mutableStateOf<SaleRecord?>(null)
    val lastAmountTendered = mutableDoubleStateOf(0.0)
    val isAddProductOpen = mutableStateOf(false)
    val isMoMoDialogOpen = mutableStateOf(false)
    val isLowStockAlertModalOpen = mutableStateOf(false)
    val editingProductForStock = mutableStateOf<Product?>(null)
    val productToPrintBarcode = mutableStateOf<Product?>(null)
    val prefilledBarcodeForNewProduct = mutableStateOf<String?>(null)
    val toastMessage = mutableStateOf<String?>(null)

    fun switchScreen(screen: PosScreen) {
        currentScreen.value = screen
    }

    fun addToCart(product: Product) {
        if (product.stockLevel <= 0) {
            toastMessage.value = "⚠️ ${product.name} is out of stock! Restock needed."
            return
        }
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.sku == product.sku }
        if (index >= 0) {
            val existing = currentList[index]
            if (existing.quantity < product.stockLevel) {
                currentList[index] = existing.copy(quantity = existing.quantity + 1)
            } else {
                toastMessage.value = "Cannot add more. Max stock available is ${product.stockLevel} ${product.unit}."
            }
        } else {
            currentList.add(CartItem(product = product, quantity = 1))
        }
        _cartItems.value = currentList
        recalculateTotals()
    }

    fun removeFromCart(sku: String) {
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.sku == sku }
        if (index >= 0) {
            val existing = currentList[index]
            if (existing.quantity > 1) {
                currentList[index] = existing.copy(quantity = existing.quantity - 1)
            } else {
                currentList.removeAt(index)
            }
            _cartItems.value = currentList
            recalculateTotals()
        }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _discountPct.doubleValue = 0.0
        recalculateTotals()
    }

    fun applyDiscountPct(pct: Double) {
        _discountPct.doubleValue = pct
        recalculateTotals()
    }

    private fun recalculateTotals() {
        val sub = _cartItems.value.sumOf { it.lineTotal }
        val disc = sub * _discountPct.doubleValue
        val taxableSub = (sub - disc).coerceAtLeast(0.0)
        val taxAmount = taxableSub * 0.03 // 3% Ghana Flat Rate Levy
        val total = taxableSub + taxAmount

        _subtotal.doubleValue = sub
        _discount.doubleValue = disc
        _tax.doubleValue = taxAmount
        _totalAmount.doubleValue = total
    }

    fun handleBarcodeScanned(code: String) {
        isScannerOpen.value = false
        val trimmed = code.trim()
        viewModelScope.launch {
            val product = repository.findProductByBarcode(trimmed) ?: repository.getProductBySku(trimmed)
            if (product != null) {
                if (currentScreen.value == PosScreen.TERMINAL) {
                    addToCart(product)
                    toastMessage.value = "Scanned & added: ${product.name}"
                } else {
                    editingProductForStock.value = product
                    toastMessage.value = "Found: ${product.name} (Stock: ${product.stockLevel})"
                }
            } else {
                toastMessage.value = "No product found with barcode $trimmed. Creating new..."
                prefilledBarcodeForNewProduct.value = trimmed
                isAddProductOpen.value = true
            }
        }
    }

    fun processCheckout(
        paymentMethod: String,
        amountTendered: Double,
        momoNetwork: String? = null,
        customerPhone: String? = null,
        momoRef: String? = null
    ) {
        val items = _cartItems.value
        if (items.isEmpty()) return

        viewModelScope.launch {
            val result = repository.completeCheckout(
                items = items,
                subtotal = _subtotal.doubleValue,
                tax = _tax.doubleValue,
                discount = _discount.doubleValue,
                totalAmount = _totalAmount.doubleValue,
                paymentMethod = paymentMethod,
                momoNetwork = momoNetwork,
                customerPhone = customerPhone,
                momoReference = momoRef
            )

            result.onSuccess { saleRecord ->
                lastAmountTendered.doubleValue = amountTendered
                activeReceipt.value = saleRecord
                clearCart()

                // Check if any product is now low or out of stock
                val nowLowStock = lowStockProducts.value
                if (nowLowStock.any { it.isOutOfStock }) {
                    toastMessage.value = "⚠️ Alert: Some items are now OUT OF STOCK! Check alerts."
                }
            }.onFailure { err ->
                toastMessage.value = "Checkout failed: ${err.message}"
            }
        }
    }

    fun saveProduct(product: Product) {
        viewModelScope.launch {
            repository.saveProduct(product)
            isAddProductOpen.value = false
            prefilledBarcodeForNewProduct.value = null
            toastMessage.value = "Saved ${product.name}"
        }
    }

    fun adjustStock(sku: String, delta: Int, reason: String, notes: String = "") {
        viewModelScope.launch {
            val result = repository.adjustStock(sku, delta, reason, notes)
            result.onSuccess { newStock ->
                editingProductForStock.value = null
                toastMessage.value = "Stock updated for $sku: $newStock"
            }.onFailure { err ->
                toastMessage.value = "Stock adjustment failed: ${err.message}"
            }
        }
    }
}
