package com.tangem.data.wallets.nfc

import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.domain.wallets.nfc.NfcWalletBlob
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

class NfcWalletBlobCodec {

    fun encode(blob: NfcWalletBlob): ByteArray {
        val body = encodeBody(blob.copy(checksum = ByteArray(0)))
        val checksum = body.sha256()
        return encodeBody(blob.copy(checksum = checksum))
    }

    fun decode(payload: ByteArray): NfcWalletBlob {
        require(payload.size >= MAGIC.size) { "NFC wallet payload is too short" }
        require(payload.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Invalid NFC wallet payload magic" }

        val fields = mutableMapOf<Int, ByteArray>()
        var offset = MAGIC.size
        while (offset < payload.size) {
            require(offset + HEADER_SIZE <= payload.size) { "Invalid NFC wallet field header" }
            val tag = payload[offset].toInt() and U_BYTE_MASK
            val length = ByteBuffer.wrap(payload, offset + 1, Int.SIZE_BYTES).int
            offset += HEADER_SIZE
            require(length >= 0 && offset + length <= payload.size) { "Invalid NFC wallet field length" }
            fields[tag] = payload.copyOfRange(offset, offset + length)
            offset += length
        }

        val checksum = fields.requireField(Field.Checksum)
        val bodyWithoutChecksum = encodeBody(
            NfcWalletBlob(
                version = fields.requireField(Field.Version).toIntValue(),
                walletId = UserWalletId(fields.requireField(Field.WalletId).decodeToString()),
                cardInstanceId = fields.requireField(Field.CardInstanceId).decodeToString(),
                backupSetId = fields.requireField(Field.BackupSetId).decodeToString(),
                localKeyId = fields.requireField(Field.LocalKeyId).decodeToString(),
                nonce = fields.requireField(Field.Nonce),
                ciphertext = fields.requireField(Field.Ciphertext),
                tag = fields.requireField(Field.Tag),
                checksum = ByteArray(0),
            ),
        )
        require(checksum.contentEquals(bodyWithoutChecksum.sha256())) { "Invalid NFC wallet payload checksum" }

        return NfcWalletBlob(
            version = fields.requireField(Field.Version).toIntValue(),
            walletId = UserWalletId(fields.requireField(Field.WalletId).decodeToString()),
            cardInstanceId = fields.requireField(Field.CardInstanceId).decodeToString(),
            backupSetId = fields.requireField(Field.BackupSetId).decodeToString(),
            localKeyId = fields.requireField(Field.LocalKeyId).decodeToString(),
            nonce = fields.requireField(Field.Nonce),
            ciphertext = fields.requireField(Field.Ciphertext),
            tag = fields.requireField(Field.Tag),
            checksum = checksum,
        )
    }

    private fun encodeBody(blob: NfcWalletBlob): ByteArray {
        return ByteArrayOutputStream().use { output ->
            output.write(MAGIC)
            output.writeField(Field.Version, blob.version.toByteArray())
            output.writeField(Field.WalletId, blob.walletId.stringValue.encodeToByteArray())
            output.writeField(Field.CardInstanceId, blob.cardInstanceId.encodeToByteArray())
            output.writeField(Field.BackupSetId, blob.backupSetId.encodeToByteArray())
            output.writeField(Field.LocalKeyId, blob.localKeyId.encodeToByteArray())
            output.writeField(Field.Nonce, blob.nonce)
            output.writeField(Field.Ciphertext, blob.ciphertext)
            output.writeField(Field.Tag, blob.tag)
            if (blob.checksum.isNotEmpty()) {
                output.writeField(Field.Checksum, blob.checksum)
            }
            output.toByteArray()
        }
    }

    private fun ByteArrayOutputStream.writeField(field: Field, value: ByteArray) {
        write(field.id)
        write(value.size.toByteArray())
        write(value)
    }

    private fun Map<Int, ByteArray>.requireField(field: Field): ByteArray {
        return requireNotNull(this[field.id]) { "Missing NFC wallet field ${field.id}" }
    }

    private fun Int.toByteArray(): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(this).array()

    private fun ByteArray.toIntValue(): Int = ByteBuffer.wrap(this).int

    private fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

    private enum class Field(val id: Int) {
        Version(1),
        WalletId(2),
        CardInstanceId(3),
        BackupSetId(4),
        LocalKeyId(5),
        Nonce(6),
        Ciphertext(7),
        Tag(8),
        Checksum(9),
    }

    companion object {
        const val MIME_TYPE = "application/vnd.tangem.nfcwallet.v1"
        const val CURRENT_VERSION = 1

        private val MAGIC = byteArrayOf(0x54, 0x4E, 0x57, 0x31)
        private const val HEADER_SIZE = 1 + Int.SIZE_BYTES
        private const val U_BYTE_MASK = 0xFF
    }
}
