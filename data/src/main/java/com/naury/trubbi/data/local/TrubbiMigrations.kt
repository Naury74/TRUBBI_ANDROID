package com.naury.trubbi.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 마이그레이션 모음.
 *
 * 버전 6부터 스키마를 `app/schemas` 에 내보낸다. 그 이전 버전은 스키마 기록이 없어
 * 마이그레이션을 작성할 수 없고, [com.naury.trubbi.di.AppModule] 에서 해당 구간만
 * 파괴적 폴백을 허용한다.
 *
 * 버전을 올릴 때는 여기에 [Migration] 을 추가하고 [ALL] 에 등록한다. 등록을 빠뜨리면
 * 앱이 기존 DB 를 열 때 `IllegalStateException` 으로 즉시 실패한다.
 */
object TrubbiMigrations {

    /**
     * 지금 스키마 버전. [TrubbiDatabase] 의 @Database(version = ...) 가 이 값을 쓴다.
     *
     * 버전을 여기서만 올리면 마이그레이션·스키마 검증이 같은 값을 보게 된다.
     */
    const val CURRENT_VERSION = 7

    /**
     * 스키마를 `app/schemas` 에 내보내기 시작한 버전.
     *
     * 이 버전부터는 올릴 때마다 [Migration] 을 작성해 [ALL] 에 등록해야 한다.
     */
    const val FIRST_EXPORTED_VERSION = 6

    /**
     * 스키마 기록이 없어 재생성 외에 방법이 없는 구간.
     *
     * [androidx.room.RoomDatabase.Builder.fallbackToDestructiveMigrationFrom] 에 그대로 넘긴다.
     * 여기 없는 버전에서 올라올 때 마이그레이션이 빠져 있으면 Room 이 즉시 예외를 던진다.
     */
    val PRE_EXPORT_VERSIONS: IntArray = (1 until FIRST_EXPORTED_VERSION).toList().toIntArray()

    /**
     * 타임라인 이벤트 제목을 번역된 문자열이 아니라 종류 + 인자로 저장하도록 바꾼다.
     *
     * 그전에는 "상차지 도착" 같은 완성 문구를 저장 시점의 언어로 넣었다. 앱 언어를 영어로
     * 바꿔도 지난 기록은 한국어로 남는 문제가 있었다.
     *
     * 이미 저장된 행은 건드리지 않는다. 번역 전 원본이 없어 되살릴 수 없고, 표시할 때
     * `titleArg` 가 비어 있으면 저장된 문구를 그대로 쓴다. 새 행부터 언어를 따라간다.
     */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE journey_events ADD COLUMN titleArg TEXT")
            db.execSQL("ALTER TABLE journey_events ADD COLUMN titleAmount INTEGER")
        }
    }

    /**
     * [androidx.room.RoomDatabase.Builder.addMigrations] 에 넘길 전체 목록.
     *
     * [FIRST_EXPORTED_VERSION] 부터 현재 버전까지 끊김 없이 이어져야 한다.
     * 그 연결은 `TrubbiMigrationsTest` 가 검증한다 — 버전만 올리고 마이그레이션을 빠뜨리면
     * 기기에서 크래시로 발견하기 전에 테스트가 먼저 막는다.
     */
    val ALL: Array<Migration> = arrayOf(MIGRATION_6_7)
}
