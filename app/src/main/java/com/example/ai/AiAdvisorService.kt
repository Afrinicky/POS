package com.example.ai

import com.example.BuildConfig
import com.example.data.model.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class InventoryHealthSummary(
    val healthScore: Int,
    val totalProducts: Int,
    val totalUnits: Int,
    val retailValuation: Double,
    val costValuation: Double,
    val grossProfit: Double,
    val averageMarginPct: Double,
    val lowStockCount: Int,
    val outOfStockCount: Int,
    val currency: String = "GH₵"
)

data class RestockItemAdvice(
    val sku: String,
    val name: String,
    val category: String,
    val currentStock: Int,
    val targetStock: Int,
    val orderQuantity: Int,
    val estimatedCost: Double,
    val priority: String, // "CRITICAL", "HIGH", "MEDIUM"
    val note: String
)

data class CategorySuggestion(
    val category: String,
    val suggestedPrice: Double,
    val targetMarginPct: Double,
    val suggestedReorderThreshold: Int,
    val unit: String,
    val notes: String
)

object AiAdvisorService {

    private const val GEMINI_MODEL = "gemini-3.5-flash"
    const val CURRENCY = "GH₵"

    suspend fun getInventoryHealth(products: List<Product>): InventoryHealthSummary = withContext(Dispatchers.Default) {
        val totalProducts = products.size
        val totalUnits = products.sumOf { it.stockLevel }
        val retailValuation = products.sumOf { it.stockLevel * it.price }
        val costValuation = products.sumOf { it.stockLevel * it.costPrice }
        val grossProfit = retailValuation - costValuation
        val avgMargin = if (retailValuation > 0) (grossProfit / retailValuation) * 100.0 else 0.0

        val lowStockCount = products.count { it.isLowStock }
        val outOfStockCount = products.count { it.isOutOfStock }

        val penalty = (outOfStockCount * 15) + (lowStockCount * 5)
        val score = (100 - penalty).coerceIn(15, 100)

        InventoryHealthSummary(
            healthScore = score,
            totalProducts = totalProducts,
            totalUnits = totalUnits,
            retailValuation = retailValuation,
            costValuation = costValuation,
            grossProfit = grossProfit,
            averageMarginPct = avgMargin,
            lowStockCount = lowStockCount,
            outOfStockCount = outOfStockCount,
            currency = CURRENCY
        )
    }

    suspend fun generateRestockAdvice(products: List<Product>): List<RestockItemAdvice> = withContext(Dispatchers.Default) {
        val lowStock = products.filter { it.isLowStock }
        lowStock.map { p ->
            val isPharma = p.category.equals("Pharmaceuticals", ignoreCase = true)
            val isProvision = p.category.equals("Provisions", ignoreCase = true)
            val isCosmetic = p.category.equals("Cosmetics", ignoreCase = true)

            val targetStock = when {
                isPharma -> maxOf(20, p.reorderThreshold * 3)
                isProvision -> maxOf(24, p.reorderThreshold * 3)
                isCosmetic -> maxOf(10, p.reorderThreshold * 2)
                else -> maxOf(12, p.reorderThreshold * 2)
            }
            val orderQty = (targetStock - p.stockLevel).coerceAtLeast(1)
            val estCost = orderQty * p.costPrice

            val priority = when {
                p.stockLevel <= 0 -> "CRITICAL"
                p.stockLevel <= p.reorderThreshold / 2 -> "HIGH"
                else -> "MEDIUM"
            }

            val note = when {
                isPharma -> "Essential medicine/OTC item. Check FDA Ghana batch & expiry date."
                isProvision -> "High velocity Ghanaian provision item. Reorder weekly."
                isCosmetic -> "High margin beauty/skincare. Maintain safety stock."
                else -> "Standard retail grocery stock."
            }

            RestockItemAdvice(
                sku = p.sku,
                name = p.name,
                category = p.category,
                currentStock = p.stockLevel,
                targetStock = targetStock,
                orderQuantity = orderQty,
                estimatedCost = estCost,
                priority = priority,
                note = note
            )
        }.sortedByDescending { it.priority == "CRITICAL" }
    }

