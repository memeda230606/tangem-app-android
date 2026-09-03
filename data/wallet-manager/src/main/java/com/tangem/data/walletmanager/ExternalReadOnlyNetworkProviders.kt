package com.tangem.data.walletmanager

import com.tangem.blockchain.blockchains.bitcoin.BitcoinUnspentOutput
import com.tangem.blockchain.blockchains.bitcoin.network.BitcoinAddressInfo
import com.tangem.blockchain.blockchains.bitcoin.network.BitcoinFee
import com.tangem.blockchain.blockchains.bitcoin.network.BitcoinNetworkProvider
import com.tangem.blockchain.blockchains.bitcoin.network.XpubInfoResponse
import com.tangem.blockchain.blockchains.ton.models.GetJettonWalletAddressInput
import com.tangem.blockchain.blockchains.ton.network.TonAccountState
import com.tangem.blockchain.blockchains.ton.network.TonGetFeeResponse
import com.tangem.blockchain.blockchains.ton.network.TonGetWalletInfoResponse
import com.tangem.blockchain.blockchains.ton.network.TonNetworkProvider
import com.tangem.blockchain.blockchains.ton.network.TonSendBocResponse
import com.tangem.blockchain.common.Blockchain
import com.tangem.blockchain.extensions.Result
import com.tangem.blockchain.extensions.SimpleResult
import java.io.IOException
import java.math.BigInteger

/** SDK-compatible, balance-only providers used when no authenticated provider can be created. */
internal class ExternalBitcoinCashNetworkProvider(
    private val balanceFallback: ExternalNetworkBalanceFallback,
) : BitcoinNetworkProvider {

    override val baseUrl: String = "https://api.3xpl.com"

    override suspend fun getInfo(address: String): Result<BitcoinAddressInfo> {
        val balance = balanceFallback.getBalance(address, Blockchain.BitcoinCash)
        return Result.Success(
            BitcoinAddressInfo(
                balance = balance,
                unspentOutputs = emptyList(),
                recentTransactions = emptyList(),
                hasUnconfirmed = false,
            ),
        )
    }

    override suspend fun getFee(): Result<BitcoinFee> = unsupported()
    override suspend fun sendTransaction(transaction: String): SimpleResult = unsupported()
    override suspend fun getSignatureCount(address: String): Result<Int> = unsupported()
    override suspend fun getInfoByXpub(xpub: String): Result<XpubInfoResponse> = unsupported()
    override suspend fun getUtxoByXpub(xpub: String): Result<List<BitcoinUnspentOutput>> = unsupported()
}

internal class ExternalTonNetworkProvider(
    private val balanceFallback: ExternalNetworkBalanceFallback,
) : TonNetworkProvider {

    override val baseUrl: String = "https://tonapi.io"

    override suspend fun getWalletInformation(address: String): Result<TonGetWalletInfoResponse> {
        val balance = balanceFallback.getBalance(address, Blockchain.TON)
        return Result.Success(
            TonGetWalletInfoResponse(
                wallet = true,
                balance = balance,
                accountState = if (balance.signum() == 0) TonAccountState.UNINITIALIZED else TonAccountState.ACTIVE,
                seqno = if (balance.signum() == 0) null else 0,
            ),
        )
    }

    override suspend fun getFee(address: String, message: String): Result<TonGetFeeResponse> = unsupported()
    override suspend fun send(boc: String): Result<TonSendBocResponse> = unsupported()
    override suspend fun getJettonWalletAddress(input: GetJettonWalletAddressInput): Result<String> = unsupported()
    override suspend fun getJettonBalance(address: String): Result<BigInteger> = unsupported()
    override suspend fun isJettonWalletActive(address: String): Result<Boolean> = unsupported()
}

private fun <T> unsupported(): T = throw IOException(
    "Authenticated provider gateway is required for this operation",
)
