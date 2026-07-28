package com.tangem.common

import com.tangem.common.card.FirmwareVersion

class CardIdRangeDec(
    private val start: String,
    private val end: String,
) {
    fun contains(cardId: String): Boolean {
        return cardId >= start && cardId <= end
    }
}

class CardFilter(
    val allowedCardTypes: List<FirmwareVersion.FirmwareType> = emptyList(),
    val maxFirmwareVersion: FirmwareVersion? = null,
    var cardIdFilter: Companion.ItemFilter? = null,
    val batchIdFilter: Companion.ItemFilter? = null,
) {
    companion object {
        sealed class ItemFilter {
            data class Allow(val items: Set<String>) : ItemFilter()
            data class Deny(val items: Set<String>) : ItemFilter()
        }
    }
}