    suspend fun classifyProductWithAi(name: String, costPrice: Double): CategorySuggestion = withContext(Dispatchers.IO) {
        val geminiKey = BuildConfig.GEMINI_API_KEY
        if (geminiKey.isNotEmpty() && geminiKey != "MY_GEMINI_API_KEY" && !geminiKey.startsWith("AQ.")) {
            try {
                val prompt = "Classify retail item '$name' with cost GH₵$costPrice for a Ghanaian retail store. " +
                        "Respond JSON: {\"category\":\"Provisions|Pharmaceuticals|Groceries|Cosmetics|General\", " +
                        "\"suggested_price\": float, \"target_margin_pct\": float, \"reorder_threshold\": int, \"unit\": string, \"notes\": string}"
                val responseText = callGeminiApi(geminiKey, prompt)
                if (responseText != null) {
                    val cleanJson = responseText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    val json = JSONObject(cleanJson)
                    return@withContext CategorySuggestion(
                        category = json.optString("category", "Provisions"),
                        suggestedPrice = json.optDouble("suggested_price", if (costPrice > 0) costPrice * 1.35 else 20.0),
                        targetMarginPct = json.optDouble("target_margin_pct", 30.0),
                        suggestedReorderThreshold = json.optInt("reorder_threshold", 6),
                        unit = json.optString("unit", "pcs"),
                        notes = json.optString("notes", "Auto-classified for Ghana retail")
                    )
                }
            } catch (e: Exception) {
                // Fallback to local heuristic
            }
        }

        // Local Heuristic Classifier tailored for Ghana
        val lower = name.lowercase()
        val pharmaKeywords = listOf("paracetamol", "coartem", "artemether", "ors", "spirit", "dettol", "syrup", "tablet", "blister", "sachet", "vitamin", "lonart", "malaria", "capsule", "medicine", "pharmacy")
        val provisionKeywords = listOf("milo", "ideal milk", "milk", "geisha", "mackerel", "soap", "pepsodent", "sugar", "cerelac", "tea", "biscuit", "tin", "provision")
        val cosmeticKeywords = listOf("shea butter", "cocoa butter", "lotion", "dudu-osun", "black soap", "cream", "lipstick", "sunscreen", "spf", "cosmetic", "hair")
        val groceryKeywords = listOf("rice", "oil", "frytol", "gari", "flour", "yam", "beans", "food", "grocery")

        when {
            pharmaKeywords.any { lower.contains(it) } -> {
                val margin = 0.40
                val price = if (costPrice > 0) costPrice / (1.0 - margin) else 15.00
                CategorySuggestion(
                    category = "Pharmaceuticals",
                    suggestedPrice = String.format("%.2f", price).toDouble(),
                    targetMarginPct = 40.0,
                    suggestedReorderThreshold = 8,
                    unit = if (lower.contains("tablet") || lower.contains("blister")) "blister" else "box",
                    notes = "Licensed medicine / OTC. Track batch and expiration."
                )
            }
            provisionKeywords.any { lower.contains(it) } -> {
                val margin = 0.25
                val price = if (costPrice > 0) costPrice / (1.0 - margin) else 25.00
                CategorySuggestion(
                    category = "Provisions",
                    suggestedPrice = String.format("%.2f", price).toDouble(),
                    targetMarginPct = 25.0,
                    suggestedReorderThreshold = 10,
                    unit = if (lower.contains("milo") || lower.contains("milk")) "tin" else "pcs",
                    notes = "Fast-moving everyday Ghanaian provision."
                )
            }
            cosmeticKeywords.any { lower.contains(it) } -> {
                val margin = 0.50
                val price = if (costPrice > 0) costPrice / (1.0 - margin) else 35.00
                CategorySuggestion(
                    category = "Cosmetics",
                    suggestedPrice = String.format("%.2f", price).toDouble(),
                    targetMarginPct = 50.0,
                    suggestedReorderThreshold = 5,
                    unit = if (lower.contains("shea")) "jar" else "btl",
                    notes = "Popular Ghanaian beauty & skincare merchandise."
                )
            }
            groceryKeywords.any { lower.contains(it) } -> {
                val margin = 0.30
                val price = if (costPrice > 0) costPrice / (1.0 - margin) else 30.00
                CategorySuggestion(
                    category = "Groceries",
                    suggestedPrice = String.format("%.2f", price).toDouble(),
                    targetMarginPct = 30.0,
                    suggestedReorderThreshold = 6,
                    unit = if (lower.contains("rice")) "bag" else "pcs",
                    notes = "Kitchen foodstuff & staples."
                )
            }
            else -> {
                val margin = 0.35
                val price = if (costPrice > 0) costPrice / (1.0 - margin) else 20.00
                CategorySuggestion(
                    category = "General",
                    suggestedPrice = String.format("%.2f", price).toDouble(),
                    targetMarginPct = 35.0,
                    suggestedReorderThreshold = 5,
                    unit = "pcs",
                    notes = "Standard merchandise item."
                )
            }
        }
    }

