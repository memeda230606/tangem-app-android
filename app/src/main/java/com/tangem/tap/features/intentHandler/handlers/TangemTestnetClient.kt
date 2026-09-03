package com.tangem.tap.features.intentHandler.handlers

import android.util.Base64
import com.tangem.wallet.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** L0 TESTNET-only adapter. It must be replaced by the HSM/SAM provider before production use. */
internal class TangemTestnetClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    fun verify(identity: ExternalNdefScanController.CardIdentity): ExternalNdefScanController.CardIdentity? {
        val nonceBytes = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val nonce = Base64.encodeToString(nonceBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val timestamp = System.currentTimeMillis()
        val message = listOf(
            PROTOCOL_VERSION,
            identity.cardInstanceId,
            identity.keyVersion.toString(),
            PURPOSE_CLAIM,
            nonce,
            timestamp.toString(),
        ).joinToString("\n")
        val proof = hmacSha256(BuildConfig.SECURE_CARD_READ_ROOT, message)
        val body = JSONObject()
            .put("cardInstanceId", identity.cardInstanceId)
            .put("keyVersion", identity.keyVersion)
            .put("purpose", PURPOSE_CLAIM)
            .put("requestNonce", nonce)
            .put("timestamp", timestamp)
            .put("proof", proof)

        val response = post("/verifications", body) ?: return null
        val card = response.getJSONObject("card")
        val binding = card.optJSONObject("binding")
        return identity.copy(
            verificationToken = response.getString("verificationToken"),
            cardRef = card.getString("cardRef"),
            boundWalletId = binding?.optString("walletId")?.takeIf(String::isNotBlank),
            bindingRole = binding?.optString("role")?.takeIf(String::isNotBlank),
        )
    }

    fun claim(verificationToken: String, walletId: String, rootPublicKeyHash: String): Boolean {
        val body = JSONObject()
            .put("verificationToken", verificationToken)
            .put("walletId", walletId)
            .put("rootPublicKeyHash", rootPublicKeyHash)
        return post("/claims", body) != null
    }

    private fun post(path: String, body: JSONObject): JSONObject? {
        val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            if (connection.responseCode !in 200..299) return null
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun hmacSha256(secret: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://hm.niubtmd.com/api/tangem/testnet"
        private const val PROTOCOL_VERSION = "TANGEM_L0_TESTNET_V1"
        private const val PURPOSE_CLAIM = "CLAIM"
        private const val NONCE_BYTES = 24
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 8_000

        fun newWalletId(): String = UUID.randomUUID().toString()

        fun walletFingerprint(publicKeys: List<ByteArray>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            publicKeys.sortedBy { Base64.encodeToString(it, Base64.NO_WRAP) }.forEach(digest::update)
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
