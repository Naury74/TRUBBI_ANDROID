plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * 저장 계층 — Room 엔티티/DAO, 리포지토리, 설정(DataStore), 인증, 백업, 화면 간 신호.
 *
 * 화면을 알지 못하고 사용자에게 보여 줄 문구도 만들지 않는다. 저장이 끝난 뒤 바깥 표면을
 * 갱신해야 하면 인터페이스로 선언만 하고(DataChangeNotifier) 구현은 앱이 끼운다.
 */
android {
    namespace = "com.naury.trubbi.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
        // 스키마를 data/schemas 에 남긴다. 버전 간 diff 로 마이그레이션 누락을 잡는다.
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Robolectric 이 매니페스트·리소스를 읽어야 한다.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":model"))
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    // 백그라운드 백업 · 재시도
    implementation(libs.androidx.work.runtime)

    // Firebase — 로그인, 클라우드 백업, 서버리스 함수
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.functions)
    implementation(libs.coroutines.play.services)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)

    // ── 단위 테스트(JVM) ────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.runtime)
}
