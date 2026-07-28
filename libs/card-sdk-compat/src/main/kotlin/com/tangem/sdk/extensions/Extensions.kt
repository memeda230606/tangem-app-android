package com.tangem.sdk.extensions

import com.tangem.TangemSdk
import com.tangem.crypto.bip39.Wordlist
import com.tangem.operations.backup.BackupService

fun BackupService.Companion.init(tangemSdk: TangemSdk, activity: Any): BackupService = BackupService()

fun Wordlist.Companion.getWordlist(): Wordlist = Wordlist.getWordlist()
