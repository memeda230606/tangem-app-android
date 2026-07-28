package com.tangem.tap.domain.sdk.mocks

internal object NfcDemoTag {

    const val URI_PREFIX = "tangem-demo://v1/"
    const val WEB_URI_PREFIX = "https://www.tangem.com/ndef/demo/v1/"

    private val uriPrefixes = listOf(URI_PREFIX, WEB_URI_PREFIX)

    fun isDemoTag(uri: String?): Boolean {
        return uriPrefixes.any { prefix -> uri?.startsWith(prefix) == true }
    }

    fun scenarioId(uri: String?): String? {
        val prefix = uriPrefixes.firstOrNull { uri?.startsWith(it) == true } ?: return null
        val value = uri?.removePrefix(prefix) ?: return null

        return value.takeIf { it.isNotBlank() && it.none { character -> character in "/?#" } }
    }
}
