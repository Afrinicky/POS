package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class MoMoState {
    INPUT_DETAILS,
    AWAITING_CUSTOMER_APPROVAL,
    PROCESSING_VERIFICATION,
    PAYMENT_SUCCESS
}

enum class GhanaTelco(
    val displayName: String,
    val brandColor: Color,
    val ussdApprovalCode: String,
    val prefixes: List<String>
) {
    MTN("MTN MoMo", Color(0xFFEAB308), "*170# -> Option 6 (My Approvals)", listOf("024", "054", "055", "059", "053")),
    TELECEL("Telecel Cash", Color(0xFFEF4444), "*110# (or Voucher Code)", listOf("020", "050")),
    AIRTELTIGO("AT Money", Color(0xFF2563EB), "*110# -> Option 5 (Approvals)", listOf("027", "057", "026", "056"))
}

@Composable
fun MobileMoneyPaymentDialog(
    totalAmount: Double,
    onPaymentConfirmed: (network: String, phone: String, reference: String) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(MoMoState.INPUT_DETAILS) }
    var selectedTelco by remember { mutableStateOf(GhanaTelco.MTN) }
    var phoneNumber by remember { mutableStateOf("") }
    var voucherCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var referenceId by remember { mutableStateOf("") }
    var timerSeconds by remember { mutableIntStateOf(30) }

    // Auto-detect network based on Ghanaian phone prefix
    LaunchedEffect(phoneNumber) {
        val clean = phoneNumber.replace(" ", "").trim()
        if (clean.length >= 3) {
            val prefix = clean.take(3)
            val matched = GhanaTelco.values().find { telco -> telco.prefixes.contains(prefix) }
            if (matched != null) {
                selectedTelco = matched
            }
        }
    }

    // Countdown timer during prompt awaiting
    LaunchedEffect(step) {
        if (step == MoMoState.AWAITING_CUSTOMER_APPROVAL) {
            timerSeconds = 30
            while (timerSeconds > 0 && step == MoMoState.AWAITING_CUSTOMER_APPROVAL) {
                delay(1000)
                timerSeconds--
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (step != MoMoState.PROCESSING_VERIFICATION) onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(selectedTelco.brandColor.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "MoMo",
                        tint = selectedTelco.brandColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Mobile Money Direct Pay",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Direct Prompt to Customer Handset",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("momo_payment_flow_dialog"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Amount Banner
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total to Charge:", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = "GH₵ ${String.format("%.2f", totalAmount)}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(selectedTelco.brandColor, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = selectedTelco.displayName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                when (step) {
                    MoMoState.INPUT_DETAILS -> {
                        // Telco Network Selector Chips
                        Text("Select Ghana Telco Network:", style = MaterialTheme.typography.labelSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            GhanaTelco.values().forEach { telco ->
                                FilterChip(
                                    selected = selectedTelco == telco,
                                    onClick = { selectedTelco = telco },
                                    label = { Text(telco.displayName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = telco.brandColor.copy(alpha = 0.25f),
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }

                        // Phone Number Input
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { input ->
                                if (input.length <= 10 && input.all { it.isDigit() }) {
                                    phoneNumber = input
                                    errorMessage = null
                                }
                            },
                            label = { Text("Customer Mobile Number") },
                            placeholder = { Text("e.g. 0244123456") },
                            leadingIcon = {
                                Text("🇬🇭", modifier = Modifier.padding(start = 8.dp), fontSize = 16.sp)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("momo_phone_input"),
                            singleLine = true
                        )

                        // Telecel Voucher Code (Optional fallback for Telecel Cash)
                        if (selectedTelco == GhanaTelco.TELECEL) {
                            OutlinedTextField(
                                value = voucherCode,
                                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) voucherCode = it },
                                label = { Text("Voucher Code (Optional)") },
                                placeholder = { Text("6 digits (dial *110#)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        Text(
                            text = "A prompt will be sent directly to the customer's phone requesting their MoMo PIN.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    MoMoState.AWAITING_CUSTOMER_APPROVAL -> {
                        // Visual Mobile Handset Prompt Animation & Status
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = selectedTelco.brandColor,
                                    strokeWidth = 3.dp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Prompt Sent to Customer Phone!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = "Phone: $phoneNumber (${selectedTelco.displayName})",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Realistic simulated USSD prompt card
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, selectedTelco.brandColor, RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .padding(12.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "CUSTOMER's PHONE SCREEN:",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Authorize payment of GH₵ ${String.format("%.2f", totalAmount)} to GHANA RETAIL STORE?\n1. Yes (Enter MoMo PIN)\n2. No",
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.Black
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Progress bar for timer
                                val progress = timerSeconds / 30f
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = selectedTelco.brandColor
                                )

                                Text(
                                    text = "Waiting for customer PIN entry... (${timerSeconds}s)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        // USSD Offline Fallback Hint
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Info, contentDescription = "Hint", tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Prompt not showing? Ask customer to dial: ${selectedTelco.ussdApprovalCode}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF92400E)
                                )
                            }
                        }
                    }

                    MoMoState.PROCESSING_VERIFICATION -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = selectedTelco.brandColor)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Settling payment with ${selectedTelco.displayName}...", fontWeight = FontWeight.SemiBold)
                                Text("Verifying transaction hash & token", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    MoMoState.PAYMENT_SUCCESS -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Payment Received Successfully!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color(0xFF16A34A)
                                )
                                Text(
                                    text = "Ref: $referenceId",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "GH₵ ${String.format("%.2f", totalAmount)} paid via ${selectedTelco.displayName}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                MoMoState.INPUT_DETAILS -> {
                    Button(
                        onClick = {
                            if (phoneNumber.length < 10) {
                                errorMessage = "Please enter a valid 10-digit Ghanaian mobile number."
                                return@Button
                            }
                            step = MoMoState.AWAITING_CUSTOMER_APPROVAL
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("send_momo_prompt_button")
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Prompt to Phone")
                    }
                }

                MoMoState.AWAITING_CUSTOMER_APPROVAL -> {
                    Button(
                        onClick = {
                            step = MoMoState.PROCESSING_VERIFICATION
                            referenceId = when (selectedTelco) {
                                GhanaTelco.MTN -> "MTN-MOM-" + Random.nextInt(100000, 999999)
                                GhanaTelco.TELECEL -> "TEL-CSH-" + Random.nextInt(100000, 999999)
                                GhanaTelco.AIRTELTIGO -> "ATM-PAY-" + Random.nextInt(100000, 999999)
                            }
                            // Simulate verification
                            step = MoMoState.PAYMENT_SUCCESS
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        modifier = Modifier.testTag("confirm_customer_approved_button")
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Approve", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Customer Entered PIN (Confirm)")
                    }
                }

                MoMoState.PROCESSING_VERIFICATION -> {
                    // Disabled while processing
                }

                MoMoState.PAYMENT_SUCCESS -> {
                    Button(
                        onClick = {
                            onPaymentConfirmed(selectedTelco.displayName, phoneNumber, referenceId)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("momo_finish_and_print_button")
                    ) {
                        Text("Print Receipt & Complete")
                    }
                }
            }
        },
        dismissButton = {
            if (step != MoMoState.PAYMENT_SUCCESS) {
                TextButton(onClick = onDismiss) {
                    Text(if (step == MoMoState.AWAITING_CUSTOMER_APPROVAL) "Cancel Prompt" else "Cancel")
                }
            }
        }
    )
}
