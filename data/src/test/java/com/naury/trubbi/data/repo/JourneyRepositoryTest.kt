package com.naury.trubbi.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.naury.trubbi.core.location.LocationProvider
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.DataChangeNotifier
import com.naury.trubbi.data.local.TrubbiDatabase
import com.naury.trubbi.domain.model.JourneyEventType
import com.naury.trubbi.domain.model.MissingEvidence
import com.naury.trubbi.domain.model.StopKind
import com.naury.trubbi.domain.model.StopPhase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 여정 진행 규칙 테스트 — 인메모리 Room 위에서 실제 리포지토리를 돌린다.
 *
 * 정차지의 도착 → 작업 → 완료 사이클과 완료 전 증빙 확인은 홈 카드·알림·위젯·플로팅이
 * 모두 공유하는 유일한 진입점이라, 여기서 어긋나면 앱 전체가 어긋난다.
 */
@RunWith(RobolectricTestRunner::class)
class JourneyRepositoryTest {

    private lateinit var db: TrubbiDatabase
    private lateinit var repo: JourneyRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TrubbiDatabase::class.java,
        ).allowMainThreadQueries().build()

        // 위치와 위젯은 이 규칙과 무관한 바깥 세계라 가짜로 둔다.
        val location = mockk<LocationProvider>(relaxed = true)
        coEvery { location.capture() } returns null
        coEvery { location.geocode(any()) } returns null

        repo = JourneyRepository(
            dao = db.journeyDao(),
            location = location,
            appScope = TestScope(StandardTestDispatcher()),
            notifier = mockk<DataChangeNotifier>(relaxed = true),
            strings = StringProvider(ApplicationProvider.getApplicationContext()),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `출발지는 시작하자마자 완료된다`() = runTest {
        val id = repo.startJourney(
            listOf(
                draft(StopKind.ORIGIN, "차고지"),
                draft(StopKind.DROPOFF, "부산 신항"),
            ),
        )

        val stops = db.journeyDao().journeyWithStopsOnce(id)!!.orderedStops

        // 출발지는 '지금 떠나는 곳'이라 대기·완료 사이클이 없다.
        assertThat(stops[0].phase).isEqualTo(StopPhase.DONE)
        assertThat(stops[1].phase).isEqualTo(StopPhase.MOVING)
    }

    @Test
    fun `현재 정차지는 출발지를 건너뛰고 다음으로 넘어간다`() = runTest {
        val id = repo.startJourney(listOf(draft(StopKind.ORIGIN, "차고지"), draft(StopKind.PICKUP, "울산항")))

        val current = db.journeyDao().journeyWithStopsOnce(id)!!.currentStop

        assertThat(current?.name).isEqualTo("울산항")
    }

    @Test
    fun `한 번 진행하면 한 단계만 올라간다`() = runTest {
        val id = repo.startJourney(listOf(draft(StopKind.PICKUP, "울산항")))
        val stop = db.journeyDao().journeyWithStopsOnce(id)!!.orderedStops.first()

        assertThat(repo.advanceStop(stop)).isEqualTo(StopPhase.WAITING)
        assertThat(repo.advanceStop(stop)).isEqualTo(StopPhase.WORKING)
        assertThat(repo.advanceStop(stop)).isEqualTo(StopPhase.DONE)
    }

    @Test
    fun `오래된 스냅샷으로 진행해도 단계가 되돌아가지 않는다`() = runTest {
        val id = repo.startJourney(listOf(draft(StopKind.PICKUP, "울산항")))
        val stale = db.journeyDao().journeyWithStopsOnce(id)!!.orderedStops.first()

        repo.advanceStop(stale)
        repo.advanceStop(stale)
        repo.advanceStop(stale)
        // 화면이 들고 있던 옛 스냅샷으로 다시 눌러도 완료 상태를 유지해야 한다.
        val again = repo.advanceStop(stale)

        assertThat(again).isEqualTo(StopPhase.DONE)
    }

    @Test
    fun `작업 시작을 먼저 누르면 도착도 함께 기록된다`() = runTest {
        val id = repo.startJourney(listOf(draft(StopKind.PICKUP, "울산항")))
        val stopId = db.journeyDao().journeyWithStopsOnce(id)!!.orderedStops.first().stopId

        repo.startWork(stopId)

        val stop = db.journeyDao().stopOnce(stopId)!!
        assertThat(stop.arrivedAt).isNotNull()
        assertThat(stop.phase).isEqualTo(StopPhase.WORKING)
    }

    @Test
    fun `진행할 때마다 타임라인에 이벤트가 쌓인다`() = runTest {
        val id = repo.startJourney(listOf(draft(StopKind.PICKUP, "울산항")))
        val stop = db.journeyDao().journeyWithStopsOnce(id)!!.orderedStops.first()

        repo.advanceStop(stop)
        repo.advanceStop(stop)
        repo.advanceStop(stop)

        val types = db.journeyDao().eventsOnce(id).map { it.type }
        assertThat(types).containsAtLeast(
            JourneyEventType.JOURNEY_STARTED.name,
            JourneyEventType.ARRIVED.name,
            JourneyEventType.WORK_STARTED.name,
            JourneyEventType.STOP_COMPLETED.name,
        )
    }

    @Test
    fun `이벤트는 번역된 제목 대신 종류와 인자로 저장된다`() = runTest {
        val id = repo.startJourney(listOf(draft(StopKind.PICKUP, "울산항")))
        val stop = db.journeyDao().journeyWithStopsOnce(id)!!.orderedStops.first()
        repo.advanceStop(stop)

        val arrived = db.journeyDao().eventsOnce(id)
            .first { it.type == JourneyEventType.ARRIVED.name }

        // 저장 시점 언어로 굳은 문구가 남아 있으면 나중에 언어를 바꿔도 그대로 남는다.
        assertThat(arrived.title).isEmpty()
        assertThat(arrived.titleArg).isEqualTo(StopKind.PICKUP.name)
    }

    @Test
    fun `상하차 사진이 없으면 완료 전에 알려 준다`() = runTest {
        val id = repo.startJourney(listOf(draft(StopKind.PICKUP, "울산항"), draft(StopKind.DROPOFF, "부산 신항")))

        val missing = repo.missingEvidence(id)

        assertThat(missing).containsExactly(MissingEvidence.PICKUP_PHOTO, MissingEvidence.DROPOFF_PHOTO)
    }

    @Test
    fun `출발지만 있는 여정은 빠진 증빙이 없다`() = runTest {
        val id = repo.startJourney(listOf(draft(StopKind.ORIGIN, "차고지")))

        assertThat(repo.missingEvidence(id)).isEmpty()
    }

    @Test
    fun `정차지를 추가하면 맨 뒤에 붙는다`() = runTest {
        val id = repo.startJourney(listOf(draft(StopKind.PICKUP, "울산항")))

        repo.addStop(id, StopKind.DROPOFF, name = "부산 신항")

        val stops = db.journeyDao().journeyWithStopsOnce(id)!!.orderedStops
        assertThat(stops.map { it.name }).containsExactly("울산항", "부산 신항").inOrder()
    }

    @Test
    fun `순서를 바꾸면 넘긴 차례대로 다시 매겨진다`() = runTest {
        val id = repo.startJourney(
            listOf(draft(StopKind.PICKUP, "A"), draft(StopKind.DROPOFF, "B"), draft(StopKind.DROPOFF, "C")),
        )
        val stops = db.journeyDao().journeyWithStopsOnce(id)!!.orderedStops

        repo.reorderStops(id, listOf(stops[2].stopId, stops[0].stopId, stops[1].stopId))

        val reordered = db.journeyDao().journeyWithStopsOnce(id)!!.orderedStops
        assertThat(reordered.map { it.name }).containsExactly("C", "A", "B").inOrder()
    }

    @Test
    fun `여정을 완료하면 진행 중 여정이 사라진다`() = runTest {
        val id = repo.startJourney(listOf(draft(StopKind.PICKUP, "울산항")))

        repo.completeJourney(id)

        assertThat(repo.activeJourneyOnce()).isNull()
    }

    private fun draft(kind: StopKind, name: String) =
        JourneyRepository.StopDraft(kind = kind, name = name)
}
