package com.tangem.crypto

object Secp256k1 {
    fun sum(first: ByteArray, second: ByteArray): ByteArray {
        if (first.isEmpty()) return second
        if (second.isEmpty()) return first

        val maxSize = maxOf(first.size, second.size)
        return ByteArray(maxSize) { index ->
            val a = first.getOrNull(first.size - maxSize + index) ?: 0
            val b = second.getOrNull(second.size - maxSize + index) ?: 0
            (a.toInt() xor b.toInt()).toByte()
        }
    }
}
