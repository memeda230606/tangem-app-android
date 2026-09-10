package com.tangem.tap.features.intentHandler.handlers

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.niubtmd.securenfc.Hex
import com.niubtmd.securenfc.Ntag424Dna
import com.niubtmd.securenfc.SecureCardKeys
import com.niubtmd.securenfc.SecureCardIdentityStore
import com.tangem.wallet.BuildConfig
import java.util.concurrent.CancellationException

/** Accepts only an NTAG 424 DNA that proves possession of our diversified AES read key. */
class ExternalNdefTagReader(
    private val secureReader: (Tag) -> ExternalNdefScanController.CardIdentity? = ::readSecureCard,
    private val serverVerifier: (ExternalNdefScanController.CardIdentity) -> ExternalNdefScanController.CardIdentity? =
        TangemTestnetClient()::verify,
) {

    fun read(tag: Tag): ReadResult {
        val identity = try {
            secureReader(tag) ?: return ReadResult.Unsupported
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // TagLostException, I/O, stale Tag and incomplete/invalid protected responses are not proof
            // of an unsupported card. Never continue, but let the user present the card again.
            return ReadResult.RetryTap
        }
        return try {
            serverVerifier(identity)?.let { ReadResult.Accepted(it) } ?: ReadResult.VerificationRejected
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ReadResult.VerificationUnavailable
        }
    }

    sealed interface ReadResult {
        data class Accepted(val identity: ExternalNdefScanController.CardIdentity) : ReadResult
        data object Unsupported : ReadResult
        data object RetryTap : ReadResult
        data object VerificationUnavailable : ReadResult
        data object VerificationRejected : ReadResult
    }

    companion object {
        internal fun authenticateReadKey(card: Ntag424Dna, readKey: ByteArray): Ntag424Dna.Session? = try {
            card.authenticate(SecureCardKeys.READ_KEY, readKey)
        } catch (error: Ntag424Dna.CardException) {
            // Only an explicit rejection AT authentication proves the key does not match.
            // The same status during later file reads may indicate a lost/invalidated session.
            if (error.status == 0xAE) null else throw error
        }

        private fun readSecureCard(tag: Tag): ExternalNdefScanController.CardIdentity? {
            val uid = checkNotNull(tag.id) { "Card metadata is unavailable" }
            check(uid.isNotEmpty()) { "Card UID is unavailable" }
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
                val session = authenticateReadKey(card, readKey) ?: return null
                val identity = SecureCardIdentityStore.read(card, session)
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
