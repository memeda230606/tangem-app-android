plugins {
    alias(deps.plugins.kotlin.android) apply false
    alias(deps.plugins.kotlin.jvm) apply false
    alias(deps.plugins.kotlin.serialization) apply false
    alias(deps.plugins.kotlin.kapt) apply false
    alias(deps.plugins.android.application) apply false
    alias(deps.plugins.android.library) apply false
    alias(deps.plugins.hilt.android) apply false
    alias(deps.plugins.google.services) apply false
    alias(deps.plugins.firebase.crashlytics) apply false
    alias(deps.plugins.firebase.perf) apply false
    alias(deps.plugins.room) apply false
    alias(deps.plugins.kotlin.compose.compiler) apply false
    alias(deps.plugins.ksp) apply false
}

buildscript {
    dependencies {
        classpath(deps.gradle.android)
        classpath(deps.agconnect.agcp)
    }
}

val clean by tasks.registering {
    delete(rootProject.buildDir)
}

interface Injected {
    @get:Inject
    val fs: FileSystemOperations
}

// Test task to run unit tests for debug/googleDebug variant (Android) and all JVM modules
val unitTest by tasks.registering {
    group = "verification"
    description = "Run unit tests for debug/googleDebug variant and all JVM modules"
}

subprojects {
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("com.tangem:blockchain")).using(project(":libs:blockchain-compat"))
            substitute(module("com.tangem.tangem-sdk-kotlin:core")).using(project(":libs:card-sdk-compat"))
            substitute(module("com.tangem.tangem-sdk-kotlin:android")).using(project(":libs:card-sdk-compat"))
            substitute(module("com.tangem.tangem-hot-sdk-kotlin:core")).using(project(":libs:hot-sdk-compat"))
            substitute(module("com.tangem.tangem-hot-sdk-kotlin:android")).using(project(":libs:hot-sdk-compat"))
            substitute(module("com.tangem.vico:core")).using(module("com.patrykandpatrick.vico:core:2.0.0"))
            substitute(module("com.tangem.vico:compose")).using(module("com.patrykandpatrick.vico:compose:2.0.0"))
            substitute(module("com.tangem.vico:compose-m3")).using(module("com.patrykandpatrick.vico:compose-m3:2.0.0"))
        }
    }

    // App module
    plugins.withId("com.android.application") {
        afterEvaluate {
            unitTest.configure { dependsOn(tasks.named("testGoogleDebugUnitTest")) }
        }
    }

    // Android libraries
    plugins.withId("com.android.library") {
        afterEvaluate {
            unitTest.configure { dependsOn(tasks.named("testDebugUnitTest")) }
        }
    }

    // Jvm modules
    plugins.withId("org.jetbrains.kotlin.jvm") {
        if (!plugins.hasPlugin("com.android.library") && !plugins.hasPlugin("com.android.application")) {
            unitTest.configure { dependsOn(tasks.named("test")) }
        }
    }
}

val assembleInternalQA by tasks.registering {
    group = "build"
    description = "Builds internal APK to 'build/outputs' directory"

    val appOutputApkDir = "$projectDir/app/build/outputs/apk/internal"
    val rootOutputApkDir = "$buildDir/outputs"
    val injected = objects.newInstance<Injected>()

    dependsOn(":app:assembleInternal")

    doFirst {
        injected.fs.delete {
            delete(appOutputApkDir)
            delete("$rootOutputApkDir/app-internal.apk")
        }
    }
    doLast {
        injected.fs.copy {
            from("$appOutputApkDir/app-internal.apk")
            into(rootOutputApkDir)
        }
    }
}

val assembleExternalQA by tasks.registering {
    group = "build"
    description = "Builds external APK to 'build/outputs' directory"

    val appOutputApkDir = "$projectDir/app/build/outputs/apk/external"
    val rootOutputApkDir = "$buildDir/outputs"
    val injected = objects.newInstance<Injected>()

    dependsOn(":app:assembleExternal")

    doFirst {
        injected.fs.delete {
            delete(appOutputApkDir)
            delete("$rootOutputApkDir/app-external.apk")
        }
    }
    doLast {
        injected.fs.copy {
            from("$appOutputApkDir/app-external.apk")
            into(rootOutputApkDir)
        }
    }
}

val assembleQA by tasks.registering {
    group = "build"
    description = "Builds internal and external APKs to 'build/outputs' directory"

    dependsOn(assembleInternalQA)
    dependsOn(assembleExternalQA)
}

val generateComposeMetrics by tasks.registering {
    group = "other"
    description = "Build external APK and generates compose metrics to 'build/compose-metrics' directory"

    subprojects {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            if (name.contains("compile")) {
                val outputDirectory = "${project.buildDir.absolutePath}/compose_metrics"
                compilerOptions {
                    freeCompilerArgs.addAll(
                        listOf(
                            "-P",
                            "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=$outputDirectory",
                            "-P",
                            "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=$outputDirectory"
                            //     "-P",
                            //     "plugin:androidx.compose.compiler.plugins.kotlin:experimentalStrongSkipping=true",
                        )
                    )
                }
            }
        }
    }

    finalizedBy(assembleExternalQA)
}
