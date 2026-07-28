package com.tangem.operations.attestation.api

object TangemApiServiceSettings {
    private val mutableInterceptors = mutableListOf<Any>()

    val interceptors: List<Any>
        get() = mutableInterceptors.toList()

    fun addInterceptors(vararg interceptors: Any) {
        mutableInterceptors.addAll(interceptors)
    }
}
