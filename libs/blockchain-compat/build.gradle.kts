plugins {
    alias(deps.plugins.kotlin.jvm)
    id("configuration")
}

dependencies {
    implementation(projects.libs.cardSdkCompat)
    implementation(deps.kotlin.coroutines)
}
