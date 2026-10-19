import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)
}

// 장소 검색·지오코딩 키는 git 에 올리지 않고 local.properties 에서 읽는다.
//   KAKAO_REST_KEY / KAKAO_NATIVE_KEY
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val kakaoRestKey: String = (localProps.getProperty("KAKAO_REST_KEY") ?: "").trim()
val kakaoNativeKey: String = (localProps.getProperty("KAKAO_NATIVE_KEY") ?: "").trim()

// 릴리스 서명 정보. local.properties 우선, 없으면 환경 변수(CI)에서 읽는다.
// 넷 중 하나라도 비면 서명 설정을 만들지 않는다 — 키 없이도 assembleRelease 가 돌아야 한다.
fun secret(key: String): String? =
    (localProps.getProperty(key) ?: System.getenv(key))?.trim()?.takeIf { it.isNotEmpty() }

val keystoreFile = secret("TRUBBI_KEYSTORE")?.let { rootProject.file(it) }?.takeIf { it.exists() }
val keystorePassword = secret("TRUBBI_KEYSTORE_PASSWORD")
val keyAliasName = secret("TRUBBI_KEY_ALIAS")
val keyPasswordValue = secret("TRUBBI_KEY_PASSWORD")
val hasReleaseSigning =
    keystoreFile != null && keystorePassword != null && keyAliasName != null && keyPasswordValue != null

android {
    namespace = "com.naury.trubbi"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.naury.trubbi"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 미설정이면 빈 문자열 — 앱은 Geocoder 폴백으로 계속 동작한다.
        buildConfigField("String", "KAKAO_REST_KEY", "\"$kakaoRestKey\"")
        buildConfigField("String", "KAKAO_NATIVE_KEY", "\"$kakaoNativeKey\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 키가 없는 환경(로컬 클론·CI 기본)에서는 서명 없이 빌드만 되도록 둔다.
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Robolectric 이 매니페스트/리소스를 읽어야 하므로 리소스를 포함시킨다.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    // ML Kit·카카오맵 네이티브 라이브러리가 ABI 당 40MB 를 넘어, 네 ABI 를 한 APK 에 담으면
    // 164MB 가 된다. 정식 배포는 AAB(bundleRelease)로 하고 Play 가 기기별로 쪼개 내려보낸다.
    //
    // APK 를 직접 배포해야 할 때만 `-Ptrubbi.abiSplits=true` 로 ABI 분할을 켠다.
    // AGP 는 분할과 번들을 동시에 만들 수 없어 기본값은 꺼 둔다.
    splits {
        abi {
            isEnable = providers.gradleProperty("trubbi.abiSplits").orNull.toBoolean()
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    bundle {
        // Play 가 기기별로 필요한 것만 내려보낸다.
        abi { enableSplit = true }
        density { enableSplit = true }
        language { enableSplit = false } // 앱 내 언어 전환을 지원하므로 묶어서 배포한다.
    }

    packaging {
        resources.excludes += setOf("META-INF/LICENSE.md", "META-INF/LICENSE-notice.md")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
}

dependencies {
    // ── 모듈 ────────────────────────────────────────────────────────────────
    implementation(project(":model"))
    implementation(project(":core"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)

    // ML Kit barcode
    implementation(libs.mlkit.barcode.scanning)
    // ML Kit OCR (Korean) + on-device image labeling
    implementation(libs.mlkit.text.recognition.korean)
    implementation(libs.mlkit.image.labeling)
    // ML Kit 문서 스캐너 (촬영·영역조정·보정·PDF 생성)
    implementation(libs.mlkit.document.scanner)

    // Image loading (+ video frame decoding so any video uri renders a poster frame)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.network.okhttp)

    // Barcode generation (encode value back into a scannable barcode)
    implementation(libs.zxing.core)

    // Lucide icon set (app-wide icons via the AppIcons facade)
    implementation(libs.lucide.icons)

    // Real backdrop blur ("Liquid Glass" frosted surfaces)
    implementation(libs.haze)
    implementation(libs.haze.blur)

    // Kakao Maps SDK — journey route/stop/record map + place search
    implementation(libs.kakao.maps)

    // Lottie — premium animated weather backgrounds (loaded from assets, falls back to the
    // built-in illustrated background when an animation file is missing)
    implementation(libs.lottie.compose)

    // Real rich-text memo editing: selection-based styling + standard HTML round-trip
    implementation(libs.richeditor.compose)

    // Media3 — video preview (ExoPlayer/PlayerView) + editing/export (Transformer + effects)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.common)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Firebase — serverless auth + cloud backup (optional; app fully works offline without it)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.functions)
    implementation(libs.coroutines.play.services)
    // Fused location (fast, accurate current-location fixes)
    implementation(libs.play.services.location)
    // Modern Google sign-in via Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)
    // 백그라운드 백업 · 재시도
    implementation(libs.androidx.work.runtime)

    // baseline-prof.txt 를 기기에 설치한다. 없으면 프로필이 APK 에만 들어가고 적용되지 않는다.
    implementation(libs.androidx.profileinstaller)

    // ── 단위 테스트(JVM) ────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.runtime)

    // @Preview 렌더링용(디버그 빌드에만).
    debugImplementation(libs.androidx.compose.ui.tooling)
}
