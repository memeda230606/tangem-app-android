package com.tangem.data.visa

import com.tangem.domain.visa.model.TangemPayAuthTokens
import com.tangem.domain.visa.model.VisaActivationOrderInfo
import com.tangem.domain.visa.model.VisaAuthChallenge
import com.tangem.domain.visa.model.VisaAuthSession
import com.tangem.domain.visa.model.VisaAuthTokens

internal object MockVisaFixtures {
    const val CUSTOMER_WALLET_ADDRESS = "0x0000000000000000000000000000000000000002"
    const val CARD_WALLET_ADDRESS = "0x0000000000000000000000000000000000000003"
    const val HASH_TO_SIGN = "0000000000000000000000000000000000000000000000000000000000000001"

    val activationOrderInfo = VisaActivationOrderInfo(
        orderId = "mock-activation-order",
        customerId = "mock-customer",
        customerWalletAddress = CUSTOMER_WALLET_ADDRESS,
        cardWalletAddress = CARD_WALLET_ADDRESS,
    )

    val cardChallenge = VisaAuthChallenge.Card(
        challenge = HASH_TO_SIGN,
        session = VisaAuthSession("mock-card-session"),
    )

    val walletChallenge = VisaAuthChallenge.Wallet(
        challenge = HASH_TO_SIGN,
        session = VisaAuthSession("mock-wallet-session"),
    )

    val visaAuthTokens = VisaAuthTokens(
        accessToken = "mock-visa-access-token",
        refreshToken = VisaAuthTokens.RefreshToken(
            value = "mock-visa-refresh-token",
            authType = VisaAuthTokens.RefreshToken.Type.CardWallet,
        ),
    )

    val tangemPayAuthTokens = TangemPayAuthTokens(
        accessToken = "mock-tangem-pay-access-token",
        expiresAt = 9_999_999_999L,
        refreshToken = "mock-tangem-pay-refresh-token",
        refreshExpiresAt = 9_999_999_999L,
        idempotencyKey = "mock-idempotency-key",
    )
}
