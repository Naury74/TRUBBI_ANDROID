package com.naury.trubbi.navigation

import android.net.Uri

/**
 * 화면 경로 문자열 모음.
 *
 * 인자가 붙는 경로는 아래 함수들로만 만든다. 호출부에서 문자열을 직접 이어 붙이면
 * 인코딩을 빠뜨려 uri 가 든 인자에서 깨진다.
 */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"

    const val MEMO = "memo?recordId={recordId}"
    fun memo(recordId: String? = null) = if (recordId == null) "memo" else "memo?recordId=$recordId"
    const val CONTACTS = "contacts"
    const val PURPOSES = "purposes"

    const val CAMERA = "camera?purpose={purpose}&mode={mode}"
    fun camera(purpose: String = "ETC", mode: String = "photo") = "camera?purpose=$purpose&mode=$mode"

    const val BARCODE = "barcode?purpose={purpose}"
    fun barcode(purpose: String = "WAYBILL") = "barcode?purpose=$purpose"

    const val DETAIL = "detail/{recordId}"
    fun detail(recordId: String) = "detail/$recordId"

    const val JOURNEY = "journey/{journeyId}"
    fun journey(journeyId: String) = "journey/$journeyId"

    const val JOURNEYS = "journeys"

    const val JOURNEY_MAP = "journeyMap/{journeyId}"
    fun journeyMap(journeyId: String) = "journeyMap/$journeyId"

    const val ACCOUNT = "account"

    const val EDITOR = "editor/{recordId}/{mediaUri}"
    fun editor(recordId: String, mediaUri: String) =
        "editor/$recordId/${Uri.encode(mediaUri)}"

    const val VIDEO_EDITOR = "videoEditor/{recordId}/{mediaUri}"
    fun videoEditor(recordId: String, mediaUri: String) =
        "videoEditor/$recordId/${Uri.encode(mediaUri)}"

    val topLevel = setOf(HOME, SETTINGS)
}
