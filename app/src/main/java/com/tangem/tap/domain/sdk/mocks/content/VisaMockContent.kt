package com.tangem.tap.domain.sdk.mocks.content

import com.tangem.common.SuccessResponse
import com.tangem.common.card.FirmwareVersion
import com.tangem.crypto.hdWallet.bip32.ExtendedPublicKey
import com.tangem.domain.models.scan.CardDTO
import com.tangem.domain.models.scan.ProductType
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.operations.derivation.DerivationTaskResponse
import com.tangem.operations.wallet.CreateWalletResponse
import com.tangem.sdk.api.CreateProductWalletTaskResponse
import com.tangem.tap.domain.sdk.mocks.MockContent

/**
 * Visual-only Visa card fixture.
 *
 * The product type drives the mocked onboarding route; its firmware and batch ID also match Visa detection rules.
 */
object VisaMockContent : MockContent {

    private const val CARD_ID = "AE05888888880018"

    override val cardDto: CardDTO = Wallet2MockContent.cardDto.copy(
        cardId = CARD_ID,
        batchId = "AE05",
        firmwareVersion = CardDTO.FirmwareVersion(
            major = 5,
            minor = 30,
            patch = 0,
            type = FirmwareVersion.FirmwareType.Release,
        ),
        settings = Wallet2MockContent.cardDto.settings.copy(
            maxWalletsCount = 1,
            isBackupAllowed = false,
            isHDWalletAllowed = false,
        ),
        isAccessCodeSet = false,
        backupStatus = CardDTO.BackupStatus.NoBackup,
    )

    override val scanResponse: ScanResponse = Wallet2MockContent.scanResponse.copy(
        card = cardDto,
        productType = ProductType.Visa,
    )

    override val successResponse: SuccessResponse = SuccessResponse(cardId = CARD_ID)

    override val derivationTaskResponse: DerivationTaskResponse = Wallet2MockContent.derivationTaskResponse

    override val extendedPublicKey: ExtendedPublicKey = Wallet2MockContent.extendedPublicKey

    override val createProductWalletTaskResponse: CreateProductWalletTaskResponse =
        Wallet2MockContent.createProductWalletTaskResponse.copy(card = cardDto)

    override val importWalletResponse: CreateProductWalletTaskResponse
        get() = createProductWalletTaskResponse

    override val finalizeTwinResponse: ScanResponse
        get() = error("Available only for Twin")

    override val createFirstTwinResponse: CreateWalletResponse
        get() = error("Available only for Twin")

    override val createSecondTwinResponse: CreateWalletResponse
        get() = error("Available only for Twin")
}
