package com.naury.trubbi.core.location

/**
 * 장소 검색·역지오코딩에 쓰는 외부 서비스 키.
 *
 * 값 자체는 앱 모듈이 빌드 설정에서 읽어 넣는다. 위치 계층이 앱의 BuildConfig 를 직접 보면
 * 앱 없이는 쓸 수도 테스트할 수도 없게 된다.
 *
 * @property kakaoRest Kakao Local REST 키. 비어 있으면 안드로이드 기본 Geocoder 로 내려간다
 */
data class LocationKeys(val kakaoRest: String)
