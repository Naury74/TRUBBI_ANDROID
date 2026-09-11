package com.naury.trubbi.core.log

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.naury.trubbi.core.BuildConfig

/**
 * 앱 전역 로깅.
 *
 * `runCatching` 으로 삼킨 실패가 아무 흔적도 남기지 않으면, 사용자가 "저장이 안 된다" 고 해도
 * 어느 단계에서 끊겼는지 확인할 방법이 없다. 삼키더라도 기록은 남긴다.
 *
 * 디버그 빌드는 Logcat 에만 쓰고, 릴리스 빌드는 Crashlytics 로도 보낸다.
 * 사진·주소·연락처 같은 사용자 데이터는 넘기지 않는다 — 메시지에는 어느 동작이 실패했는지와
 * 예외 종류만 담는다.
 */
object TrubbiLog {

    /**
     * 예상 가능한 실패를 기록한다. 흐름은 계속 진행된다.
     *
     * @param tag 실패한 영역. 클래스명이나 기능명을 쓴다
     * @param message 무엇을 하려다 실패했는지. 사용자 데이터를 넣지 않는다
     * @param error 원인 예외. 없으면 생략한다
     */
    fun warn(tag: String, message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, message, error)
            return
        }
        runCatching {
            FirebaseCrashlytics.getInstance().log("[$tag] $message")
            if (error != null) FirebaseCrashlytics.getInstance().recordException(error)
        }
    }

    /**
     * 기능이 동작하지 않는 수준의 실패를 기록한다.
     *
     * @param tag 실패한 영역
     * @param message 무엇을 하려다 실패했는지
     * @param error 원인 예외
     */
    fun error(tag: String, message: String, error: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, message, error)
            return
        }
        runCatching {
            FirebaseCrashlytics.getInstance().log("[$tag] $message")
            FirebaseCrashlytics.getInstance().recordException(error)
        }
    }

    /**
     * 크래시 리포트에 함께 붙일 상태 값을 남긴다.
     *
     * 크래시 시점의 화면·모드처럼, 재현 조건을 좁히는 데 쓰는 값만 넣는다.
     *
     * @param key 항목 이름
     * @param value 값. 사용자 데이터를 넣지 않는다
     */
    fun setContext(key: String, value: String) {
        if (BuildConfig.DEBUG) return
        runCatching { FirebaseCrashlytics.getInstance().setCustomKey(key, value) }
    }
}

/**
 * [runCatching] 의 실패를 기록하고 그대로 흘려보낸다.
 *
 * ```
 * runCatching { mediaStorage.saveVideo(file, purpose) }
 *     .logFailure("MediaStorage", "영상 저장 실패")
 *     .getOrNull()
 * ```
 *
 * @param tag 실패한 영역
 * @param message 무엇을 하려다 실패했는지
 */
fun <T> Result<T>.logFailure(tag: String, message: String): Result<T> =
    onFailure { TrubbiLog.warn(tag, message, it) }
