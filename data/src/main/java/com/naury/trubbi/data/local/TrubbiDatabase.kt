package com.naury.trubbi.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * 앱의 유일한 로컬 데이터베이스.
 *
 * 기록·연락처 쪽은 [TrubbiDao], 여정 쪽은 [JourneyDao] 로 나눠 둔다. 한 DAO 에 40개 넘는
 * 쿼리가 몰리는 것보다 화면 단위로 찾아가기가 쉽다.
 */
@Database(
    entities = [
        RecordEntity::class, MediaEntity::class, ContactEntity::class,
        JourneyEntity::class, JourneyStopEntity::class, RoutePointEntity::class,
        ExpenseEntity::class, IssueEntity::class, JourneyEventEntity::class,
        FavoritePlaceEntity::class,
    ],
    version = TrubbiMigrations.CURRENT_VERSION,
    // 스키마를 app/schemas 에 남긴다. 버전 간 diff 로 마이그레이션 누락을 확인한다.
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TrubbiDatabase : RoomDatabase() {
    abstract fun trubbiDao(): TrubbiDao
    abstract fun journeyDao(): JourneyDao
}
