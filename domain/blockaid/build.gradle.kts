plugins {
    alias(deps.plugins.android.library)
    alias(deps.plugins.kotlin.android)
    id("configuration")
}

android {
    namespace = "com.tangem.domain.blockaid"
}


dependencies {
    /** Project - Domain */
    implementation(projects.domain.models)
    implementation(projects.domain.core)
    implementation(projects.domain.blockaid.models)

    /** Tangem SDK */
    implementation(projects.libs.blockchainCompat)


    /** Other */
    implementation(deps.moshi.adapters)
}