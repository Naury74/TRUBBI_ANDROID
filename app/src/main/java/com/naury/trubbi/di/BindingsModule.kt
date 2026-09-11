package com.naury.trubbi.di

import com.naury.trubbi.core.voice.VoicePreferences
import com.naury.trubbi.data.DataChangeNotifier
import com.naury.trubbi.data.settings.SettingsRepository
import com.naury.trubbi.widget.WidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 인터페이스와 구현을 잇는다.
 *
 * 여기 모인 것들은 데이터 계층이 필요로 하는 동작을 선언만 하고, 실제 수행은 바깥 계층이
 * 맡는 경우다. 구현을 바꿔 끼우는 지점이 이 파일 하나로 모인다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    /** 데이터 변경 알림은 바탕화면 위젯 갱신으로 옮긴다. */
    @Binds
    @Singleton
    abstract fun bindDataChangeNotifier(impl: WidgetUpdater): DataChangeNotifier

    /** 음성 출력이 읽는 설정은 설정 저장소가 답한다. */
    @Binds
    @Singleton
    abstract fun bindVoicePreferences(impl: SettingsRepository): VoicePreferences
}
