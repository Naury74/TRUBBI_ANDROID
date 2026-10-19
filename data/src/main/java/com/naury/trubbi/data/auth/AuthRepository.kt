package com.naury.trubbi.data.auth

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await

/**
 * 로그인한 사용자. 화면이 실제로 쓰는 값만 담는다.
 *
 * 기본 상태는 null 이다. 로그인은 클라우드 백업 기능에만 필요하다.
 *
 * @property uid Firebase 계정 식별자
 * @property email 로그인 이메일. 소셜 계정은 없을 수 있다
 * @property displayName 표시 이름
 * @property photoUrl 프로필 사진 URL
 * @property providers 연결된 로그인 수단(`password`, `google.com` …)
 * @property emailVerified 이메일 인증을 마쳤는지
 */
data class TrubbiUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val providers: List<String>,
    val emailVerified: Boolean,
)

/**
 * 로그인 이메일 단계에서 미리 보여 줄 계정 정보.
 *
 * 비밀번호 입력 전에 계정 존재 여부와 표시 정보를 확인할 수 있다.
 *
 * @property exists 해당 이메일로 만들어진 계정이 있는지
 * @property displayName 있으면 표시 이름
 * @property photoUrl 있으면 프로필 사진 URL
 */
data class AccountLookup(
    val exists: Boolean,
    val displayName: String?,
    val photoUrl: String?,
)

