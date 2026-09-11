package com.naury.trubbi.domain.tracking

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 여정 추적 포그라운드 서비스를 켜고 끄는 얇은 손잡이.
 *
 * 호출부가 [Intent] 와 서비스 클래스를 직접 다루지 않도록 감싼 계층이다.
 * 서비스 종료·백그라운드 실행 제한 등의 실패는 예외로 전파하지 않는다. 추적이 중단돼도
 * 여정 기록 자체는 계속 저장된다.
 *
 * @property context 서비스를 띄우기 위한 애플리케이션 컨텍스트 (Hilt 주입)
 */
@Singleton
class JourneyTracker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * 추적을 시작한다. 이미 실행 중이면 서비스가 대상 여정만 교체한다.
     *
     * @param journeyId 추적할 여정 id
     */
    fun start(journeyId: String) {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, JourneyTrackingService::class.java)
                    .putExtra(JourneyTrackingService.EXTRA_JOURNEY_ID, journeyId),
            )
        }
    }

    /** 추적을 중단한다. 여정 완료·취소 시 호출한다. */
    fun stop() {
        runCatching {
            context.startService(
                Intent(context, JourneyTrackingService::class.java)
                    .setAction(JourneyTrackingService.ACTION_STOP),
            )
        }
    }
}
