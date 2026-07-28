plugins {
    alias(deps.plugins.kotlin.jvm)
    alias(deps.plugins.kotlin.serialization)
    id("configuration")
}

dependencies {
    implementation(projects.libs.cardSdkCompat)
    implementation(deps.kotlin.serialization)
}
