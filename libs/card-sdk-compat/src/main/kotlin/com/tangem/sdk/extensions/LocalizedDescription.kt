package com.tangem.sdk.extensions

import com.tangem.common.core.TangemSdkError

data class LocalizedDescriptionResource(
    val resId: Int? = null,
    val args: List<Arg> = emptyList(),
) {
    data class Arg(val value: Any)
}

fun TangemSdkError.localizedDescriptionRes(): LocalizedDescriptionResource {
    return LocalizedDescriptionResource()
}
