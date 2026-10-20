package com.naury.trubbi.core.voice

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * 클라우드 음성(받아쓰기·해석·합성)을 시도해도 되는 망인지.
 *
 * INTERNET 만 보면 로그인 전 공용 와이파이나 신호가 끊긴 셀에서도 온라인으로 판정해, 클라우드 호출이
 * 시간 초과될 때까지 기사가 기다리게 된다. 실제로 바깥과 통신이 확인된(VALIDATED) 망만 온라인으로 본다.
 */
internal fun Context.hasValidatedNetwork(): Boolean = runCatching {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching false
    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}.getOrDefault(false)
