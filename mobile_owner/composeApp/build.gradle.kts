import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.gradle.kotlin.dsl.buildkonfig
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
    alias(libs.plugins.buildkonfig)
}

// Push notifications (FCM). The google-services plugin fails the build outright
// when google-services.json is missing, and that file carries project-specific
// keys that are not in this repository — so it is applied only once somebody has
// dropped theirs into composeApp/. Without it the app still builds and runs;
// PushTokenProvider simply returns no token and the owner falls back to the
// local alarm reminders. See docs/FCM_SETUP.md.
val googleServicesJson = file("google-services.json")
if (googleServicesJson.exists()) {
    apply(plugin = libs.plugins.googleServices.get().pluginId)
}


kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi")
    }

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
       androidMain.dependencies {
           implementation(compose.preview)
           implementation(libs.androidx.activity.compose)
           // Koin Android integration
           implementation(libs.koin.android)
           implementation(libs.koin.compose)
           implementation(libs.ktor.client.okhttp)
           implementation(libs.ktor.client.logginig)
           // FCM. The dependency is unconditional (the Kotlin in
           // core/push/ references it) while the google-services *plugin*
           // above is not — a build with no google-services.json compiles
           // fine and just never obtains a token at runtime.
           implementation(project.dependencies.platform(libs.firebase.bom))
           implementation(libs.firebase.messaging)
       }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // Networking
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logginig)
            // Serialization
            implementation(libs.kotlinx.serialization.json)
            // koin
            implementation(libs.androidx.room.runtime)
            implementation(libs.sqlite.bundled)
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.datetime)

            // navigation
            implementation(libs.navigation.compose)

            // Image Picker & Cropper
            implementation(libs.peekaboo.image.picker)
            implementation(libs.easycrop)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)

            // Glassmorphism blur for bars/toolbars
            implementation(libs.haze)
            implementation(libs.haze.materials)

            implementation(libs.qrose)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            implementation(libs.ktor.client.darwin)
            implementation(libs.ktor.client.logginig)
        }
    }
}

