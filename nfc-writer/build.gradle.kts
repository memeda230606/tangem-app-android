import java.util.Properties

plugins {
    alias(deps.plugins.android.application)
}

val secureCardProperties = Properties().apply {
    rootProject.file("secure-card.properties")
        .takeIf(File::isFile)
        ?.inputStream()
        ?.use(::load)
}

fun secureCardSecret(propertyName: String, environmentName: String): String =
    secureCardProperties.getProperty(propertyName)
        ?: System.getenv(environmentName)
        ?: ""

android {
    namespace = "com.niubtmd.nfcwriter"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.niubtmd.nfcwriter"
        minSdk = 24
        targetSdk = 36
        versionCode = 8
        versionName = "1.5.0"
        buildConfigField(
            "String",
            "SECURE_CARD_ADMIN_ROOT",
            "\"${secureCardSecret("adminRoot", "SECURE_CARD_ADMIN_ROOT")}\"",
        )
        buildConfigField(
            "String",
            "SECURE_CARD_READ_ROOT",
            "\"${secureCardSecret("readRoot", "SECURE_CARD_READ_ROOT")}\"",
        )
        buildConfigField(
            "String",
            "SECURE_CARD_WRITE_ROOT",
            "\"${secureCardSecret("writeRoot", "SECURE_CARD_WRITE_ROOT")}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.secureNfc)
}
