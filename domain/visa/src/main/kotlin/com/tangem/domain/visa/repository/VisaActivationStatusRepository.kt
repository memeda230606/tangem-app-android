package com.tangem.domain.visa.repository

import com.tangem.domain.visa.model.VisaCardActivationStatus

/**
 * Holds the latest Visa activation state for a scanned card.
 *
 * The state is intentionally separated from ScanResponse: domain:models cannot depend on domain:visa:models.
 */
interface VisaActivationStatusRepository {

    fun get(cardId: String): VisaCardActivationStatus?

    fun set(cardId: String, status: VisaCardActivationStatus)

    fun remove(cardId: String)
}
