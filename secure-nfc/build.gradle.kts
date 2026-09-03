plugins {
    alias(deps.plugins.android.library)
}

android {
    namespace = "com.niubtmd.securenfc"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation(deps.test.junit)
}
