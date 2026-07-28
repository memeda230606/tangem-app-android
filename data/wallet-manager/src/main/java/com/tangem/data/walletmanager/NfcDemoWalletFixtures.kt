package com.tangem.data.walletmanager

import com.tangem.domain.models.network.TxInfo
import java.math.BigDecimal

/** Deterministic public transaction data for ordinary-NFC demo wallets. */
internal object NfcDemoWalletFixtures {

    val transactions = listOf(
        TxInfo(
            txHash = "0x7a34b6e8c9d1234567890abcdef1234567890abcdef1234567890abcdef1234",
            timestampInMillis = 1_752_379_200_000L,
            isOutgoing = false,
            destinationType = TxInfo.DestinationType.Single(
                TxInfo.AddressType.User("0x1111111111111111111111111111111111111111"),
            ),
            sourceType = TxInfo.SourceType.Single("0x2222222222222222222222222222222222222222"),
            interactionAddressType = TxInfo.InteractionAddressType.User(
                "0x2222222222222222222222222222222222222222",
            ),
            status = TxInfo.TransactionStatus.Confirmed,
            type = TxInfo.TransactionType.Transfer,
            amount = BigDecimal("100.00"),
        ),
        TxInfo(
            txHash = "0x9c56d7f1a2b34567890abcdef1234567890abcdef1234567890abcdef123456",
            timestampInMillis = 1_752_292_800_000L,
            isOutgoing = true,
            destinationType = TxInfo.DestinationType.Single(
                TxInfo.AddressType.User("0x3333333333333333333333333333333333333333"),
            ),
            sourceType = TxInfo.SourceType.Single("0x1111111111111111111111111111111111111111"),
            interactionAddressType = TxInfo.InteractionAddressType.User(
                "0x3333333333333333333333333333333333333333",
            ),
            status = TxInfo.TransactionStatus.Confirmed,
            type = TxInfo.TransactionType.Transfer,
            amount = BigDecimal("25.50"),
        ),
    )
}
