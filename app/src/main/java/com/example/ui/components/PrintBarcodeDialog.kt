package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Product
import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.Code128Writer
import java.io.FileOutputStream

@Composable
fun PrintBarcodeDialog(
    product: Product,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val effectiveBarcode = remember(product) {
        if (!product.barcode.isNullOrBlank()) product.barcode else Product.generateBarcode(product.sku)
    }

    val barcodeBitmap = remember(effectiveBarcode) {
        generateBarcodeBitmap(effectiveBarcode, 600, 160)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = "Barcode",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Print Product Barcode",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Print sticker label for thermal printer or product shelf.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Printable Label Sticker Preview (Standard Ghanaian Retail Format)
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                        .testTag("printable_barcode_label")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "GHANA RETAIL & PHARMACY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            text = product.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = Color.Black
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SKU: ${product.sku}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.DarkGray
                            )
                            if (!product.expiryDate.isNullOrBlank()) {
                                Text(
                                    text = "EXP: ${product.expiryDate}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFDC2626),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Rendered Barcode Graphic
                        if (barcodeBitmap != null) {
                            Image(
                                bitmap = barcodeBitmap.asImageBitmap(),
                                contentDescription = "Barcode Graphic",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                            )
                        }

                        Text(
                            text = effectiveBarcode,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = Color.Black,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "GH₵ ${String.format("%.2f", product.price)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F766E),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    printBarcodeLabel(context, product, effectiveBarcode)
                },
                modifier = Modifier.testTag("print_label_confirm_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.Print, contentDescription = "Print", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Print Label")
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(
                    onClick = {
                        shareBarcodeSticker(context, product, effectiveBarcode)
                    },
                    modifier = Modifier.testTag("share_label_button")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

fun generateBarcodeBitmap(content: String, width: Int, height: Int): Bitmap? {
    return try {
        val writer = Code128Writer()
        val bitMatrix = writer.encode(content, BarcodeFormat.CODE_128, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

fun createFullLabelBitmap(product: Product, barcodeStr: String): Bitmap {
    val width = 500
    val height = 300
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(AndroidColor.WHITE)

    val paint = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.BLACK
        textAlign = Paint.Align.CENTER
    }

    // Header
    paint.textSize = 18f
    paint.color = AndroidColor.GRAY
    canvas.drawText("GHANA RETAIL & PHARMACY", width / 2f, 30f, paint)

    // Product Name
    paint.textSize = 24f
    paint.color = AndroidColor.BLACK
    paint.isFakeBoldText = true
    canvas.drawText(product.name.take(24), width / 2f, 65f, paint)

    // SKU & Expiry
    paint.textSize = 18f
    paint.isFakeBoldText = false
    val subText = if (!product.expiryDate.isNullOrBlank()) "SKU: ${product.sku} | EXP: ${product.expiryDate}" else "SKU: ${product.sku}"
    canvas.drawText(subText, width / 2f, 95f, paint)

    // Barcode bitmap
    val barcodeBmp = generateBarcodeBitmap(barcodeStr, 400, 100)
    if (barcodeBmp != null) {
        canvas.drawBitmap(barcodeBmp, 50f, 110f, paint)
    }

    // Numbers
    paint.textSize = 18f
    paint.isFakeBoldText = true
    canvas.drawText(barcodeStr, width / 2f, 230f, paint)

    // Price in Ghanaian Cedis
    paint.textSize = 32f
    paint.color = AndroidColor.rgb(15, 118, 110)
    canvas.drawText("GH₵ ${String.format("%.2f", product.price)}", width / 2f, 275f, paint)

    return bitmap
}

fun printBarcodeLabel(context: Context, product: Product, barcodeStr: String) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager != null) {
            val labelBitmap = createFullLabelBitmap(product, barcodeStr)
            val printAdapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder("Barcode_${product.sku}.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    val pdfDocument = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(500, 300, 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(labelBitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)
                    try {
                        destination?.fileDescriptor?.let { fd ->
                            FileOutputStream(fd).use { out ->
                                pdfDocument.writeTo(out)
                            }
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    } finally {
                        pdfDocument.close()
                    }
                }
            }
            printManager.print("Barcode_${product.sku}", printAdapter, null)
        } else {
            Toast.makeText(context, "Printer service not available.", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Print error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun shareBarcodeSticker(context: Context, product: Product, barcodeStr: String) {
    try {
        val labelBitmap = createFullLabelBitmap(product, barcodeStr)
        val uriStr = MediaStore.Images.Media.insertImage(
            context.contentResolver,
            labelBitmap,
            "Barcode_${product.sku}",
            "Barcode label for ${product.name}"
        )
        val imageUri = Uri.parse(uriStr)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, "Barcode Label for ${product.name} (GH₵ ${String.format("%.2f", product.price)})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Barcode Label"))
    } catch (e: Exception) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Product: ${product.name}\nSKU: ${product.sku}\nBarcode: $barcodeStr\nPrice: GH₵ ${String.format("%.2f", product.price)}")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Barcode Details"))
    }
}
