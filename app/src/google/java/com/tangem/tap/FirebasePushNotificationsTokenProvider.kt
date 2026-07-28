package com.tangem.tap

import com.google.firebase.messaging.FirebaseMessaging
import com.tangem.utils.logging.TangemLogger
import com.tangem.utils.notifications.PushNotificationsTokenProvider
import com.tangem.wallet.BuildConfig
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

internal class FirebasePushNotificationsTokenProvider @Inject constructor() : PushNotificationsTokenProvider {
    override suspend fun getToken(): String {
        if (BuildConfig.NFC_DEMO_ENABLED) return ""

        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (ex: Exception) {
            TangemLogger.e("Error", ex)
            ""
        }
    }
}
