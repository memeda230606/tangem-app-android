package com.tangem.datasource.api.common

import com.tangem.datasource.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val LOOPBACK_MOCK_BASE_URL = "https://127.0.0.1/"

/**
 * Keeps the standalone mocked build bootable when private environment URLs are redacted from the repository.
 * Real builds and valid URLs are returned unchanged.
 */
internal fun String.withStandaloneMockFallback(): String {
    return if (BuildConfig.MOCK_DATA_SOURCE && toHttpUrlOrNull() == null) {
        LOOPBACK_MOCK_BASE_URL
    } else {
        this
    }
}
