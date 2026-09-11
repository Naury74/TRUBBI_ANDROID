package com.naury.trubbi.widget

import com.naury.trubbi.data.local.TrubbiDao
import com.naury.trubbi.data.repo.JourneyRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 위젯 프로바이더가 Hilt 그래프에 닿기 위한 진입점.
 *
 * AppWidgetProvider 는 시스템이 만들어 주는 BroadcastReceiver 라 생성자 주입을 쓸 수 없다.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun trubbiDao(): TrubbiDao
    fun journeyRepository(): JourneyRepository
}