    suspend fun askAssistant(question: String, products: List<Product>): String = withContext(Dispatchers.IO) {
        val geminiKey = BuildConfig.GEMINI_API_KEY
        if (geminiKey.isNotEmpty() && geminiKey != "MY_GEMINI_API_KEY" && !geminiKey.startsWith("AQ.")) {
            try {
                val inventorySummary = products.take(20).joinToString("\n") {
                    "${it.sku}: ${it.name} | Cat: ${it.category} | Stock: ${it.stockLevel} | Price: GH₵${it.price} | Cost: GH₵${it.costPrice}"
                }
                val prompt = "You are a Retail POS & Pharmacy Consultant in Ghana. " +
                        "Store Inventory in Ghanaian Cedis (GH₵):\n$inventorySummary\n\nUser Question: $question\n" +
                        "Provide a concise, practical recommendation in GH₵."
                val response = callGeminiApi(geminiKey, prompt)
                if (response != null) return@withContext response
            } catch (e: Exception) {
                // Fallback to offline
            }
        }

        val q = question.lowercase()
        return@withContext when {
            q.contains("pharma") || q.contains("medicine") -> {
                val pharma = products.filter { it.category.equals("Pharmaceuticals", ignoreCase = true) }
                "You have ${pharma.size} pharmaceutical items in stock with ${pharma.sumOf { it.stockLevel }} units total. Paracetamol, Coartem, and ORS are top priority OTC essentials."
            }
            q.contains("provision") -> {
                val prov = products.filter { it.category.equals("Provisions", ignoreCase = true) }
                "Your provisions inventory includes ${prov.size} items (${prov.sumOf { it.stockLevel }} units). High volume staples like Milo and Ideal Milk drive daily traffic."
            }
            q.contains("momo") || q.contains("payment") -> {
                "Mobile Money (MTN MoMo, Telecel Cash) represents the majority of retail transactions in Ghana. Ensure cash float is maintained for change when customers pay in cash."
            }
            q.contains("low") || q.contains("order") -> {
                val low = products.filter { it.isLowStock }
                if (low.isEmpty()) "All product levels in your shop are in a healthy range!"
                else "Attention: ${low.size} items are low on stock: " +
                        low.joinToString(", ") { "${it.name} (${it.stockLevel} ${it.unit} left)" } +
                        ". Check Restock Plan tab for optimal batch orders in GH₵."
            }
            q.contains("margin") || q.contains("profit") -> {
                val sorted = products.filter { it.price > 0 && it.costPrice > 0 }.sortedByDescending { it.grossMarginPct }
                val top3 = sorted.take(3)
                "Top margin items: " + top3.joinToString("; ") { "${it.name} (${String.format("%.1f", it.grossMarginPct)}% margin)" }
            }
            else -> {
                val health = getInventoryHealth(products)
                "Store Health Score: ${health.healthScore}/100. Total inventory valuation is GH₵${String.format("%.2f", health.retailValuation)} across ${health.totalUnits} items."
            }
        }
    }

    private fun callGeminiApi(apiKey: String, prompt: String): String? {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 3000
        conn.readTimeout = 4000
        conn.doOutput = true

        val jsonBody = JSONObject().apply {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    }
                    put("parts", parts)
                })
            }
            put("contents", contents)
        }

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(jsonBody.toString())
            writer.flush()
        }

        if (conn.responseCode == 200) {
            val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val root = JSONObject(response)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return parts.getJSONObject(0).optString("text")
                }
            }
        }
        return null
    }
}
