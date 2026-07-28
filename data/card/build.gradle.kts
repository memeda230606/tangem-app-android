plugins {
    alias(deps.plugins.android.library)
    alias(deps.plugins.kotlin.android)
    alias(deps.plugins.kotlin.kapt)
    alias(deps.plugins.hilt.android)
    id("configuration")
}

android {
    namespace = "com.tangem.data.card"
}

dependencies {
    implementation(deps.androidx.datastore)

    implementation(projects.libs.blockchainCompat) {
        exclude(module = "joda-time")
    }

    implementation(deps.hilt.android)
    kapt(deps.hilt.kapt)

    implementation(projects.libs.cardSdkCompat)
    implementation(projects.libs.cardSdkCompat)

    implementation(projects.core.datasource)
    implementation(projects.core.utils)

    implementation(projects.libs.blockchainSdk)

    implementation(projects.domain.card)
    implementation(projects.domain.models)
}