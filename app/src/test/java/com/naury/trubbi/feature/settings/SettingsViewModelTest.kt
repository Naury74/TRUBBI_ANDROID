package com.naury.trubbi.feature.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.naury.trubbi.R
import com.naury.trubbi.core.file.MediaStorage
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.backup.BackupManager
import com.naury.trubbi.data.backup.BackupResult
import com.naury.trubbi.data.settings.SettingsRepository
import io.mockk.coEvery
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
 * 백업 진행 상태 전이 테스트.
 *
 * 이 화면은 진행 중일 때 메시지를 지우면 안 되고, 끝났을 때는 지워야 한다. 예전에는 그 판단을
 * 토스트 문구를 문자열로 비교해서 했고 다국어를 켜는 순간 깨졌다. 이제 [BackupStatus.inProgress]
 * 로 판단하므로, 그 플래그가 실제로 true → false 로 바뀌는지를 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var backupManager: BackupManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val settingsRepo = mockk<SettingsRepository>(relaxed = true) {
            every { settings } returns flowOf(com.naury.trubbi.data.settings.TrubbiSettings())
            every { profile } returns flowOf(com.naury.trubbi.data.settings.UserProfile())
            every { purposes } returns flowOf(emptyList())
            every { watermarkDefaults } returns
                flowOf(com.naury.trubbi.data.settings.WatermarkDefaults())
        }
        backupManager = mockk()
        // 실제 문구 대신 리소스 id 를 돌려준다 — 어떤 문구를 고르는지까지 검증할 수 있다.
        val strings = mockk<StringProvider> {
            every { get(any<Int>()) } answers { firstArg<Int>().toString() }
            every { get(any<Int>(), *anyVararg()) } answers { firstArg<Int>().toString() }
        }
        viewModel = SettingsViewModel(
            repo = settingsRepo,
            mediaStorage = mockk<MediaStorage>(relaxed = true),
            backupManager = backupManager,
            strings = strings,
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `백업이 시작되면 진행 중으로 표시하고 끝나면 해제한다`() = runTest(dispatcher) {
        coEvery { backupManager.export(any()) } returns BackupResult.Exported(records = 12, media = 30)

        viewModel.backupStatus.test {
            assertThat(awaitItem()).isNull()

            viewModel.exportBackup(mockk(relaxed = true))

            val started = awaitItem()
            assertThat(started?.inProgress).isTrue()

            val finished = awaitItem()
            assertThat(finished?.inProgress).isFalse()
            assertThat(finished?.message).isEqualTo(R.string.backup_done.toString())
        }
    }

    @Test
    fun `백업이 실패해도 진행 중 표시는 반드시 해제된다`() = runTest(dispatcher) {
        coEvery { backupManager.export(any()) } returns BackupResult.Failed(IllegalStateException("저장 공간 부족"))

        viewModel.backupStatus.test {
            awaitItem()
            viewModel.exportBackup(mockk(relaxed = true))
            awaitItem() // 진행 중

            val finished = awaitItem()
            assertThat(finished?.inProgress).isFalse()
            assertThat(finished?.message).isEqualTo(R.string.backup_failed.toString())
        }
    }

    @Test
    fun `되돌리기 실패는 백업 실패와 다른 문구를 쓴다`() = runTest(dispatcher) {
        coEvery { backupManager.import(any()) } returns BackupResult.Failed(IllegalStateException("깨진 파일"))

        viewModel.backupStatus.test {
            awaitItem()
            viewModel.importBackup(mockk(relaxed = true))
            awaitItem() // 진행 중

            val finished = awaitItem()
            assertThat(finished?.message).isEqualTo(R.string.restore_failed.toString())
            assertThat(finished?.message).isNotEqualTo(R.string.backup_failed.toString())
        }
    }

    @Test
    fun `메시지를 지우면 상태가 비워진다`() = runTest(dispatcher) {
        coEvery { backupManager.import(any()) } returns BackupResult.Imported(records = 12)

        viewModel.backupStatus.test {
            awaitItem()
            viewModel.importBackup(mockk(relaxed = true))
            awaitItem() // 진행 중
            awaitItem() // 완료

            viewModel.clearMessage()
            assertThat(awaitItem()).isNull()
        }
    }
}
