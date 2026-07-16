package com.example.data

import android.util.Base64
import java.nio.charset.Charset

object PrintingHelper {

    fun formatLeftRight(left: String, right: String, width: Int = 32): String {
        var l = left
        val r = right
        if (l.length + r.length > width - 1) {
            val maxLen = width - r.length - 1
            if (maxLen > 0) {
                l = l.substring(0, maxLen)
            }
        }
        val spaces = width - l.length - r.length
        val sb = StringBuilder(l)
        for (i in 0 until spaces) {
            sb.append(' ')
        }
        sb.append(r)
        return sb.toString()
    }

    fun formatAlign(text: String, width: Int = 32, align: String = "Center"): String {
        var s = text
        if (s.length > width) {
            s = s.substring(0, width)
        }
        val spaces = width - s.length
        if (spaces <= 0) return s
        val sb = StringBuilder()
        when (align) {
            "Left" -> {
                sb.append(s)
                for (i in 0 until spaces) sb.append(' ')
            }
            "Right" -> {
                for (i in 0 until spaces) sb.append(' ')
                sb.append(s)
            }
            else -> { // Center
                val left = spaces / 2
                for (i in 0 until left) sb.append(' ')
                sb.append(s)
                val right = spaces - left
                for (i in 0 until right) sb.append(' ')
            }
        }
        return sb.toString()
    }

    fun generateReceiptText(
        title: String,
        subtitle: String?,
        contentLines: List<String>,
        settings: ReceiptSettingsEntity,
        width: Int = 32
    ): String {
        val sb = StringBuilder()
        val lineSeparator = "-".repeat(width)

        // Header
        val headerLines = settings.header.split("\n")
        for (line in headerLines) {
            if (line.trim().isNotEmpty()) {
                sb.append(formatAlign(line.trim(), width, settings.headerAlign)).append("\n")
            }
        }

        // Title & Subtitle
        sb.append(formatAlign(title, width, "Center")).append("\n")
        if (subtitle != null) {
            sb.append(formatAlign(subtitle, width, "Center")).append("\n")
        }
        sb.append(lineSeparator).append("\n")

        // Content
        for (line in contentLines) {
            if (line == "line") {
                sb.append(lineSeparator).append("\n")
            } else {
                sb.append(line).append("\n")
            }
        }
        sb.append(lineSeparator).append("\n")

        // Footer
        val footerLines = settings.footer.split("\n")
        for (line in footerLines) {
            if (line.trim().isNotEmpty()) {
                sb.append(formatAlign(line.trim(), width, settings.footerAlign)).append("\n")
            }
        }

        return sb.toString()
    }


    fun bitmapToEscPos(bitmap: android.graphics.Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        
        // Calculate bytes per width (8 pixels per byte)
        val xL = (width + 7) / 8
        val xH = xL / 256
        val yL = height % 256
        val yH = height / 256
        
        val byteList = mutableListOf<Byte>()
        
        // GS v 0 (print raster bit image)
        byteList.add(29.toByte())
        byteList.add(118.toByte())
        byteList.add(48.toByte())
        byteList.add(0.toByte())
        
        byteList.add((xL % 256).toByte())
        byteList.add(xH.toByte())
        byteList.add(yL.toByte())
        byteList.add(yH.toByte())
        
        // Raster data
        for (y in 0 until height) {
            for (xByte in 0 until xL) {
                var currentByte = 0
                for (b in 0 until 8) {
                    val x = xByte * 8 + b
                    if (x < width) {
                        val pixel = bitmap.getPixel(x, y)
                        // Simple thresholding for B&W
                        val r = android.graphics.Color.red(pixel)
                        val g = android.graphics.Color.green(pixel)
                        val bColor = android.graphics.Color.blue(pixel)
                        val a = android.graphics.Color.alpha(pixel)
                        
                        val luminance = (r * 0.299 + g * 0.587 + bColor * 0.114).toInt()
                        if (luminance < 128 && a > 128) {
                            currentByte = currentByte or (1 shl (7 - b))
                        }
                    }
                }
                byteList.add(currentByte.toByte())
            }
        }
        
        return byteList.toByteArray()
    }

    fun getRawBTIntentUri(receiptText: String, logoBase64: String?, logoAlign: String): String {
        val escInit = byteArrayOf(27, 64) // ESC @ (initialize)
        val textBytes = (receiptText + "\n\n\n\n").toByteArray(Charset.forName("GBK"))
        
        var combinedBytes = escInit
        
        if (!logoBase64.isNullOrEmpty()) {
            try {
                val alignmentByte = when (logoAlign) {
                    "Right" -> byteArrayOf(27, 97, 2)
                    "Left" -> byteArrayOf(27, 97, 0)
                    else -> byteArrayOf(27, 97, 1) // Center
                }
                
                val imageBytes = Base64.decode(logoBase64, Base64.DEFAULT)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                
                val escPosImage = bitmapToEscPos(bitmap)
                val resetAlignLeft = byteArrayOf(27, 97, 0)
                
                combinedBytes = combinedBytes + alignmentByte + escPosImage + byteArrayOf(10) + resetAlignLeft
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        combinedBytes = combinedBytes + textBytes
        val base64Data = Base64.encodeToString(combinedBytes, Base64.NO_WRAP)
        return "intent:base64,$base64Data#Intent;scheme=rawbt;package=ru.a402d.rawbtprinter;end;"
    }
}
