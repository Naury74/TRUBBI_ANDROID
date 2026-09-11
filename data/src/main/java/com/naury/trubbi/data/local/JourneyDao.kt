package com.naury.trubbi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 여정과 그에 딸린 정차지·경로·비용·이슈·타임라인을 다루는 DAO.
 *
 * 화면은 `observe…` 를 쓰고, `…Once` 는 리포트 생성·서비스처럼 구독 해지 지점이 없는
 * 일회성 호출에서만 쓴다.
 */
@Dao
interface JourneyDao {

    // ── 여정
    @Upsert
    suspend fun upsertJourney(journey: JourneyEntity)

    @Transaction
    @Query("SELECT * FROM journeys WHERE completedAt IS NULL AND isCanceled = 0 ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveJourney(): Flow<JourneyWithStops?>

    @Transaction
    @Query("SELECT * FROM journeys WHERE journeyId = :id LIMIT 1")
    fun observeJourney(id: String): Flow<JourneyWithStops?>

    @Transaction
    @Query("SELECT * FROM journeys WHERE isCanceled = 0 ORDER BY startedAt DESC")
    fun observeJourneys(): Flow<List<JourneyWithStops>>

    @Query("SELECT * FROM journeys WHERE journeyId = :id LIMIT 1")
    suspend fun journeyOnce(id: String): JourneyEntity?

    @Transaction
    @Query("SELECT * FROM journeys WHERE completedAt IS NULL AND isCanceled = 0 ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeJourneyOnce(): JourneyWithStops?

    @Query("UPDATE journeys SET completedAt = :at WHERE journeyId = :id")
    suspend fun completeJourney(id: String, at: Long)

    @Query("UPDATE journeys SET isCanceled = 1 WHERE journeyId = :id")
    suspend fun cancelJourney(id: String)

    @Query("UPDATE journeys SET totalDistanceM = totalDistanceM + :deltaM WHERE journeyId = :id")
    suspend fun addDistance(id: String, deltaM: Long)

    // ── 장소 즐겨찾기
    @Query("SELECT * FROM favorite_places ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<FavoritePlaceEntity>>

    @Query("SELECT * FROM favorite_places ORDER BY createdAt DESC")
    suspend fun favoritesOnce(): List<FavoritePlaceEntity>

    @Upsert
    suspend fun upsertFavorite(place: FavoritePlaceEntity)

    @Query("DELETE FROM favorite_places WHERE id = :id")
    suspend fun deleteFavorite(id: String)

    /** 이름과 반올림한 좌표로 조회한다. 지도에서 해제할 때 즐겨찾기 id 를 알 수 없다. */
    @Query(
        "SELECT * FROM favorite_places WHERE name = :name AND " +
            "((:lat IS NULL AND latitude IS NULL) OR ABS(latitude - :lat) < 0.0001) AND " +
            "((:lng IS NULL AND longitude IS NULL) OR ABS(longitude - :lng) < 0.0001) LIMIT 1",
    )
    suspend fun favoriteByKey(name: String, lat: Double?, lng: Double?): FavoritePlaceEntity?

    // ── 정차지
    @Upsert
    suspend fun upsertStop(stop: JourneyStopEntity)

    @Query("SELECT * FROM journey_stops WHERE journeyId = :journeyId ORDER BY seq ASC")
    fun observeStops(journeyId: String): Flow<List<JourneyStopEntity>>

    @Query("SELECT * FROM journey_stops WHERE stopId = :id LIMIT 1")
    suspend fun stopOnce(id: String): JourneyStopEntity?

    @Query("SELECT COALESCE(MAX(seq), -1) FROM journey_stops WHERE journeyId = :journeyId")
    suspend fun maxStopSeq(journeyId: String): Int

    @Query("DELETE FROM journey_stops WHERE stopId = :id")
    suspend fun deleteStop(id: String)

    /** 최근 사용한 정차지 이름. 중복을 제거하고 최신순으로 반환한다. */
    @Query(
        """
        SELECT * FROM journey_stops
        WHERE name IS NOT NULL AND name != ''
        GROUP BY name
        ORDER BY MAX(createdAt) DESC
        LIMIT :limit
        """,
    )
    suspend fun recentStops(limit: Int = 8): List<JourneyStopEntity>

    /** 방문 2회 이상인 정차지를 방문 횟수 내림차순으로 반환한다. */
    @Query(
        """
        SELECT * FROM journey_stops
        WHERE name IS NOT NULL AND name != ''
        GROUP BY name
        HAVING COUNT(*) >= 2
        ORDER BY COUNT(*) DESC, MAX(createdAt) DESC
        LIMIT :limit
        """,
    )
    suspend fun frequentStops(limit: Int = 6): List<JourneyStopEntity>

    // ── 경로 점
    @Insert
    suspend fun insertRoutePoint(point: RoutePointEntity)

    @Query("SELECT * FROM journey_route_points WHERE journeyId = :journeyId ORDER BY at ASC")
    suspend fun routePointsOnce(journeyId: String): List<RoutePointEntity>

    @Query("SELECT * FROM journey_route_points WHERE journeyId = :journeyId ORDER BY at ASC")
    fun observeRoutePoints(journeyId: String): Flow<List<RoutePointEntity>>

    @Query("SELECT * FROM journey_route_points WHERE journeyId = :journeyId ORDER BY at DESC LIMIT 1")
    suspend fun lastRoutePoint(journeyId: String): RoutePointEntity?

    // ── 비용
    @Upsert
    suspend fun upsertExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE journeyId = :journeyId ORDER BY usedAt DESC")
    fun observeExpenses(journeyId: String): Flow<List<ExpenseEntity>>

    @Query("DELETE FROM expenses WHERE expenseId = :id")
    suspend fun deleteExpense(id: String)

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE journeyId = :journeyId")
    fun observeTotalExpense(journeyId: String): Flow<Long>

    // ── 이슈(파손·지연·분쟁)
    @Upsert
    suspend fun upsertIssue(issue: IssueEntity)

    @Query("SELECT * FROM journey_issues WHERE journeyId = :journeyId ORDER BY occurredAt DESC")
    fun observeIssues(journeyId: String): Flow<List<IssueEntity>>

    @Query("DELETE FROM journey_issues WHERE issueId = :id")
    suspend fun deleteIssue(id: String)

    // ── 타임라인 이벤트
    @Insert
    suspend fun insertEvent(event: JourneyEventEntity)

    @Query("SELECT * FROM journey_events WHERE journeyId = :journeyId ORDER BY at ASC, id ASC")
    fun observeEvents(journeyId: String): Flow<List<JourneyEventEntity>>

    // ── 한 번만 읽는 조회 (PDF 리포트·완료 전 증빙 확인)
    @Transaction
    @Query("SELECT * FROM journeys WHERE journeyId = :id LIMIT 1")
    suspend fun journeyWithStopsOnce(id: String): JourneyWithStops?

    @Query("SELECT * FROM journey_events WHERE journeyId = :journeyId ORDER BY at ASC, id ASC")
    suspend fun eventsOnce(journeyId: String): List<JourneyEventEntity>

    @Query("SELECT * FROM expenses WHERE journeyId = :journeyId ORDER BY usedAt ASC")
    suspend fun expensesOnce(journeyId: String): List<ExpenseEntity>

    @Query("SELECT * FROM journey_issues WHERE journeyId = :journeyId ORDER BY occurredAt ASC")
    suspend fun issuesOnce(journeyId: String): List<IssueEntity>

    @Transaction
    @Query("SELECT * FROM records WHERE isDeleted = 0 AND journeyId = :journeyId ORDER BY createdAt ASC")
    suspend fun journeyRecordsOnce(journeyId: String): List<RecordWithMedia>

    // ── 여정에 묶인 기록
    @Transaction
    @Query("SELECT * FROM records WHERE isDeleted = 0 AND journeyId = :journeyId ORDER BY createdAt ASC")
    fun observeJourneyRecords(journeyId: String): Flow<List<RecordWithMedia>>

    @Query("UPDATE records SET journeyId = :journeyId, stopId = :stopId, evidenceCategory = :category, updatedAt = :now WHERE recordId = :recordId")
    suspend fun linkRecord(recordId: String, journeyId: String?, stopId: String?, category: String?, now: Long)
}