/**
 * Firebase 인증을 감싼 리포지토리.
 *
 * 로그인은 클라우드 백업에만 필요하며, 나머지 기능은 로그아웃·오프라인 상태에서도 동작한다.
 *
 * 네트워크를 사용하는 함수는 예외를 던지지 않고 [Result] 를 반환한다.
 * `users` 문서 갱신 같은 부수적 쓰기는 실패해도 주 흐름을 중단시키지 않는다.
 *
 * @property context `google-services` 가 심어 둔 리소스를 읽기 위한 컨텍스트
 * @property auth Firebase 인증 클라이언트
 * @property firestore 프로필·동의 이력을 두는 `users` 컬렉션
 * @property storage 프로필 사진 저장소
 * @property strings 오류 문구를 현재 언어로 만들기 위한 리소스 접근자
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: com.google.firebase.storage.FirebaseStorage,
    private val strings: StringProvider,
) {
    companion object {
        /** 동의 이력에 함께 적는 약관 버전. 약관을 고치면 올려서 재동의를 받는다. */
        const val TERMS_VERSION = "2026.06.09"
        const val PRIVACY_VERSION = "2026.06.09"
        const val LOCATION_TERMS_VERSION = "2026.06.09"
    }

    // 메일 링크 인증은 `AuthStateListener` 를 트리거하지 않는다.
    // `reload()` 이후 이 값을 증가시켜 [currentUser] 를 한 번 더 방출시킨다.
    private val refresh = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** 현재 사용자(없으면 null). 인증 상태가 바뀔 때와 [reloadVerification] 뒤에 다시 흐른다. */
    val currentUser: Flow<TrubbiUser?> =
        callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { trySend(Unit) }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }.combine(refresh) { _, _ -> auth.currentUser?.toTrubbiUser() }

    /** 로그인 상태인지. 이메일 인증 완료 여부는 보지 않는다. */
    fun isLoggedIn(): Boolean = auth.currentUser != null

    /**
     * 이메일 인증을 아직 마치지 않아 막아야 하는 사용자인지.
     *
     * 소셜 로그인 계정은 제공자가 이미 검증한 메일이므로 대상이 아니다.
     *
     * @param user 검사할 사용자
     */
    fun requiresVerification(user: TrubbiUser): Boolean =
        user.providers.any { it.contains("password") } && !user.emailVerified

    /** 첫 프레임에서 쓰는 동기 스냅숏. Flow 방출을 기다리면 화면이 한 번 전환됐다 돌아온다. */
    fun currentUserNow(): TrubbiUser? = auth.currentUser?.toTrubbiUser()

    /** 방금 만든 계정으로 인증 메일을 보낸다. */
    suspend fun sendEmailVerification(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error(strings[R.string.auth_not_signed_in])
        user.sendEmailVerification().await()
    }

    /**
     * 서버에서 계정 상태를 다시 읽고 [currentUser] 를 갱신한다.
     *
     * @return 이제 인증이 끝났으면 true
     */
    suspend fun reloadVerification(): Boolean {
        val user = auth.currentUser ?: return false
        runCatching { user.reload().await() }
        refresh.value += 1
        return auth.currentUser?.isEmailVerified == true
    }

    /** 구글 로그인을 켜면 `google-services` 가 심어 주는 웹 클라이언트 id. 없으면 null. */
    val googleWebClientId: String? by lazy {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) context.getString(resId) else null
    }

    /**
     * 이메일·비밀번호로 계정을 만든다.
     *
     * @param email 가입 이메일
     * @param password 비밀번호
     * @param name 표시 이름. 비우면 설정하지 않는다
     * @param marketingConsent 마케팅 수신 동의 여부. 동의 이력에 함께 기록한다
     */
    suspend fun signUpEmail(email: String, password: String, name: String?, marketingConsent: Boolean = false): Result<Unit> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        if (!name.isNullOrBlank()) {
            runCatching {
                result.user?.updateProfile(
                    com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build(),
                )?.await()
            }
        }
        // 계정 생성은 이미 끝났다. 인증 메일 발송과 Firestore 쓰기는 네트워크 상태에 따라
        // 오래 걸릴 수 있어 타임아웃을 건다. 인증 메일은 재발송할 수 있다.
        kotlinx.coroutines.withTimeoutOrNull(7000) {
            runCatching { result.user?.sendEmailVerification()?.await() }
            upsertUserDoc(name)
            runCatching {
                result.user?.uid?.let { uid ->
                    firestore.collection("users").document(uid).set(
                        mapOf(
                            "termsVersion" to TERMS_VERSION,
                            "privacyVersion" to PRIVACY_VERSION,
                            "locationTermsVersion" to LOCATION_TERMS_VERSION,
                            "over14" to true,
                            "marketingConsent" to marketingConsent,
                            "agreedAt" to System.currentTimeMillis(),
                        ),
                        com.google.firebase.firestore.SetOptions.merge(),
                    ).await()
                }
            }
        }
    }

    /** 이메일·비밀번호로 로그인한다. */
    suspend fun signInEmail(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        upsertUserDoc(null)
    }

    /** 로그인 후 약관 동의 게이트에서 동의를 users 문서에 기록(소셜/기기변경 등). */
    suspend fun recordConsent(marketingConsent: Boolean): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error(strings[R.string.auth_sign_in_required])
        firestore.collection("users").document(user.uid).set(
            mapOf(
                "termsVersion" to TERMS_VERSION,
                "privacyVersion" to PRIVACY_VERSION,
                "locationTermsVersion" to LOCATION_TERMS_VERSION,
                "over14" to true,
                "marketingConsent" to marketingConsent,
                "agreedAt" to System.currentTimeMillis(),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }

    /** 이름(표시 이름)을 Firebase 계정 + users 문서에 저장하고 currentUser를 다시 흘려보낸다. */
    suspend fun updateDisplayName(name: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error(strings[R.string.auth_sign_in_required])
        val n = name.trim()
        user.updateProfile(
            com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(n).build(),
        ).await()
        runCatching {
            firestore.collection("users").document(user.uid)
                .set(
                    mapOf("displayName" to n, "updatedAt" to System.currentTimeMillis()),
                    com.google.firebase.firestore.SetOptions.merge(),
                ).await()
        }
        refresh.value += 1
    }

    /** 프로필 사진을 Storage에 올리고 photoUrl을 계정 + users 문서에 동기화. 반환=다운로드 URL. */
    suspend fun updatePhoto(localUri: android.net.Uri): Result<String> = runCatching {
        val user = auth.currentUser ?: error(strings[R.string.auth_sign_in_required])
        val ref = storage.reference.child("users/${user.uid}/profile.jpg")
        ref.putFile(localUri).await()
        val url = ref.downloadUrl.await().toString()
        user.updateProfile(
            com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setPhotoUri(android.net.Uri.parse(url)).build(),
        ).await()
        runCatching {
            firestore.collection("users").document(user.uid)
                .set(
                    mapOf("photoUrl" to url, "updatedAt" to System.currentTimeMillis()),
                    com.google.firebase.firestore.SetOptions.merge(),
                ).await()
        }
        refresh.value += 1
        url
    }

    /**
     * 로그인 화면에서 이메일만으로 계정을 미리 조회한다.
     *
     * @param email 조회할 이메일
     * @return 계정 존재 여부와 표시 이름·사진
     */
    suspend fun lookupAccount(email: String): Result<AccountLookup> = runCatching {
        val res = com.google.firebase.functions.FirebaseFunctions.getInstance()
            .getHttpsCallable("checkEmail").call(mapOf("email" to email.trim())).await()

        @Suppress("UNCHECKED_CAST")
        val data = res.data as? Map<String, Any?>
        AccountLookup(
            exists = (data?.get("exists") as? Boolean) ?: false,
            displayName = data?.get("displayName") as? String,
            photoUrl = data?.get("photoUrl") as? String,
        )
    }

    /**
     * 이미 가입된 이메일인지. 회원가입 단계에서 바로 알려 주기 위한 확인.
     *
     * `checkEmail` Cloud Function 을 우선 호출한다. 클라이언트 `fetchSignInMethodsForEmail` 은
     * 계정 열거 방지가 켜져 있으면 항상 빈 결과를 반환하므로 함수 실패 시의 대체 경로로만 쓴다.
     *
     * @param email 확인할 이메일
     */
    @Suppress("DEPRECATION")
    suspend fun emailRegistered(email: String): Result<Boolean> = runCatching {
        val e = email.trim()
        val viaFn = runCatching {
            val res = com.google.firebase.functions.FirebaseFunctions.getInstance()
                .getHttpsCallable("checkEmail").call(mapOf("email" to e)).await()
            @Suppress("UNCHECKED_CAST")
            (res.data as? Map<String, Any?>)?.get("exists") as? Boolean
        }.getOrNull()
        viaFn ?: !auth.fetchSignInMethodsForEmail(e).await().signInMethods.isNullOrEmpty()
    }

    /**
     * 구글 로그인. Credential Manager 가 받아 온 ID 토큰으로 Firebase 세션을 연다.
     *
     * @param idToken Credential Manager 가 돌려준 구글 ID 토큰
     */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
        upsertUserDoc(null)
    }

    /**
     * 카카오·네이버용. Cloud Functions 가 발급한 커스텀 토큰으로 로그인한다.
     *
     * @param token 서버가 만든 Firebase 커스텀 토큰
     */
    suspend fun signInWithCustomToken(token: String): Result<Unit> = runCatching {
        auth.signInWithCustomToken(token).await()
        upsertUserDoc(null)
    }

    /** 비밀번호 재설정 메일을 보낸다. */
    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /** 로그아웃한다. 기기에 저장된 기록은 삭제하지 않는다. */
    fun signOut() = auth.signOut()

    /** 프로필을 탈퇴 상태로 표시한 뒤 계정을 삭제한다. 기기에 저장된 기록은 삭제하지 않는다. */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error(strings[R.string.auth_not_signed_in])
        // 탈퇴 표시를 먼저 기록한다. 이 쓰기가 실패해도 계정 삭제는 진행한다.
        runCatching {
            firestore.collection("users").document(user.uid)
                .set(
                    mapOf("status" to "withdrawn", "updatedAt" to System.currentTimeMillis()),
                    com.google.firebase.firestore.SetOptions.merge(),
                ).await()
        }
        user.delete().await()
    }

    /** `users/{uid}` 문서를 갱신한다. 실패는 무시하며 로그인 결과에 영향을 주지 않는다. */
    private suspend fun upsertUserDoc(name: String?) {
        val user = auth.currentUser ?: return
        runCatching {
            val now = System.currentTimeMillis()
            val data = mutableMapOf<String, Any?>(
                "uid" to user.uid,
                "email" to user.email,
                "displayName" to (name?.takeIf { it.isNotBlank() } ?: user.displayName),
                "photoUrl" to user.photoUrl?.toString(),
                "providers" to user.providerData.map { it.providerId },
                "status" to "active",
                "updatedAt" to now,
                "lastLoginAt" to now,
            )
            firestore.collection("users").document(user.uid)
                .set(data, com.google.firebase.firestore.SetOptions.merge()).await()
        }
    }

    private fun com.google.firebase.auth.FirebaseUser.toTrubbiUser() = TrubbiUser(
        uid = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString(),
        providers = providerData.map { it.providerId }.filter { it != "firebase" },
        emailVerified = isEmailVerified,
    )
}
