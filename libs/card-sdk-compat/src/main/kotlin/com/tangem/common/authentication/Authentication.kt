package com.tangem.common.authentication

interface AuthenticationManager {
    val needEnrollBiometrics: Boolean
        get() = false

    val canAuthenticate: Boolean
        get() = false

    fun unsubscribe(activity: Any?) = Unit
}
