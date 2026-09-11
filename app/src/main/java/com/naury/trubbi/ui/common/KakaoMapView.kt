package com.naury.trubbi.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles
import com.naury.trubbi.R

/** 위경도 한 점. */
data class MapPoint(val lat: Double, val lng: Double)

/** 미리 그려 둔 [bitmap] 으로 찍는, 탭할 수 있는 마커. */
data class MapMarker(val id: String, val point: MapPoint, val bitmap: Bitmap)

/** 카메라 위치 요청. 지정하면 최초 자동 프레이밍 대신 호출부가 카메라를 쥔다. */
sealed interface MapFocus {
    /** 한 점을 [zoom] 배율로 가운데 둔다(값이 클수록 확대). */
    data class Point(val lat: Double, val lng: Double, val zoom: Int = 16) : MapFocus

    /** [points] 가 모두 화면에 들어오도록 맞춘다. */
    data class Bounds(val points: List<MapPoint>, val paddingPx: Int = 100) : MapFocus
}

/**
 * 지도 바깥(떠 있는 버튼 등)에서 카메라를 직접 움직이기 위한 손잡이.
 *
 * 지도가 아직 준비되지 않았을 수 있어 모든 호출이 조용히 무시될 수 있다.
 */
class MapHandle {
    internal var map: KakaoMap? = null

    fun zoomIn() {
        runCatching { map?.moveCamera(CameraUpdateFactory.zoomIn()) }
    }

    fun zoomOut() {
        runCatching { map?.moveCamera(CameraUpdateFactory.zoomOut()) }
    }

    fun moveTo(lat: Double, lng: Double, zoom: Int = 16) {
        runCatching { map?.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(lat, lng), zoom)) }
    }

    /** 경로·정차지 전체가 한 화면에 들어오도록 맞춘다. */
    fun fit(points: List<MapPoint>, paddingPx: Int = 120) {
        runCatching {
            val map = map ?: return@runCatching
            when {
                points.isEmpty() -> Unit
                points.size == 1 ->
                    map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(points[0].lat, points[0].lng), 15))
                else ->
                    map.moveCamera(
                        CameraUpdateFactory.fitMapPoints(
                            points.map { LatLng.from(it.lat, it.lng) }.toTypedArray(),
                            paddingPx,
                        ),
                    )
            }
        }
    }
}

/**
 * 앱 전역에서 재사용하는 카카오 지도(vectormap SDK).
 *
 * 주행 경로선과 마커를 그리고, 처음 로드될 때 한 번 전체가 보이도록 카메라를 맞춘다.
 * `KakaoMapSdk.init` 이 선행돼야 한다(TrubbiApp 에서 네이티브 키로 초기화).
 *
 * @param focus 지정하면 호출부가 카메라를 제어하고 자동 프레이밍은 건너뛴다
 * @param myLocation 현재 위치 — 탭할 수 없는 파란 점으로 그린다
 * @param handle 확대·현위치 복귀 버튼이 쓸 손잡이
 * @param onMarkerClick 마커 탭 — [MapMarker.id] 를 돌려준다
 */
