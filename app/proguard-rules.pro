# R8 규칙.
#
# 기본 규칙(proguard-android-optimize.txt)에 더해, 리플렉션·네이티브 호출로 접근되는 것만
# 여기에 남긴다. 라이브러리가 자체 consumer rules 를 제공하면 여기 적지 않는다
# (Hilt·Room·Coil·Navigation 은 AAR 에 규칙이 들어 있다).

# 크래시 리포트에서 원래 위치를 읽을 수 있도록 남긴다.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 코루틴·리플렉션이 참조하는 시그니처.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*

# ── Firebase / Firestore ────────────────────────────────────────────────────
# Firestore 는 문서를 POJO 로 역직렬화할 때 필드명을 리플렉션으로 읽는다.
-keepclassmembers class com.naury.trubbi.** {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}
-keepnames class com.google.firebase.firestore.** { *; }

# Cloud Functions 응답은 Map<String, Any?> 로만 받으므로 별도 규칙이 필요 없다.

# ── Kakao Maps SDK ──────────────────────────────────────────────────────────
# 네이티브 레이어에서 JNI 로 콜백을 호출한다.
-keep class com.kakao.vectormap.** { *; }
-dontwarn com.kakao.vectormap.**

# ── ML Kit ──────────────────────────────────────────────────────────────────
# 모델 로딩이 클래스명을 문자열로 찾는다.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**

# ── Media3 (Transformer / ExoPlayer) ────────────────────────────────────────
# 디코더·이펙트가 리플렉션으로 선택된다.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── ZXing ───────────────────────────────────────────────────────────────────
-dontwarn com.google.zxing.**

# ── Lottie ──────────────────────────────────────────────────────────────────
-dontwarn com.airbnb.lottie.**

# ── 앱 자체 ─────────────────────────────────────────────────────────────────
# 위젯·서비스·리시버는 매니페스트에서 이름으로 참조된다(기본 규칙이 커버하지만 명시).
-keep class com.naury.trubbi.widget.** extends android.appwidget.AppWidgetProvider
-keep class com.naury.trubbi.domain.tracking.JourneyTrackingService

# 백업 JSON 의 키는 문자열 리터럴이라 난독화 대상이 아니다. 별도 규칙 불필요.
