package com.tangem.common.services

import com.tangem.common.services.secure.InMemorySecureStorage

class InMemoryStorage(
    name: String = "default",
) : InMemorySecureStorage(name)
