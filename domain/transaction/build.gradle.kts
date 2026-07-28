plugins {
    alias(deps.plugins.android.library)
    alias(deps.plugins.kotlin.android)
    id("configuration")
}

android {
    namespace = "com.tangem.domain.transaction"
}

dependencies {
    implementation(deps.kotlin.coroutines)
    implementation(deps.arrow.core)

    implementation(projects.core.utils)
    implementation(projects.core.ui)
    implementation(projects.core.datasource)

    /** Tangem SDKs */
    implementation(projects.libs.cardSdkCompat)
    implementation(projects.libs.cardSdkCompat) {
        exclude(module = "joda-time")
    }
    implementation(projects.libs.blockchainCompat)

    implementation(projects.libs.blockchainSdk)
    implementation(projects.libs.crypto)

    implementation(projects.domain.account.status)
    implementation(projects.domain.common)
    implementation(projects.domain.dynamicAddresses)
    implementation(projects.domain.dynamicAddresses.models)
    implementation(projects.domain.models)
    implementation(projects.domain.legacy)
    implementation(projects.domain.walletManager)
    implementation(projects.domain.wallets.models)
    implementation(projects.domain.tokens)
    implementation(projects.domain.tokens.models)
    implementation(projects.domain.transaction.models)
    implementation(projects.domain.demo)
    implementation(projects.domain.card)
    implementation(projects.domain.notifications)
    api(projects.domain.networks)

    testRuntimeOnly(deps.test.junit5.engine)
    testImplementation(projects.common.test)
    testImplementation(projects.test.core)
    testImplementation(projects.test.mock)
}