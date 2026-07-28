package com.tangem.blockchain.common.trustlines

import com.tangem.blockchain.common.Amount

sealed class AssetRequirementsCondition {
    data object PaidTransaction : AssetRequirementsCondition()
    data class RequiredTrustline(val amount: Amount) : AssetRequirementsCondition()
    data class PaidTransactionWithFee(val feeAmount: Amount) : AssetRequirementsCondition()
    data class IncompleteTransaction(val amount: Amount, val feeAmount: Amount) : AssetRequirementsCondition()
}
