package com.naury.trubbi.data.di

import android.content.Context
import androidx.room.Room
import com.naury.trubbi.data.local.JourneyDao
import com.naury.trubbi.data.local.TrubbiDao
import com.naury.trubbi.data.local.TrubbiDatabase
import com.naury.trubbi.data.local.TrubbiMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 데이터베이스와 DAO 를 만든다.
 *
 * Room 을 쓰는 모듈이 그 설정도 함께 들고 있는다. 앱 모듈이 데이터베이스를 조립하면
 * 마이그레이션 정책이 저장 계층 밖에 놓여, 스키마를 바꾼 사람이 못 보고 지나치기 쉽다.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TrubbiDatabase =
        Room.databaseBuilder(context, TrubbiDatabase::class.java, "trubbi.db")
            // 스키마를 내보내기 시작한 버전 이전에는 마이그레이션 근거가 없어 재생성만 가능하다.
            // 그 범위만 파괴적 폴백을 열어 두고, 그 뒤 버전 업은 반드시 Migration 을 추가해야 한다
            // (누락 시 IllegalStateException 으로 즉시 드러난다).
            .fallbackToDestructiveMigrationFrom(dropAllTables = false, *TrubbiMigrations.PRE_EXPORT_VERSIONS)
            .addMigrations(*TrubbiMigrations.ALL)
            .build()

    @Provides
    fun provideDao(db: TrubbiDatabase): TrubbiDao = db.trubbiDao()

    @Provides
    fun provideJourneyDao(db: TrubbiDatabase): JourneyDao = db.journeyDao()
}
