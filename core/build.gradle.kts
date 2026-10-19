plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * 안드로이드 SDK 와 외부 서비스를 감싸는 층 — 위치, 음성, 촬영 파일, ML, 날씨, 로깅.
 *
 * 저장 계층(:data)이나 화면을 알지 못한다. 저장소가 필요로 하는 값은 인터페이스로 선언만 하고
 * (VoicePreferences 처럼) 구현은 위 모듈이 끼운다.
 */
android {
    namespace = "com.naury.trubbi.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
        // TrubbiLog 가 디버그/릴리스를 구분해 Logcat 과 Crashlytics 중 하나로 보낸다.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ML Kit — 바코드 인식, 한국어 OCR, 이미지 라벨링
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition.korean)
    implementation(libs.mlkit.image.labeling)

    // 바코드 생성
    implementation(libs.zxing.core)

    // Firebase — 크래시 리포트, 음성 인식·합성·의도 해석을 도는 Functions
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.functions)
    implementation(libs.coroutines.play.services)

    // 정확한 현재 위치
    implementation(libs.play.services.location)

    // ── 단위 테스트(JVM) ────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
}
