plugins {
    alias(deps.plugins.android.library)
    alias(deps.plugins.kotlin.android)
    alias(deps.plugins.kotlin.compose.compiler)
    id("configuration")
}

android {
    namespace = "com.tangem.libs.vico_compat"

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(deps.vico.core)
    api(deps.vico.compose)
    api(deps.vico.compose.m3)

    implementation(deps.compose.runtime)
    implementation(deps.compose.ui)
}
