package com.naury.trubbi.di

import com.naury.trubbi.BuildConfig
import com.naury.trubbi.core.location.LocationKeys
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 앱 전역 싱글턴 — 데이터베이스, DAO, 화면과 무관하게 살아남아야 하는 코루틴 스코프.
 *
 * 리포지토리·매니저 클래스는 생성자 주입(@Inject)으로 충분해 여기에 두지 않는다.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** 장소 검색 키는 빌드 설정에서 읽어 위치 계층에 넣어 준다. 없으면 Geocoder 폴백으로 동작한다. */
    @Provides
    @Singleton
    fun provideLocationKeys(): LocationKeys = LocationKeys(BuildConfig.KAKAO_REST_KEY)

    /**
     * 화면이 사라져도 끝까지 돌아야 하는 작업용 스코프.
     *
     * 도착 직후 좌표를 채우는 일처럼, 사용자가 화면을 떠나도 완료돼야 하는 후속 작업에 쓴다.
     */
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
