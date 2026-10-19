package com.naury.trubbi

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.kakao.vectormap.KakaoMapSdk
import dagger.hilt.android.HiltAndroidApp

/**
 * 애플리케이션 진입점. Hilt 컴포넌트와 지도 SDK, 이미지 로더를 여기서 세운다.
 */
@HiltAndroidApp
class TrubbiApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // MapView 가 하나라도 뜨기 전에 네이티브 키가 들어가 있어야 한다.
        val key = BuildConfig.KAKAO_NATIVE_KEY
        if (key.isNotBlank()) runCatching { KakaoMapSdk.init(this, key) }
    }

    // 영상 프레임 디코더를 끼워, 썸네일이 없는 영상도 AsyncImage 가 첫 프레임을 그려 낸다.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
