package com.tangem.sdk.storage

import com.tangem.common.services.secure.InMemorySecureStorage
import com.tangem.common.services.secure.SecureStorage

class AndroidSecureStorage(
    preferences: Any?,
    androidSecureStorageV2: SecureStorage,
    androidSecureStorageV3: SecureStorage,
) : SecureStorage by androidSecureStorageV3

class AndroidSecureStorageV2(
    appContext: Any?,
    useStrongBox: Boolean,
    name: String,
) : SecureStorage by InMemorySecureStorage(name)

fun SecureStorage.Companion.create(activity: Any?): SecureStorage = InMemorySecureStorage(name = "card_sdk")

fun SecureStorage.Companion.createEncryptedSharedPreferences(context: Any?, storageName: String): Any = storageName
