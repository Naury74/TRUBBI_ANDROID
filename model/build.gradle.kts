plugins {
    alias(libs.plugins.android.library)
}

/**
 * 도메인 타입 — 열거형, 여정 모델, 메모 저장 형식.
 *
 * 가장 아래 모듈이라 다른 모듈에 의존하지 않는다. 표시 문구는 이름이 아니라 리소스 id 로 들고
 * 있어서(Labeled.labelRes), 언어 판단은 위 계층이 한다.
 */
android {
    // 네임스페이스를 코틀린 패키지와 맞춰 두면 이 모듈 안에서 R 을 import 없이 쓴다.
    namespace = "com.naury.trubbi.domain.model"
    compileSdk = 37

    defaultConfig { minSdk = 30 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.androidx.annotation)
}
