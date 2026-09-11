package com.naury.trubbi.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 백그라운드에서 도는 주기 클라우드 백업 작업.
 *
 * 의존성이 리포지토리 하나뿐이라 `HiltWorker` 와 커스텀 `WorkerFactory` 대신
 * [EntryPointAccessors] 로 직접 꺼낸다.
 *
 * 로그아웃 상태면 작업 없이 성공으로 끝내고, 백업 실패 시 `Result.retry()` 를 반환한다.
 *
 * @param context WorkManager 가 넘겨주는 컨텍스트
 * @param params 작업 파라미터
 */
class CloudBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    /** Worker 인스턴스는 Hilt 그래프 밖에서 생성되므로 EntryPoint 로 의존성을 받는다. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun cloudBackup(): CloudBackupRepository
    }

    override suspend fun doWork(): Result {
        val repo = EntryPointAccessors
            .fromApplication(applicationContext, Deps::class.java)
            .cloudBackup()
        if (!repo.isSignedIn()) return Result.success() // 로그인 전이면 올릴 곳이 없다
        return repo.backup().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}
