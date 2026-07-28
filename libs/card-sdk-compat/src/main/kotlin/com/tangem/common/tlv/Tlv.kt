package com.tangem.common.tlv

class Tlv private constructor(
    val data: ByteArray,
) {
    companion object {
        fun deserialize(data: ByteArray): Tlv = Tlv(data)
    }
}

class TlvDecoder(
    val tlv: Tlv,
)
