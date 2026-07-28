package com.tangem.tap.domain.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Handler
import android.os.Looper
import com.tangem.data.wallets.nfc.NfcWalletBlobCodec
import com.tangem.domain.wallets.nfc.NfcCardGateway
import com.tangem.domain.wallets.nfc.NfcWalletBlob
import com.tangem.tap.foregroundActivityObserver
import com.tangem.utils.coroutines.CoroutineDispatcherProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AndroidNdefNfcCardGateway @Inject constructor(
    private val dispatchers: CoroutineDispatcherProvider,
) : NfcCardGateway {

    private val codec = NfcWalletBlobCodec()
    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun readWalletBlob(): NfcWalletBlob? {
        return runNfcOperation { tag ->
            val ndef = tag.connectNdef()
            try {
                ndef.findWalletRecord()
                    ?.payload
                    ?.let(codec::decode)
            } finally {
                ndef.close()
            }
        }
    }

    override suspend fun writeWalletBlob(blob: NfcWalletBlob) {
        runNfcOperation { tag ->
            val ndef = tag.connectNdef()
            try {
                require(ndef.isWritable) { "NFC tag is read-only" }
                require(ndef.findWalletRecord() == null) { "NFC tag already contains encrypted wallet payload" }

                val message = NdefMessage(
                    arrayOf(NdefRecord.createMime(NfcWalletBlobCodec.MIME_TYPE, codec.encode(blob))),
                )
                require(message.toByteArray().size <= ndef.maxSize) { "NFC tag capacity is not enough" }

                ndef.writeNdefMessage(message)
            } finally {
                ndef.close()
            }
        }
    }

    override suspend fun eraseWalletBlob() {
        runNfcOperation { tag ->
            val ndef = tag.connectNdef()
            try {
                require(ndef.isWritable) { "NFC tag is read-only" }

                val message = NdefMessage(
                    arrayOf(NdefRecord(NdefRecord.TNF_EMPTY, ByteArray(0), ByteArray(0), ByteArray(0))),
                )
                require(message.toByteArray().size <= ndef.maxSize) { "NFC tag capacity is not enough" }

                ndef.writeNdefMessage(message)
            } finally {
                ndef.close()
            }
        }
    }

    private suspend fun <T> runNfcOperation(block: (Tag) -> T): T = withContext(dispatchers.mainImmediate) {
        val activity = foregroundActivityObserver.foregroundActivity
            ?: error("Foreground activity is required for NFC operation")
        val adapter = NfcAdapter.getDefaultAdapter(activity)
            ?: error("NFC is not supported")

        require(adapter.isEnabled) { "NFC is disabled" }

        suspendCancellableCoroutine { continuation ->
            val callback = NfcAdapter.ReaderCallback { tag ->
                val result = runCatching { block(tag) }
                disableReaderMode(adapter)

                result
                    .onSuccess { value ->
                        if (continuation.isActive) {
                            continuation.resume(value)
                        }
                    }
                    .onFailure { throwable ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(throwable)
                        }
                    }
            }

            adapter.enableReaderMode(activity, callback, READER_FLAGS, null)
            continuation.invokeOnCancellation { disableReaderMode(adapter) }
        }
    }

    private fun Tag.connectNdef(): Ndef {
        val ndef = Ndef.get(this) ?: error("NFC tag does not support NDEF")
        ndef.connect()
        return ndef
    }

    private fun Ndef.findWalletRecord(): NdefRecord? {
        return ndefMessage
            ?.records
            ?.firstOrNull { it.toMimeType() == NfcWalletBlobCodec.MIME_TYPE }
    }

    private fun disableReaderMode(adapter: NfcAdapter) {
        val activity = foregroundActivityObserver.foregroundActivity ?: return
        mainHandler.post {
            adapter.disableReaderMode(activity)
        }
    }

    private companion object {
        const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
    }
}
