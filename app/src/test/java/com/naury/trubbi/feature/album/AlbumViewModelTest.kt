package com.naury.trubbi.feature.album

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.naury.trubbi.core.file.MediaStorage
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.AlbumEntryRequest
import com.naury.trubbi.data.RecordOrderHolder
import com.naury.trubbi.data.local.MediaEntity
import com.naury.trubbi.data.local.RecordEntity
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.data.repo.RecordRepository
import com.naury.trubbi.data.settings.SettingsRepository
import com.naury.trubbi.domain.model.RecordType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * 앨범 필터·검색 조합 테스트.
 *
 * 종류·용도·기간 필터가 각각 별도의 Flow 라 [kotlinx.coroutines.flow.combine] 으로 합쳐진다.
 * 조합 순서나 조건이 어긋나면 화면에는 "결과 없음" 만 보이고 원인을 알 수 없으므로,
 * 세 필터가 함께 걸렸을 때의 교집합과 검색 breakdown 집계를 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun record(
        id: String,
        type: RecordType,
        purpose: String,
        jobDate: String,
        title: String = "제목-$id",
        memo: String? = null,
        favorite: Boolean = false,
    ) = RecordWithMedia(
        record = RecordEntity(
            recordId = id,
            type = type.name,
            title = title,
            purpose = purpose,
            memo = memo,
            jobDate = jobDate,
            createdAt = id.hashCode().toLong(),
            updatedAt = 0L,
            latitude = null,
            longitude = null,
            locationName = null,
            tags = emptyList(),
            barcodeValue = null,
            barcodeFormat = null,
            isFavorite = favorite,
        ),
        media = emptyList<MediaEntity>(),
    )

    private val records = listOf(
        record("a", RecordType.PHOTO, "상차", "2026-09-01", title = "부산 파렛트"),
        record("b", RecordType.PHOTO, "하차", "2026-09-02"),
        record("c", RecordType.DOCUMENT, "상차", "2026-09-03"),
        record("d", RecordType.PHOTO, "상차", "2026-09-10", memo = "파렛트 파손"),
        record("e", RecordType.MEMO, "기타", "2026-09-02", favorite = true),
    )

    private lateinit var viewModel: AlbumViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val repo = mockk<RecordRepository> {
            every { observeRecords() } returns flowOf(records)
        }
        val settingsRepo = mockk<SettingsRepository>(relaxed = true) {
            every { purposes } returns flowOf(listOf("상차", "하차", "기타"))
            every { gridColumns } returns flowOf(0)
        }
        // 기본 용도는 저장된 문자열이 그대로 표시 문구가 된다.
        val strings = mockk<StringProvider> {
            every { get(any<Int>()) } returns ""
            every { get(any<Int>(), *anyVararg()) } returns ""
        }
        viewModel = AlbumViewModel(
            repo = repo,
            settingsRepo = settingsRepo,
            mediaStorage = mockk<MediaStorage>(relaxed = true),
            orderHolder = RecordOrderHolder(),
            entryRequest = AlbumEntryRequest(),
            strings = strings,
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `필터를 걸지 않으면 전체가 최신순으로 나온다`() = runTest(dispatcher) {
        viewModel.records.test {
            assertThat(awaitItem()).isEmpty() // 초기값
            val all = awaitItem()
            assertThat(all).hasSize(5)
        }
    }

    @Test
    fun `종류와 용도와 기간이 함께 걸리면 교집합만 남는다`() = runTest(dispatcher) {
        viewModel.setFilter(AlbumFilter.PHOTO)
        viewModel.setPurposeFilter("상차")
        viewModel.setDateRange("2026-09-01", "2026-09-05")

        viewModel.records.test {
            // 사진 + 상차 + 기간 안 → a 하나만 남는다 (d 는 09-10 이라 기간 밖).
            val ids = awaitLast().map { it.record.recordId }
            assertThat(ids).containsExactly("a")
        }
    }

    @Test
    fun `즐겨찾기 필터는 종류를 가리지 않는다`() = runTest(dispatcher) {
        viewModel.setFilter(AlbumFilter.FAVORITE)

        viewModel.records.test {
            val ids = awaitLast().map { it.record.recordId }
            assertThat(ids).containsExactly("e")
        }
    }

    @Test
    fun `필터가 하나라도 걸리면 hasActiveFilter 가 켜진다`() = runTest(dispatcher) {
        viewModel.hasActiveFilter.test {
            assertThat(awaitItem()).isFalse()

            viewModel.setPurposeFilter("상차")
            assertThat(awaitItem()).isTrue()

            viewModel.setPurposeFilter(null)
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `검색 breakdown 은 걸린 항목별로 건수를 센다`() = runTest(dispatcher) {
        viewModel.setSearchQuery("파렛트")

        viewModel.searchBreakdown.test {
            val breakdown = awaitLast().toMap()
            // a 는 제목에서, d 는 메모에서 걸린다.
            assertThat(breakdown[SearchField.NAME]).isEqualTo(1)
            assertThat(breakdown[SearchField.MEMO]).isEqualTo(1)
        }
    }

    /** combine 이 여러 번 방출하므로 마지막으로 안정된 값만 본다. */
    private suspend fun <T> app.cash.turbine.ReceiveTurbine<List<T>>.awaitLast(): List<T> {
        var last = awaitItem()
        while (true) {
            val next = runCatching { awaitItem() }.getOrNull() ?: return last
            last = next
        }
    }
}
