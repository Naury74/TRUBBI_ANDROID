package com.naury.trubbi.data

import com.naury.trubbi.domain.model.StopPhase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/** 앱 화면 바깥(플로팅 컨트롤·알림)에서 일어난 단계 진행 한 건. */
data class AdvanceEvent(
    val stopName: String,
    val kindLabel: String,
    val phase: StopPhase,
    val lastOfJourney: Boolean,
    val id: Long = System.nanoTime(),
)

/**
 * 앱 밖에서 누른 단계 진행을, 화면이 떠 있을 때 앱 안과 똑같은 피드백으로 이어 주는 통로.
 *
 * 뷰모델은 [advance] 를 구독한다. 앱 안 버튼은 리포지토리를 직접 부르고 여기에 쓰지 않는다
 * (같은 피드백이 두 번 뜨는 걸 막는다).
 */
@Singleton
class JourneyControlSignal @Inject constructor() {
    val advance = MutableStateFlow<AdvanceEvent?>(null)
    fun post(event: AdvanceEvent) {
        advance.value = event
    }

    /**
     * 여정 화면이 보이는 중인지.
     *
     * true 면 음성과 확인 UI 를 앱이 맡고 플로팅 서비스는 침묵한다. false 면 반대다.
     * 이 구분이 없으면 같은 안내가 두 번 들린다.
     */
    val appForeground = MutableStateFlow(false)
    fun setForeground(on: Boolean) {
        appForeground.value = on
    }
}
