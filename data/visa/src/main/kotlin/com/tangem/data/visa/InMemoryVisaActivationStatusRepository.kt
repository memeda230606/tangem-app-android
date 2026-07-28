package com.tangem.data.visa

import com.tangem.domain.visa.model.VisaCardActivationStatus
import com.tangem.domain.visa.repository.VisaActivationStatusRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Session-scoped activation state. It is used by a scan and the subsequent onboarding flow. */
@Singleton
internal class InMemoryVisaActivationStatusRepository @Inject constructor() : VisaActivationStatusRepository {

    private val statuses = ConcurrentHashMap<String, VisaCardActivationStatus>()

    override fun get(cardId: String): VisaCardActivationStatus? = statuses[cardId]

    override fun set(cardId: String, status: VisaCardActivationStatus) {
        statuses[cardId] = status
    }

    override fun remove(cardId: String) {
        statuses.remove(cardId)
    }
}
