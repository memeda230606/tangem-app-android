package com.tangem.tap.features.intentHandler.handlers

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.niubtmd.securenfc.Hex
import com.niubtmd.securenfc.Ntag424Dna
import com.niubtmd.securenfc.SecureCardKeys
import com.niubtmd.securenfc.SecureCardPayload
import com.tangem.wallet.BuildConfig

/** Accepts only an NTAG 424 DNA that proves possession of our diversified AES read key. */
class ExternalNdefTagReader(
    private val secureReader: (Tag) -> ExternalNdefScanController.CardIdentity? = ::readSecureCard,
    private val serverVerifier: (ExternalNdefScanController.CardIdentity) -> ExternalNdefScanController.CardIdentity? =
        TangemTestnetClient()::verify,
) {

    @Volatile
    var lastIdentity: ExternalNdefScanController.CardIdentity? = null
        private set

    fun read(tag: Tag): ExternalNdefScanController.Result {
        val identity = runCatching { secureReader(tag)?.let(serverVerifier) }.getOrNull()
        lastIdentity = identity
        return if (identity != null) {
            ExternalNdefScanController.Result.Accepted
        } else {
            ExternalNdefScanController.Result.Rejected
        }
    }

    companion object {
        private fun readSecureCard(tag: Tag): ExternalNdefScanController.CardIdentity? {
            val uid = tag.id ?: return null
            if (uid.size != 7) return null
            val isoDep = IsoDep.get(tag) ?: return null

            return try {
                isoDep.connect()
                isoDep.timeout = 5_000
                val card = Ntag424Dna(isoDep::transceive)
                card.selectNdefApplication()
                if (!card.isNtag424Dna) return null
                val readRoot = Hex.decode(BuildConfig.SECURE_CARD_READ_ROOT)
                val readKey = SecureCardKeys.derive(readRoot, uid, SecureCardKeys.READ_KEY)
                val session = card.authenticate(SecureCardKeys.READ_KEY, readKey)
                val identity = SecureCardPayload.decodeV2(card.readFull(session, Ntag424Dna.NDEF_FILE))
                ExternalNdefScanController.CardIdentity(
                    cardInstanceId = identity.cardInstanceId.toString(),
                    keyVersion = identity.keyVersion,
                    targetUrl = identity.targetUrl,
                )
            } finally {
                runCatching { isoDep.close() }
            }
        }
    }
}
