plugins {
    alias(deps.plugins.android.library)
    alias(deps.plugins.kotlin.android)
    alias(deps.plugins.kotlin.kapt)
    alias(deps.plugins.kotlin.serialization)
    id("configuration")
}

android {
    namespace = "com.tangem.lib.crypto"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {

    // region Project
    implementation(projects.core.utils)
    implementation(projects.libs.blockchainSdk)
    // endregion

    // region Tangem SDKs
    implementation(projects.libs.cardSdkCompat)
    implementation(projects.libs.blockchainCompat)
    // endregion

    // region Other deps
    implementation(deps.kotlin.coroutines)
    // endregion

    // region Test libraries
    testImplementation(projects.test.core)
    testRuntimeOnly(deps.test.junit5.engine)
    // endregion
}