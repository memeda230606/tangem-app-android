plugins {
    alias(deps.plugins.android.library)
    alias(deps.plugins.kotlin.android)
    alias(deps.plugins.kotlin.serialization)
    id("configuration")
}

android {
    namespace = "com.tangem.domain.staking"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    api(projects.domain.staking.models)
    api(projects.domain.core)
    api(projects.core.analytics)
    api(projects.core.utils)

    implementation(deps.kotlin.datetime)
    implementation(deps.kotlin.serialization)
    implementation(deps.jodatime)

    implementation(projects.domain.legacy)
    implementation(projects.domain.walletManager) // TODO refactor to use from data module
    implementation(projects.domain.models)
    implementation(projects.domain.tokens.models)
    implementation(projects.domain.wallets.models)

    implementation(projects.libs.blockchainCompat) {
        exclude(module = "joda-time")
    }
    implementation(projects.libs.crypto)
    implementation(projects.libs.blockchainSdk)

    testRuntimeOnly(deps.test.junit5.engine)
    testImplementation(projects.libs.cardSdkCompat)
    testImplementation(projects.common.test)
    testImplementation(projects.test.core)
}