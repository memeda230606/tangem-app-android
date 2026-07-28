plugins {
    alias(deps.plugins.android.library)
    alias(deps.plugins.kotlin.android)
    id("configuration")
}

android {
    namespace = "com.tangem.features.wallet.api"
}

dependencies {
    /** AndroidX */
    implementation(deps.androidx.fragment.ktx)

    /** Project - Domain */
    implementation(projects.domain.models)
    implementation(projects.domain.visa.models)

    /** Tangem libraries */
    implementation(projects.libs.cardSdkCompat)

    /** Core */
    implementation(projects.core.ui)
    implementation(projects.core.decompose)
    implementation(projects.common.ui)

    /** Other */
    implementation(deps.kotlin.immutable.collections)
}