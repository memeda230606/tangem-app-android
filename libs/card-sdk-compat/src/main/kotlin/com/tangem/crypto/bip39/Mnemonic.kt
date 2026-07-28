package com.tangem.crypto.bip39

import com.tangem.common.core.TangemSdkError

open class Mnemonic(
    open val mnemonicComponents: List<String>,
    open val passphrase: String? = null,
) {
    val mnemonic: String
        get() = mnemonicComponents.joinToString(separator = " ")
}

sealed class MnemonicErrorResult {
    data object InvalidChecksum : MnemonicErrorResult()
    data object InvalidWordsCount : MnemonicErrorResult()
    data class InvalidWords(val words: List<String>) : MnemonicErrorResult()
}

enum class EntropyLength(val wordsCount: Int) {
    Bits128Length(wordsCount = 12),
    Bits256Length(wordsCount = 24),
}

class Wordlist(val words: List<String>) {
    companion object {
        fun getWordlist(): Wordlist = Wordlist(defaultWords)

        private val defaultWords = listOf(
            "abandon", "ability", "able", "about", "above", "absent",
            "absorb", "abstract", "absurd", "abuse", "access", "accident",
            "account", "accuse", "achieve", "acid", "acoustic", "acquire",
            "across", "act", "action", "actor", "actress", "actual",
        )
    }
}

class DefaultMnemonic : Mnemonic {
    constructor(entropy: EntropyLength, wordlist: Wordlist) : super(
        mnemonicComponents = wordlist.words.take(entropy.wordsCount),
    )

    constructor(mnemonic: String, wordlist: Wordlist) : super(
        mnemonicComponents = parseMnemonic(mnemonic),
    )

    companion object {
        private fun parseMnemonic(mnemonic: String): List<String> {
            val words = mnemonic.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            if (words.size != 12 && words.size != 24) {
                throw TangemSdkError.MnemonicException(
                    message = "Invalid mnemonic words count",
                    mnemonicResult = MnemonicErrorResult.InvalidWordsCount,
                )
            }
            return words
        }
    }
}
