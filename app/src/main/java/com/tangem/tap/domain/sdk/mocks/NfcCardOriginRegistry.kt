package com.tangem.tap.domain.sdk.mocks

/** Process-local route selected by the most recent card discovery. Persisted wallet bindings remain authoritative. */
internal object NfcCardOriginRegistry {
    enum class Origin { Demo, Real }

    @Volatile
    var current: Origin = Origin.Real
}
