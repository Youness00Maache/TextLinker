package com.xfire.textlinker.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

/**
 * Utility class for generating QR codes from tokens
 */
class QRCodeGenerator {
    companion object {
        /**
         * Generate a QR code bitmap from a token
         * 
         * @param token The token to encode in the QR code
         * @param width The width of the QR code bitmap
         * @param height The height of the QR code bitmap
         * @return A Bitmap containing the QR code
         */
        fun generateQRCode(token: String, width: Int = 500, height: Int = 500): Bitmap? {
            try {
                val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
                hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
                hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
                hints[EncodeHintType.MARGIN] = 1

                val writer = QRCodeWriter()
                val bitMatrix = writer.encode(token, BarcodeFormat.QR_CODE, width, height, hints)
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                    }
                }
                return bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }
}