android {
    namespace = "xyz.sattar.javid.proqueue"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "xyz.sattar.javid.proqueue"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = (System.getenv("ANDROID_VERSION_CODE") ?: "100").toInt()
        versionName = System.getenv("ANDROID_VERSION_NAME") ?: "1.0.0"
        // Safe default: anything built without picking the "local" flavor
        // (release, CI, a plain `assembleDebug`) still talks to the real
        // server. See the "local" flavor below for why this can't live in
        // the buildkonfig{} block instead.
        buildConfigField("String", "BASE_URL", "\"https://api.noobatyar.ir\"")
        buildConfigField("String", "BOOKING_BASE_URL", "\"https://app.noobatyar.ir\"")
        buildConfigField("String", "STORE", "\"site\"")
        buildConfigField("String", "MYKET_IAB_PUBLIC_KEY", "\"\"")
    }

    // "prod" talks to the real server and is what release builds/CI use by
    // default. "local" talks to a Django dev server on this machine (see
    // docs/ENVIRONMENTS.md) and gets its own applicationId suffix so both
    // variants can be installed on the same emulator/device side by side.
    //
    // BASE_URL/BOOKING_BASE_URL are set via AGP's own per-flavor
    // buildConfigField (read through AppConfig, not through buildkonfig's
    // BuildKonfig object) specifically because buildkonfig has no concept of
    // Android product flavors: its `defaultConfigs("local")` only activates
    // via the unrelated `-Pbuildkonfig.flavor=local` Gradle property, which
    // nothing in this project ever set. A "local"-flavor build (any
    // xyz.sattar.javid.proqueue.local install, from Android Studio's Run
    // button or a plain `assembleLocalDebug`) was therefore always getting
    // BuildKonfig's un-flavored `defaultConfigs { }` — i.e. the production
    // URL — regardless of which Android flavor it was. AGP's own
    // buildConfigField, by contrast, genuinely varies per product flavor.
    // "store" decides how plans and packs are paid for. "site" (the APK on
    // noobatyar.ir and Telegram) and "bazaar" (uploaded to Cafe Bazaar) use
    // the Zibal gateway. "myket" is the build
    // uploaded to Myket, which requires its own in-app billing for digital
    // products and rejects apps that send users to a bank gateway — see
    // core/billing/ and accounting/payment/myket.py on the server.
    flavorDimensions += listOf("env", "store")
    productFlavors {
        create("site") {
            dimension = "store"
        }
        create("bazaar") {
            dimension = "store"
            buildConfigField("String", "STORE", "\"bazaar\"")
        }
        create("myket") {
            dimension = "store"
            buildConfigField("String", "STORE", "\"myket\"")
            // The RSA key from Myket's panel (برنامه ← پرداخت درون‌برنامه‌ای).
            // Public by design (it only verifies Myket's signatures), but kept
            // out of the repo so it can change without a commit.
            val myketKey = (System.getenv("MYKET_IAB_PUBLIC_KEY")
                ?: project.findProperty("myketIabPublicKey") as String?).orEmpty()
            buildConfigField("String", "MYKET_IAB_PUBLIC_KEY", "\"$myketKey\"")
            // Required by myket-billing-client's merged manifest.
            val marketApplicationId = "ir.mservices.market"
            manifestPlaceholders["marketApplicationId"] = marketApplicationId
            manifestPlaceholders["marketBindAddress"] = "$marketApplicationId.InAppBillingService.BIND"
            manifestPlaceholders["marketPermission"] = "$marketApplicationId.BILLING"
        }
        create("prod") {
            dimension = "env"
        }
        create("local") {
            dimension = "env"
            applicationIdSuffix = ".local"
            versionNameSuffix = "-local"
            // 10.0.2.2 is the Android emulator's alias for the host machine's
            // loopback interface — this is where `python manage.py runserver`
            // listens per docs/ENVIRONMENTS.md. On a real device on the same
            // LAN, override with your machine's IP instead (adb won't route
            // 10.0.2.2).
            //
            // Defaults to production (2b1951b). To point it at a backend on this
            // machine without editing this file, pass the URL as a property:
            //   ./gradlew assembleLocalDebug -PlocalBaseUrl=http://127.0.0.1:8000
            // 127.0.0.1 works on any emulator (LDPlayer included, which has no
            // 10.0.2.2 alias) once `adb reverse tcp:8000 tcp:8000` is set up.
            val localBaseUrl = (project.findProperty("localBaseUrl") as String?)
                ?: "https://api.noobatyar.ir"
            val localBookingUrl = (project.findProperty("localBookingUrl") as String?)
                ?: "https://app.noobatyar.ir"
            buildConfigField("String", "BASE_URL", "\"$localBaseUrl\"")
            buildConfigField("String", "BOOKING_BASE_URL", "\"$localBookingUrl\"")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (System.getenv("ANDROID_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Two Android product flavors pick which server this build talks to (see
// docs/ENVIRONMENTS.md). "prod" is the safe default returned by defaultConfigs,
// so anything that isn't explicitly built as the "local" flavor (iOS included,
// which has no flavor concept) still points at the real server.
//
// Android does NOT read these — see AppConfig (core/utils/AppConfig.kt) and
// the per-flavor buildConfigField calls in the android{} block above.
// buildkonfig's own "flavor" is a Gradle project property
// (-Pbuildkonfig.flavor=local, per its FLAVOR_PROPERTY) that is completely
// unrelated to Android's product flavors declared above; nothing in this
// project ever set it, so a `defaultConfigs("local")` block here was
// unreachable dead configuration — every build, "local" Android flavor
// included, silently got the un-flavored `defaultConfigs { }` below (i.e.
// the production URL), which is how a "local" debug build talking straight
// to https://api.noobatyar.ir went unnoticed. This object still exists for
// iOS, which genuinely has no flavor concept and is meant to always use these
// (production) values.
buildkonfig {
    packageName = "xyz.sattar.javid.proqueue"
    defaultConfigs {
        buildConfigField(STRING, "BASE_URL", "https://api.noobatyar.ir")
        buildConfigField(STRING, "BOOKING_BASE_URL", "https://app.noobatyar.ir")
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
    
    // Chucker
    debugImplementation(libs.chucker.library)
    releaseImplementation(libs.chucker.library.no.op)

    // Myket in-app billing — only in the Myket build, so the site APK carries
    // none of its manifest entries (billing permission, receiver, queries).
    add("myketImplementation", "com.github.myketstore:myket-billing-client:1.19")

    // After running find ksp generated directories on: composeApp/build/generated/...
    add("kspAndroid", libs.androidx.room.compiler) // Android
    add("kspIosSimulatorArm64", libs.androidx.room.compiler) // Apple Silicon iOS Simulators
    add("kspIosArm64", libs.androidx.room.compiler) // Real iOS Devices
}

room {
    schemaDirectory("$projectDir/schemas")
}