@Composable
fun KakaoMapView(
    markers: List<MapMarker>,
    route: List<MapPoint>,
    modifier: Modifier = Modifier,
    routeColor: Int = ROUTE_BLUE,
    focus: MapFocus? = null,
    myLocation: MapPoint? = null,
    handle: MapHandle? = null,
    onMarkerClick: (String) -> Unit = {},
    onReady: (() -> Unit)? = null,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    var framed by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    val loadFailed = stringResource(R.string.map_load_failed)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { mapViewRef.value?.resume() }
                Lifecycle.Event.ON_PAUSE -> runCatching { mapViewRef.value?.pause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { mapViewRef.value?.finish() }
        }
    }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                MapView(ctx).also { view ->
                    mapViewRef.value = view
                    // 스크롤 목록 안에서도 두 손가락 줌이 먹도록, 터치가 시작되는 순간부터 부모의
                    // 가로채기를 막는다. 늦게 막으면 둘째 손가락이 인식되기 전에 스크롤로 넘어간다.
                    view.setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN,
                            MotionEvent.ACTION_POINTER_DOWN,
                            MotionEvent.ACTION_MOVE,
                            ->
                                v.parent?.requestDisallowInterceptTouchEvent(true)

                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL,
                            ->
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        false
                    }
                    view.start(
                        object : MapLifeCycleCallback() {
                            override fun onMapDestroy() = Unit

                            override fun onMapError(error: Exception) {
                                Log.e("KakaoMap", "map auth/load failed", error)
                                mapError = error.message ?: loadFailed
                            }
                        },
                        object : KakaoMapReadyCallback() {
                            override fun onMapReady(map: KakaoMap) {
                                mapError = null
                                kakaoMap = map
                                handle?.map = map
                                map.setOnLabelClickListener { _, _, label ->
                                    (label.tag as? String)?.let(onMarkerClick)
                                    true
                                }
                                onReady?.invoke()
                            }
                        },
                    )
                }
            },
        )

        // 인증·로드가 실패하면 빈 화면 대신 원인을 띄운다. 키 해시·패키지명 점검에 이 문구가 필요하다.
        mapError?.let { message ->
            Column(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    loadFailed,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    LaunchedEffect(kakaoMap, markers, route, myLocation) {
        val map = kakaoMap ?: return@LaunchedEffect
        drawOverlays(map, markers, route, routeColor, myLocation)
        // focus 를 직접 주는 화면(장소 검색 등)은 스스로 카메라를 쥐므로 자동 프레이밍은 건너뛴다.
        if (focus == null && !framed && frameContent(map, markers, route)) framed = true
    }

    LaunchedEffect(kakaoMap, focus) {
        val map = kakaoMap ?: return@LaunchedEffect
        val target = focus ?: return@LaunchedEffect
        runCatching {
            when (target) {
                is MapFocus.Point ->
                    map.moveCamera(
                        CameraUpdateFactory.newCenterPosition(LatLng.from(target.lat, target.lng), target.zoom),
                    )

                is MapFocus.Bounds -> when {
                    target.points.isEmpty() -> Unit
                    target.points.size == 1 ->
                        map.moveCamera(
                            CameraUpdateFactory.newCenterPosition(
                                LatLng.from(target.points[0].lat, target.points[0].lng),
                                15,
                            ),
                        )

                    else ->
                        map.moveCamera(
                            CameraUpdateFactory.fitMapPoints(
                                target.points.map { LatLng.from(it.lat, it.lng) }.toTypedArray(),
                                target.paddingPx,
                            ),
                        )
                }
            }
        }
    }
}

const val ROUTE_BLUE: Int = 0xFF2D6CDF.toInt()

/** 현위치 표시 — 옅은 정확도 후광 + 흰 테두리 + 파란 점. */
private val myDotBitmap: Bitmap by lazy {
    val size = 56
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val center = size / 2f
    paint.color = 0x332D6CDF
    canvas.drawCircle(center, center, center, paint)
    paint.color = Color.WHITE
    canvas.drawCircle(center, center, size * 0.30f, paint)
    paint.color = ROUTE_BLUE
    canvas.drawCircle(center, center, size * 0.22f, paint)
    bitmap
}

private fun drawOverlays(
    map: KakaoMap,
    markers: List<MapMarker>,
    route: List<MapPoint>,
    routeColor: Int,
    myLocation: MapPoint?,
) {
    runCatching {
        map.routeLineManager?.layer?.let { layer ->
            layer.removeAll()
            if (route.size >= 2) {
                val style = RouteLineStyles.from(RouteLineStyle.from(10f, routeColor))
                val segment = RouteLineSegment.from(route.map { LatLng.from(it.lat, it.lng) }, style)
                layer.addRouteLine(RouteLineOptions.from(segment))
            }
        }
        map.labelManager?.let { labels ->
            labels.layer?.removeAll()
            // 현위치 점을 먼저 깔아 정차지 마커에 덮이도록 한다.
            myLocation?.let { here ->
                val styles = labels.addLabelStyles(LabelStyles.from(LabelStyle.from(myDotBitmap)))
                labels.layer?.addLabel(
                    LabelOptions.from(LatLng.from(here.lat, here.lng)).setStyles(styles).setClickable(false),
                )
            }
            markers.forEach { marker ->
                val styles = labels.addLabelStyles(LabelStyles.from(LabelStyle.from(marker.bitmap)))
                labels.layer?.addLabel(
                    LabelOptions.from(LatLng.from(marker.point.lat, marker.point.lng))
                        .setStyles(styles)
                        .setClickable(true)
                        .setTag(marker.id),
                )
            }
        }
    }
}

/**
 * 경로와 마커가 모두 보이도록 카메라를 맞춘다.
 *
 * @return 맞출 내용이 있었는지. false 면 데이터가 들어온 뒤 다시 시도한다.
 */
private fun frameContent(map: KakaoMap, markers: List<MapMarker>, route: List<MapPoint>): Boolean =
    runCatching {
        val points = (route + markers.map { it.point }).map { LatLng.from(it.lat, it.lng) }
        when {
            points.isEmpty() -> false
            points.size == 1 -> {
                map.moveCamera(CameraUpdateFactory.newCenterPosition(points.first(), 15))
                true
            }

            else -> {
                map.moveCamera(CameraUpdateFactory.fitMapPoints(points.toTypedArray(), 80))
                true
            }
        }
    }.getOrDefault(false)
