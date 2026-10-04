package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AiAdvisorService
import com.example.ai.InventoryHealthSummary
import com.example.ai.RestockItemAdvice
import com.example.ui.PosViewModel
import kotlinx.coroutines.launch

@Composable
fun AiAdvisorScreen(
    viewModel: PosViewModel
) {
    val products by viewModel.productsList.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Restock Plan", "Store Health", "AI Assistant")

    var healthSummary by remember { mutableStateOf<InventoryHealthSummary?>(null) }
    var restockAdviceList by remember { mutableStateOf<List<RestockItemAdvice>>(emptyList()) }
    var userQuestion by remember { mutableStateOf("") }
    var aiAnswer by remember { mutableStateOf<String?>(null) }
    var isAskingAi by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(products) {
        healthSummary = AiAdvisorService.getInventoryHealth(products)
        restockAdviceList = AiAdvisorService.generateRestockAdvice(products)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("ai_advisor_screen")
    ) {
        // Tab Navigation
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            0 -> RestockPlanTab(
                restockAdviceList = restockAdviceList,
                onQuickRestock = { item ->
                    viewModel.adjustStock(item.sku, item.orderQuantity, "RESTOCK", "AI Restock Recommendation")
                }
            )
            1 -> StoreHealthTab(healthSummary = healthSummary)
            2 -> AiAssistantTab(
                userQuestion = userQuestion,
                onQuestionChange = { userQuestion = it },
                aiAnswer = aiAnswer,
                isAskingAi = isAskingAi,
                onSendQuestion = { q ->
                    isAskingAi = true
                    coroutineScope.launch {
                        aiAnswer = AiAdvisorService.askAssistant(q, products)
                        isAskingAi = false
                    }
                }
            )
        }
    }
}

@Composable
fun RestockPlanTab(
    restockAdviceList: List<RestockItemAdvice>,
    onQuickRestock: (RestockItemAdvice) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Unpaid AI Restock Advisor (Ghana)",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Predicts demand for fast-moving provisions, life-saving OTC pharmaceuticals, and cosmetics in Ghanaian Cedis (GH₵).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        if (restockAdviceList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "OK", tint = Color(0xFF16A34A), modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("All shop inventory levels are healthy!", fontWeight = FontWeight.SemiBold)
                        Text("No items currently require immediate reordering.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(restockAdviceList, key = { it.sku }) { item ->
                val priorityColor = when (item.priority) {
                    "CRITICAL" -> Color(0xFFEF4444)
                    "HIGH" -> Color(0xFFD97706)
                    else -> Color(0xFF0284C7)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(text = "Category: ${item.category}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Box(
                                modifier = Modifier
                                    .background(priorityColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.priority,
                                    color = priorityColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Current: ${item.currentStock}  ➜  Target: ${item.targetStock}  (+${item.orderQuantity})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (item.estimatedCost > 0.0) {
                                    Text(
                                        text = "Est. Restock Cost: GH₵ ${String.format("%.2f", item.estimatedCost)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = { onQuickRestock(item) },
                                modifier = Modifier.testTag("quick_restock_${item.sku}")
                            ) {
                                Text("+ Order ${item.orderQuantity}")
                            }
                        }

                        Text(
                            text = item.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StoreHealthTab(healthSummary: InventoryHealthSummary?) {
    if (healthSummary == null) return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Overall Score Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("GHANA STORE HEALTH SCORE", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = "${healthSummary.healthScore} / 100",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (healthSummary.healthScore >= 75) Color(0xFF16A34A) else Color(0xFFD97706)
                    )
                    Text(
                        text = if (healthSummary.healthScore >= 80) "Optimal stock balance. Strong cash flow across provisions and pharmaceuticals."
                        else "Attention required: ${healthSummary.lowStockCount} items at risk of running out.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Financial Metrics Grid in GH₵
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Valuation (Ghanaian Cedis)", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    HealthMetricRow("Total Retail Inventory Value", "GH₵ ${String.format("%.2f", healthSummary.retailValuation)}")
                    HealthMetricRow("Total Cost Basis", "GH₵ ${String.format("%.2f", healthSummary.costValuation)}")
                    HealthMetricRow("Projected Gross Profit", "+GH₵ ${String.format("%.2f", healthSummary.grossProfit)}", isPositive = true)
                    HealthMetricRow("Average Store Margin", "${String.format("%.1f", healthSummary.averageMarginPct)}%", isPositive = true)
                }
            }
        }

        // Stock Breakdown
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Stock Status Breakdown", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    HealthMetricRow("Active Products in Catalog", "${healthSummary.totalProducts} items")
                    HealthMetricRow("Total Units in Inventory", "${healthSummary.totalUnits} units")
                    HealthMetricRow("Low Stock Alert", "${healthSummary.lowStockCount} items", isWarning = healthSummary.lowStockCount > 0)
                    HealthMetricRow("Out of Stock", "${healthSummary.outOfStockCount} items", isError = healthSummary.outOfStockCount > 0)
                }
            }
        }
    }
}

@Composable
fun AiAssistantTab(
    userQuestion: String,
    onQuestionChange: (String) -> Unit,
    aiAnswer: String?,
    isAskingAi: Boolean,
    onSendQuestion: (String) -> Unit
) {
    val quickQuestions = listOf(
        "Which pharmaceuticals have the highest margin?",
        "What provisions are running low on stock?",
        "What are best practices for MoMo vs Cash management?"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_assistant_tab")
    ) {
        Text("Quick Questions", style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(quickQuestions) { q ->
                FilterChip(
                    selected = false,
                    onClick = {
                        onQuestionChange(q)
                        onSendQuestion(q)
                    },
                    label = { Text(q, fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Answer box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (isAskingAi) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Analyzing inventory with Ghanaian AI...", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (aiAnswer != null) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "AI", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ghana Retail Intelligence", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = aiAnswer,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }
                } else {
                    Text(
                        text = "Ask any inventory, restock, or margin question above to analyze your provisions, medicines, groceries, and cosmetics.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = userQuestion,
                onValueChange = onQuestionChange,
                placeholder = { Text("Ask pharmacy / retail assistant...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_question_input"),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = {
                    if (userQuestion.isNotBlank()) {
                        onSendQuestion(userQuestion)
                    }
                },
                modifier = Modifier.testTag("ai_question_send")
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun HealthMetricRow(
    label: String,
    value: String,
    isPositive: Boolean = false,
    isWarning: Boolean = false,
    isError: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val color = when {
            isError -> Color(0xFFEF4444)
            isWarning -> Color(0xFFD97706)
            isPositive -> Color(0xFF16A34A)
            else -> MaterialTheme.colorScheme.onSurface
        }
        Text(text = value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = color)
    }
}
