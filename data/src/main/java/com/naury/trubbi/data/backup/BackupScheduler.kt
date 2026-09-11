package com.naury.trubbi.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 주기 클라우드 백업 작업을 WorkManager 에 걸고 푸는 곳.
 *
 * @property context WorkManager 인스턴스를 얻기 위한 애플리케이션 컨텍스트 (Hilt 주입)
 */
@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * 두 설정 값을 한 번에 반영한다. 호출 순서와 무관하게 같은 결과가 되도록 멱등하게 동작한다.
     *
     * @param enabled 자동 백업 사용 여부. false 면 예약된 작업을 취소한다
     * @param wifiOnly true 면 종량제가 아닌 망에서만 실행 (`UNMETERED`)
     */
    fun apply(enabled: Boolean, wifiOnly: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        // UPDATE 는 다음 실행 시각을 유지한 채 제약만 교체한다.
        // REPLACE 를 쓰면 설정을 바꿀 때마다 12시간 주기가 처음부터 다시 시작된다.
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val WORK_NAME = "auto_cloud_backup"
    }
}
