plugins {
    alias(deps.plugins.android.library)
    alias(deps.plugins.kotlin.android)
    alias(deps.plugins.kotlin.kapt)
    alias(deps.plugins.hilt.android)
    id("configuration")
}

android {
    namespace = "com.tangem.legacy"
}

dependencies {
    implementation(projects.domain.models)
    implementation(projects.domain.visa.models)

    api(projects.core.analytics.models)
    implementation(projects.core.configToggles)
    implementation(projects.core.res)

    /** Tangem libraries */
    implementation(projects.libs.cardSdkCompat)
    implementation(projects.libs.cardSdkCompat) {
        exclude(module = "joda-time")
    }

    /** DI */
    implementation(deps.hilt.android)
    kapt(deps.hilt.kapt)
}