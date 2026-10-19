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
    alias(libs.plugins.ktlint) apply false
}

/** detekt CLI 가 빌드된 Kotlin 버전. detekt 를 올릴 때 함께 맞춘다. */
val DETEKT_KOTLIN_VERSION = "2.0.21"

// 정적 분석은 모든 모듈에 같은 규칙으로 건다. 모듈이 늘어날 때마다 설정을 복사하지 않기 위해
// 여기 한 번만 적는다. 규칙 자체는 config/detekt.yml 과 .editorconfig 에 있다.
subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    // detekt 는 Gradle 플러그인 대신 CLI 로 돌린다. 1.23 플러그인은 AGP 9 의 내장 Kotlin 을 알아보지 못해
    // detektDebug 같은 작업을 만들지 않고, 자기가 빌드된 Kotlin(2.0.21) 컴파일러로만 동작한다.
    val detektCli = configurations.create("detekt") {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(DETEKT_KOTLIN_VERSION)
        }
    }
    dependencies.add(detektCli.name, rootProject.libs.detekt.cli)
    val detektReports = layout.buildDirectory.dir("reports/detekt")
    tasks.register<JavaExec>("detekt") {
        group = "verification"
        description = "detekt 로 Kotlin 코드 복잡도와 위험 패턴을 검사한다."
        classpath = detektCli
        mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
        val sources = fileTree("src") { include("**/*.kt") }
        onlyIf { !sources.isEmpty }
        val baseline = rootProject.file("config/detekt-baseline.xml")
        inputs.files(sources, "$rootDir/config/detekt.yml")
        outputs.dir(detektReports)
        args(
            "--input", file("src").absolutePath,
            "--config", "$rootDir/config/detekt.yml",
            "--build-upon-default-config",
            "--parallel",
            "--report", "html:${detektReports.get().file("detekt.html").asFile}",
            "--report", "txt:${detektReports.get().file("detekt.txt").asFile}",
        )
        // 기준선 파일은 있을 때만 쓴다(./gradlew detekt -- --create-baseline 으로 만들 수 있다).
        if (baseline.exists()) args("--baseline", baseline.absolutePath)
    }
    tasks.matching { it.name == "check" }.configureEach { dependsOn("detekt") }

    // Robolectric 은 JDK 내부 API 로 FileDescriptor 를 다루는데, JDK 17+ 는 이를 기본으로 열어 두지 않는다.
    // (JDK 25 에서는 이게 없으면 IllegalAccessException 으로 테스트가 깨진다.)
    tasks.withType<Test>().configureEach {
        jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--enable-native-access=ALL-UNNAMED")
    }

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(rootProject.libs.versions.ktlint.get())
        android.set(true)
        ignoreFailures.set(false)
        filter {
            // 생성 코드는 우리가 고칠 수 없다.
            exclude { it.file.path.contains("/build/") }
        }
    }
}
