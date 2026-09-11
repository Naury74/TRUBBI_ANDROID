// 루트 빌드 파일 — 플러그인 버전을 여기서 못 박고, 모듈 공통 설정만 둔다.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.crashlytics) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

// 정적 분석은 모든 모듈에 같은 규칙으로 건다. 모듈이 늘어날 때마다 설정을 복사하지 않기 위해
// 여기 한 번만 적는다. 규칙 자체는 config/detekt.yml 과 .editorconfig 에 있다.
subprojects {
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(files("$rootDir/config/detekt.yml"))
        baseline = file("$rootDir/config/detekt-baseline.xml")
        parallel = true
    }

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.3.1")
        android.set(true)
        ignoreFailures.set(false)
        filter {
            // 생성 코드는 우리가 고칠 수 없다.
            exclude { it.file.path.contains("/build/") }
        }
    }
}
