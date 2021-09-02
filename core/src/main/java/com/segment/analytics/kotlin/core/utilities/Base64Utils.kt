@file:JvmName("Base64Utils")
package com.segment.analytics.kotlin.core.utilities

// Encode string to base64
fun encodeToBase64(str: String) = encodeToBase64(str.toByteArray())

// Encode byte-array to base64, this implementation is not url-safe
fun encodeToBase64(bytes: ByteArray) = buildString {
    val wData = ByteArray(3) // working data
    var i = 0
    while (i < bytes.size) {
        val leftover = bytes.size - i
        val available = if (leftover >= 3) {
            3
        } else {
            leftover
        }
        for (j in 0 until available) {
            wData[j] = bytes[i++]
        }
        for (j in 2 downTo available) {
            wData[j] = 0 // clear out
        }
        // Given a 3 byte block (24 bits), encode it to 4 base64 characters
        val chunk = ((wData[0].toInt() and 0xFF) shl 16) or
                ((wData[1].toInt() and 0xFF) shl 8) or
                (wData[2].toInt() and 0xFF)

        // if we have too little characters in this block, we add padding
        val padCount = (wData.size - available) * 8 / 6

        // encode to base64
        for (index in 3 downTo padCount) { // 4 base64 characters
            val char = (chunk shr (6 * index)) and 0x3f // 0b00111111
            append(char.base64Val())
        }

        // add padding if needed
        repeat(padCount) { append("=") }
    }
}

private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
private fun Int.base64Val(): Char = ALPHABET[this]