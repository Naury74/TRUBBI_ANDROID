package com.naury.trubbi.widget

import android.content.Context
import com.naury.trubbi.data.DataChangeNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 데이터 변경 알림을 바탕화면 위젯 갱신으로 옮긴다.
 *
 * 위젯을 못 그리는 상황(위젯이 하나도 놓여 있지 않거나 런처가 갱신을 거부)에서도 저장 자체는
 * 성공해야 하므로, 갱신 실패는 삼키고 호출부로 올리지 않는다.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) : DataChangeNotifier {

    override fun barcodesChanged() {
        runCatching { BarcodeViewWidget.updateAll(context) }
    }

    /** 여정 카드와 지도 위젯을 함께 갱신한다. 여정 시작·단계 진행·정차지 추가·위치 이동 때 불린다. */
    override fun journeyChanged() {
        runCatching { JourneyWidget.updateAll(context) }
        runCatching { JourneyMapWidget.updateAll(context) }
    }
}
