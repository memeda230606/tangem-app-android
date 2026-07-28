package com.tangem.operations.preflightread

import com.tangem.common.card.Card
import com.tangem.common.core.SessionEnvironment

interface PreflightReadFilter {
    fun onCardRead(card: Card) = Unit
    fun onCardRead(card: Card, environment: SessionEnvironment) = onCardRead(card)
    fun onFullCardRead(card: Card, environment: SessionEnvironment) = onCardRead(card, environment)
}
