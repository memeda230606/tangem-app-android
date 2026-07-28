package com.tangem.tap.domain.nfc

import com.tangem.common.card.Card
import com.tangem.common.card.CardWallet
import com.tangem.common.card.EllipticCurve
import com.tangem.common.card.EncryptionMode
import com.tangem.common.card.FirmwareVersion
import com.tangem.crypto.bip39.Mnemonic
import com.tangem.data.wallets.nfc.NfcWalletCrypto
import com.tangem.domain.models.MobileWallet
import com.tangem.domain.models.scan.CardDTO
import com.tangem.domain.models.scan.ProductType
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.domain.models.wallet.UserWallet
import com.tangem.domain.wallets.builder.HotUserWalletBuilder
import com.tangem.domain.wallets.nfc.NfcCardGateway
import com.tangem.domain.wallets.nfc.NfcEncryptedWalletOnboardingRepository
import com.tangem.domain.wallets.nfc.NfcWalletAesKey
import com.tangem.domain.wallets.nfc.NfcWalletBlob
import com.tangem.domain.wallets.nfc.NfcWalletKeyRepository
import com.tangem.hot.sdk.TangemHotSdk
import com.tangem.hot.sdk.model.HotAuth
import com.tangem.operations.attestation.Attestation
import com.tangem.operations.backup.PrimaryCard
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultNfcEncryptedWalletOnboardingRepository @Inject constructor(
    private val nfcCardGateway: NfcCardGateway,
    private val nfcWalletKeyRepository: NfcWalletKeyRepository,
    private val hotSdk: TangemHotSdk,
    private val hotUserWalletBuilderFactory: HotUserWalletBuilder.Factory,
) : NfcEncryptedWalletOnboardingRepository {

    private val crypto = NfcWalletCrypto()

    private var pendingState: PendingState? = null

    override suspend fun preparePrimaryCard(): ScanResponse {
        val existingBlob = nfcCardGateway.readWalletBlob()
        require(existingBlob == null) { "NFC card already contains an encrypted wallet payload" }

        pendingState = null

        return createScanResponse(
            userWallet = null,
            primaryCard = null,
            cardId = NFC_PRIMARY_CARD_ID,
            backupStatus = CardDTO.BackupStatus.NoBackup,
        )
    }

    override suspend fun createPrimaryWallet(mnemonic: Mnemonic, passphrase: String?): ScanResponse {
        val hotWalletId = hotSdk.importWallet(mnemonic, passphrase?.toCharArray(), HotAuth.NoAuth)
        val hotWallet = hotUserWalletBuilderFactory.create(hotWalletId).build()
        val aesKey = crypto.generateAesKey()
        val localKeyId = UUID.randomUUID().toString()
        val backupSetId = UUID.randomUUID().toString()
        val primaryCardInstanceId = UUID.randomUUID().toString()

        val userWallet = UserWallet.NfcEncrypted(
            name = hotWallet.name,
            walletId = hotWallet.walletId,
            cardsInWallet = setOf(primaryCardInstanceId),
            hotWalletId = hotWalletId,
            wallets = hotWallet.wallets,
            localKeyId = localKeyId,
            backupSetId = backupSetId,
            backedUp = false,
        )
        val seedPayload = mnemonic.toSeedPayload(passphrase)

        writeEncryptedSeed(
            seedPayload = seedPayload,
            aesKey = aesKey,
            userWallet = userWallet,
            cardInstanceId = primaryCardInstanceId,
        )

        val primaryCard = createPrimaryCard(
            cardId = primaryCardInstanceId.toCardId(),
            userWallet = userWallet,
        )
        val scanResponse = createScanResponse(
            userWallet = userWallet,
            primaryCard = primaryCard,
            cardId = primaryCard.cardId,
            backupStatus = CardDTO.BackupStatus.NoBackup,
        )

        pendingState = PendingState(
            userWallet = userWallet,
            aesKey = aesKey,
            seedPayload = seedPayload,
            primaryCardInstanceId = primaryCardInstanceId,
            backupCardInstanceIds = emptyList(),
            scanResponse = scanResponse,
        )

        return scanResponse
    }

    override suspend fun addBackupCard(): CardDTO {
        val state = requireNotNull(pendingState) { "NFC wallet onboarding is not started" }
        val cardInstanceId = UUID.randomUUID().toString()

        writeEncryptedSeed(
            seedPayload = state.seedPayload,
            aesKey = state.aesKey,
            userWallet = state.userWallet,
            cardInstanceId = cardInstanceId,
        )

        val updatedBackupIds = state.backupCardInstanceIds + cardInstanceId
        val updatedWallet = state.userWallet.copy(
            cardsInWallet = setOf(state.primaryCardInstanceId) + updatedBackupIds,
            backedUp = true,
        )
        val updatedScanResponse = createScanResponse(
            userWallet = updatedWallet,
            primaryCard = state.scanResponse.primaryCard,
            cardId = state.primaryCardInstanceId.toCardId(),
            backupStatus = CardDTO.BackupStatus.Active(updatedBackupIds.size),
        )

        pendingState = state.copy(
            userWallet = updatedWallet,
            backupCardInstanceIds = updatedBackupIds,
            scanResponse = updatedScanResponse,
        )

        return createCard(
            cardId = cardInstanceId.toCardId(),
            wallets = createCardWallets(updatedWallet.wallets),
            backupStatus = CardDTO.BackupStatus.Active(updatedBackupIds.size),
        )
    }

    override suspend fun verifyPrimaryCard() {
        val state = requireNotNull(pendingState) { "NFC wallet onboarding is not started" }
        val blob = requireNotNull(nfcCardGateway.readWalletBlob()) { "NFC card has no encrypted wallet payload" }

        blob.requireMatches(state, state.primaryCardInstanceId)
    }

    override suspend fun verifyBackupCard(cardIndex: Int) {
        val state = requireNotNull(pendingState) { "NFC wallet onboarding is not started" }
        val cardInstanceId = requireNotNull(state.backupCardInstanceIds.getOrNull(cardIndex)) {
            "NFC wallet backup card is not added"
        }
        val blob = requireNotNull(nfcCardGateway.readWalletBlob()) { "NFC card has no encrypted wallet payload" }

        blob.requireMatches(state, cardInstanceId)
    }

    override suspend fun finalize(accessCode: CharArray?): UserWallet.NfcEncrypted {
        val state = requireNotNull(pendingState) { "NFC wallet onboarding is not started" }
        val userWallet = state.userWallet.copy(
            backedUp = state.backupCardInstanceIds.isNotEmpty(),
            cardsInWallet = setOf(state.primaryCardInstanceId) + state.backupCardInstanceIds,
        )

        nfcWalletKeyRepository.save(
            key = NfcWalletAesKey(
                walletId = userWallet.walletId,
                localKeyId = userWallet.localKeyId,
                key = state.aesKey,
            ),
            accessCode = accessCode,
        )
        pendingState = state.copy(userWallet = userWallet)

        return userWallet
    }

    override fun isNfcScanResponse(scanResponse: ScanResponse): Boolean {
        return scanResponse.card.cardId.startsWith(NFC_CARD_ID_PREFIX)
    }

    override fun clear() {
        pendingState = null
    }

    private suspend fun writeEncryptedSeed(
        seedPayload: ByteArray,
        aesKey: ByteArray,
        userWallet: UserWallet.NfcEncrypted,
        cardInstanceId: String,
    ) {
        val blob = crypto.encryptSeed(
            seed = seedPayload,
            aesKey = aesKey,
            walletId = userWallet.walletId,
            cardInstanceId = cardInstanceId,
            backupSetId = userWallet.backupSetId,
            localKeyId = userWallet.localKeyId,
        )

        nfcCardGateway.writeWalletBlob(blob)
    }

    private fun NfcWalletBlob.requireMatches(state: PendingState, cardInstanceId: String) {
        require(walletId == state.userWallet.walletId) { "Wrong NFC wallet card" }
        require(backupSetId == state.userWallet.backupSetId) { "Wrong NFC wallet backup set" }
        require(localKeyId == state.userWallet.localKeyId) { "Wrong NFC wallet local key" }
        require(this.cardInstanceId == cardInstanceId) { "Wrong NFC wallet card instance" }

        crypto.decryptSeed(this, state.aesKey)
    }

    private fun createScanResponse(
        userWallet: UserWallet.NfcEncrypted?,
        primaryCard: PrimaryCard?,
        cardId: String,
        backupStatus: CardDTO.BackupStatus?,
    ): ScanResponse {
        return ScanResponse(
            card = createCard(
                cardId = cardId,
                wallets = createCardWallets(userWallet?.wallets),
                backupStatus = backupStatus,
            ),
            productType = ProductType.Wallet2,
            walletData = null,
            primaryCard = primaryCard,
        )
    }

    private fun createPrimaryCard(
        cardId: String,
        userWallet: UserWallet.NfcEncrypted,
    ): PrimaryCard {
        return PrimaryCard(
            cardId = cardId,
            batchId = NFC_BATCH_ID,
            cardPublicKey = userWallet.wallets?.firstOrNull()?.publicKey ?: NFC_CARD_PUBLIC_KEY,
            linkingKey = NFC_LINKING_KEY,
            existingWalletsCount = userWallet.wallets?.size ?: 0,
            isHDWalletAllowed = true,
            issuer = Card.Issuer(
                name = NFC_ISSUER_NAME,
                publicKey = NFC_ISSUER_PUBLIC_KEY,
            ),
            manufacturer = Card.Manufacturer(
                name = NFC_MANUFACTURER_NAME,
                manufactureDate = Date(NFC_MANUFACTURE_DATE),
                signature = ByteArray(0),
            ),
            walletCurves = SUPPORTED_CURVES,
            firmwareVersion = FirmwareVersion(
                major = NFC_FIRMWARE_MAJOR,
                minor = NFC_FIRMWARE_MINOR,
                patch = 0,
                type = FirmwareVersion.FirmwareType.Release,
            ),
            isKeysImportAllowed = true,
            certificate = null,
        )
    }

    private fun createCard(
        cardId: String,
        wallets: List<CardDTO.Wallet>,
        backupStatus: CardDTO.BackupStatus?,
    ): CardDTO {
        return CardDTO(
            cardId = cardId,
            batchId = NFC_BATCH_ID,
            cardPublicKey = wallets.firstOrNull()?.publicKey ?: NFC_CARD_PUBLIC_KEY,
            firmwareVersion = CardDTO.FirmwareVersion(
                major = NFC_FIRMWARE_MAJOR,
                minor = NFC_FIRMWARE_MINOR,
                patch = 0,
                type = FirmwareVersion.FirmwareType.Release,
            ),
            manufacturer = CardDTO.Manufacturer(
                name = NFC_MANUFACTURER_NAME,
                manufactureDate = Date(NFC_MANUFACTURE_DATE),
                signature = ByteArray(0),
            ),
            issuer = CardDTO.Issuer(
                name = NFC_ISSUER_NAME,
                publicKey = NFC_ISSUER_PUBLIC_KEY,
            ),
            settings = CardDTO.Settings(
                securityDelay = 0,
                maxWalletsCount = SUPPORTED_CURVES.size,
                isSettingAccessCodeAllowed = true,
                isSettingPasscodeAllowed = false,
                isResettingUserCodesAllowed = false,
                isLinkedTerminalEnabled = false,
                isBackupAllowed = true,
                supportedEncryptionModes = listOf(EncryptionMode.Strong),
                isFilesAllowed = false,
                isHDWalletAllowed = true,
                isKeysImportAllowed = true,
            ),
            userSettings = CardDTO.UserSettings(isUserCodeRecoveryAllowed = false),
            linkedTerminalStatus = CardDTO.LinkedTerminalStatus.None,
            isAccessCodeSet = true,
            isPasscodeSet = false,
            supportedCurves = SUPPORTED_CURVES,
            wallets = wallets,
            attestation = Attestation(
                cardKeyAttestation = Attestation.Status.Verified,
                walletKeysAttestation = Attestation.Status.Skipped,
                firmwareAttestation = Attestation.Status.Skipped,
                cardUniquenessAttestation = Attestation.Status.Skipped,
            ),
            backupStatus = backupStatus,
        )
    }

    private fun createCardWallets(wallets: List<MobileWallet>?): List<CardDTO.Wallet> {
        return wallets.orEmpty().mapIndexed { index, wallet ->
            CardDTO.Wallet(
                publicKey = wallet.publicKey,
                chainCode = wallet.chainCode,
                curve = wallet.curve,
                settings = CardWallet.Settings(isPermanent = false),
                totalSignedHashes = 0,
                remainingSignatures = null,
                index = index,
                hasBackup = true,
                derivedKeys = wallet.derivedKeys,
                extendedPublicKey = wallet.extendedPublicKey,
                isImported = true,
            )
        }
    }

    private fun Mnemonic.toSeedPayload(passphrase: String?): ByteArray {
        return buildString {
            append(mnemonicComponents.joinToString(separator = " "))
            if (!passphrase.isNullOrEmpty()) {
                append('\n')
                append(passphrase)
            }
        }.encodeToByteArray()
    }

    private fun String.toCardId(): String {
        val compact = filter(Char::isLetterOrDigit).uppercase()
        return NFC_CARD_ID_PREFIX + compact.takeLast(NFC_CARD_ID_RANDOM_PART_LENGTH).padStart(
            length = NFC_CARD_ID_RANDOM_PART_LENGTH,
            padChar = '0',
        )
    }

    private data class PendingState(
        val userWallet: UserWallet.NfcEncrypted,
        val aesKey: ByteArray,
        val seedPayload: ByteArray,
        val primaryCardInstanceId: String,
        val backupCardInstanceIds: List<String>,
        val scanResponse: ScanResponse,
    )

    private companion object {
        const val NFC_CARD_ID_PREFIX = "NFCW"
        const val NFC_CARD_ID_RANDOM_PART_LENGTH = 12
        const val NFC_PRIMARY_CARD_ID = "NFCW000000000000"
        const val NFC_BATCH_ID = "NFC1"
        const val NFC_MANUFACTURER_NAME = "NFC"
        const val NFC_ISSUER_NAME = "Tangem NFC Wallet"
        const val NFC_MANUFACTURE_DATE = 1_735_689_600_000L
        const val NFC_FIRMWARE_MAJOR = 6
        const val NFC_FIRMWARE_MINOR = 33

        val NFC_CARD_PUBLIC_KEY = byteArrayOf(
            3, 23, -112, -57, 109, 60, -82, -36, 45, -14, -34, -12, 10, -89, 14, 37,
            38, 36, -102, 37, 93, 90, 69, -113, -117, 120, -29, 12, -125, 43, -40, -31, 5,
        )
        val NFC_LINKING_KEY = byteArrayOf(
            2, 121, 98, 127, -70, 14, 5, -23, -76, 115, -30, -26, 111, 17, 110, 34,
            -100, -121, -57, -123, 74, 3, -91, 56, -20, 56, 50, -40, -101, 96, 82, 70, -91,
        )
        val NFC_ISSUER_PUBLIC_KEY = byteArrayOf(
            2, 95, 22, -67, 29, 46, -81, -28, 99, -26, 42, 51, 90, 9, -26, -78,
            -69, -53, -48, 68, 82, 82, 104, -123, -53, 103, -97, -60, -46, 122, -15, -67, 34,
        )
        val SUPPORTED_CURVES = listOf(
            EllipticCurve.Secp256k1,
            EllipticCurve.Ed25519,
            EllipticCurve.Bls12381G2Aug,
            EllipticCurve.Secp256r1,
            EllipticCurve.Ed25519Slip0010,
            EllipticCurve.Bls12381G2,
            EllipticCurve.Bls12381G2Pop,
            EllipticCurve.Bip0340,
        )
    }
}
