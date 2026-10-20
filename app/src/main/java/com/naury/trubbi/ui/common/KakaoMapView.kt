package com.naury.trubbi.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kakao.vectormap.GestureType
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
import com.kakao.vectormap.route.RouteLinePattern
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles
import com.naury.trubbi.BuildConfig
import com.naury.trubbi.R
import com.naury.trubbi.ui.theme.LocalTrubbiColors

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
 * 주행 경로선과 마커를 그리고, 내용이 모두 보이도록 카메라를 맞춘다. 사용자가 지도를 직접 움직이기 전까지는
 * 내용이 바뀔 때마다 다시 맞춘다. 예전에는 처음 한 번만 맞춰서, 경로가 먼저 오고 마커가 늦게 오면 마커가
 * 화면 밖에 잘렸다. `KakaoMapSdk.init` 이 선행돼야 한다(TrubbiApp 에서 네이티브 키로 초기화).
 *
 * @param plannedRoute 아직 달린 자취가 없을 때 정차지 순서를 잇는 점선
 * @param routeBreaks 자취가 끊긴 곳. i 가 들어 있으면 route[i-1]→route[i] 를 실선 대신 점선으로 그린다
 * @param focus 지정하면 호출부가 카메라를 제어하고 자동 프레이밍은 건너뛴다
 * @param myLocation 현재 위치 — 탭할 수 없는 파란 점으로 그린다
 * @param handle 확대·현위치 복귀 버튼이 쓸 손잡이
 * @param gesturesEnabled false 면 지도를 손으로 움직이지 않는다. 스크롤 화면 안의 미리보기 카드에서 쓰며,
 *   이때 손가락은 페이지 스크롤로 넘어가고 탭은 [onMapTap] 으로 간다
 * @param framePadding 자동으로 맞출 때 가장자리 여백. 지도 위에 얹힌 버튼에 핀이 가리지 않게 한다
 * @param obscured 지도 위에 얹힌 카드·패널이 가리는 영역. 카메라를 맞출 때 이 부분을 피한다
 * @param onCameraIdle 카메라 이동이 멈췄을 때 화면 가운데 좌표
 * @param onMarkerClick 마커 탭 — [MapMarker.id] 를 돌려준다
 */
@Composable
fun KakaoMapView(
    markers: List<MapMarker>,
    route: List<MapPoint>,
    modifier: Modifier = Modifier,
    routeColor: Int = ROUTE_BLUE,
    plannedRoute: List<MapPoint> = emptyList(),
    routeBreaks: Set<Int> = emptySet(),
    focus: MapFocus? = null,
    myLocation: MapPoint? = null,
    handle: MapHandle? = null,
    gesturesEnabled: Boolean = true,
    framePadding: Dp = 56.dp,
    obscured: PaddingValues = PaddingValues(0.dp),
    onMapTap: (() -> Unit)? = null,
    onCameraIdle: ((MapPoint) -> Unit)? = null,
    onMarkerClick: (String) -> Unit = {},
    onReady: (() -> Unit)? = null,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    // 사용자가 지도를 직접 움직였는지. 그 뒤로는 자동 프레이밍이 카메라를 빼앗지 않는다.
    var userMoved by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    val loadFailed = stringResource(R.string.map_load_failed)
    val currentMarkerClick by rememberUpdatedState(onMarkerClick)
    val paddingPx = with(LocalDensity.current) { framePadding.roundToPx() }
    // 같은 비트맵의 스타일을 매번 새로 등록하면 지도 안에 스타일이 계속 쌓인다.
    val styleCache = remember { HashMap<Bitmap, LabelStyles>() }
    val handleRef by rememberUpdatedState(handle)
    val currentIdle by rememberUpdatedState(onCameraIdle)

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
                        if (!gesturesEnabled) return@setOnTouchListener true
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
                                handleRef?.map = map
                                map.setOnLabelClickListener { _, _, label ->
                                    (label.tag as? String)?.let { currentMarkerClick(it) }
                                    true
                                }
                                map.setOnCameraMoveStartListener { _, gesture ->
                                    if (gesture != GestureType.Unknown) userMoved = true
                                }
                                map.setOnCameraMoveEndListener { _, position, _ ->
                                    position?.position?.let { currentIdle?.invoke(MapPoint(it.latitude, it.longitude)) }
                                }
                                onReady?.invoke()
                            }
                        },
                    )
                }
            },
        )

        // 카카오 지도에는 야간 스타일이 없다. 다크 테마에서는 지도를 살짝 어둡게 덮어 야간 운전 때 눈부심을 줄인다.
        // 그리기만 하고 터치는 받지 않아 지도 조작은 그대로다.
        if (LocalTrubbiColors.current.isDark) {
            Box(Modifier.matchParentSize().background(ComposeColor.Black.copy(alpha = NIGHT_DIM)))
        }

        // 조작을 끈 미리보기: 탭만 받고, 끌기는 받지 않아 바깥 스크롤로 넘어간다.
        if (!gesturesEnabled && onMapTap != null) {
            Box(Modifier.matchParentSize().pointerInput(onMapTap) { detectTapGestures { onMapTap() } })
        }

        // 인증·로드가 실패하면 빈 화면 대신 알린다. 원인(키 해시·패키지명)은 디버그 빌드에서만 보여 준다.
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
                if (BuildConfig.DEBUG) {
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
    }

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    LaunchedEffect(kakaoMap, obscured) {
        val map = kakaoMap ?: return@LaunchedEffect
        with(density) {
            runCatching {
                map.setPadding(
                    obscured.calculateLeftPadding(layoutDirection).roundToPx(),
                    obscured.calculateTopPadding().roundToPx(),
                    obscured.calculateRightPadding(layoutDirection).roundToPx(),
                    obscured.calculateBottomPadding().roundToPx(),
                )
            }
        }
    }
    LaunchedEffect(kakaoMap, route, plannedRoute, routeBreaks, routeColor) {
        val map = kakaoMap ?: return@LaunchedEffect
        drawRoute(map, route, plannedRoute, routeBreaks, routeColor)
    }
    LaunchedEffect(kakaoMap, markers, myLocation) {
        val map = kakaoMap ?: return@LaunchedEffect
        drawLabels(map, markers, myLocation, styleCache)
    }
    LaunchedEffect(kakaoMap, markers, route, plannedRoute, userMoved) {
        val map = kakaoMap ?: return@LaunchedEffect
        // focus 를 직접 주는 화면(장소 검색 등)은 스스로 카메라를 쥐므로 자동 프레이밍은 건너뛴다.
        if (focus == null && !userMoved) frameContent(map, markers.map { it.point } + route + plannedRoute, paddingPx)
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

private fun drawRoute(map: KakaoMap, route: List<MapPoint>, planned: List<MapPoint>, breaks: Set<Int>, routeColor: Int) {
    runCatching {
        val layer = map.routeLineManager?.layer ?: return
        layer.removeAll()
        fun line(points: List<MapPoint>, styles: RouteLineStyles) =
            layer.addRouteLine(RouteLineOptions.from(RouteLineSegment.from(points.map { LatLng.from(it.lat, it.lng) }, styles)))
        val dotted = RouteLineStyles.from(RouteLineStyle.from(ROUTE_WIDTH, Color.TRANSPARENT, RouteLinePattern.from(dotBitmap(routeColor), DOT_SPACING)))
        // 달린 자취가 없으면 정차지 순서를 점선으로 잇는다. 자취가 생기면 그 위에 실선이 덮는다.
        if (route.size < 2 && planned.size >= 2) line(planned, dotted)
        if (route.size < 2) return
        // 흰 테두리를 둘러 지도 위 파란 도로와 구분되게 한다.
        val solid = RouteLineStyles.from(RouteLineStyle.from(ROUTE_WIDTH, routeColor, ROUTE_STROKE, Color.WHITE))
        // 끊긴 곳(터널·추적 중단)은 실제로 달린 길을 모르므로 직선 실선 대신 점선으로 잇는다.
        var start = 0
        for (i in 1..route.lastIndex) {
            if (i in breaks) {
                if (i - 1 > start) line(route.subList(start, i), solid)
                line(listOf(route[i - 1], route[i]), dotted)
                start = i
            }
        }
        if (route.lastIndex > start) line(route.subList(start, route.size), solid)
    }
}

private fun drawLabels(map: KakaoMap, markers: List<MapMarker>, myLocation: MapPoint?, cache: HashMap<Bitmap, LabelStyles>) {
    runCatching {
        val labels = map.labelManager ?: return
        fun styleOf(bitmap: Bitmap): LabelStyles? =
            cache[bitmap] ?: labels.addLabelStyles(LabelStyles.from(LabelStyle.from(bitmap)))?.also { cache[bitmap] = it }
        labels.layer?.removeAll()
        // 현위치 점을 먼저 깔아 정차지 마커에 덮이도록 한다.
        myLocation?.let { here ->
            labels.layer?.addLabel(
                LabelOptions.from(LatLng.from(here.lat, here.lng)).setStyles(styleOf(myDotBitmap)).setClickable(false),
            )
        }
        markers.forEach { marker ->
            labels.layer?.addLabel(
                LabelOptions.from(LatLng.from(marker.point.lat, marker.point.lng))
                    .setStyles(styleOf(marker.bitmap))
                    .setClickable(true)
                    .setTag(marker.id),
            )
        }
    }
}

/** 점선용 점 하나. 색마다 한 번만 만든다. */
private val dotBitmaps = HashMap<Int, Bitmap>()

private fun dotBitmap(color: Int): Bitmap = dotBitmaps.getOrPut(color) {
    val size = 18
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val canvas = Canvas(bitmap)
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = color
        canvas.drawCircle(size / 2f, size / 2f, size * 0.34f, paint)
    }
}

private const val NIGHT_DIM = 0.3f
private const val ROUTE_WIDTH = 12f
private const val ROUTE_STROKE = 3f
private const val DOT_SPACING = 26f

/**
 * 점들이 모두 보이도록 카메라를 맞춘다.
 *
 * @return 맞출 내용이 있었는지
 */
private fun frameContent(map: KakaoMap, content: List<MapPoint>, paddingPx: Int): Boolean =
    runCatching {
        val points = content.map { LatLng.from(it.lat, it.lng) }
        when {
            points.isEmpty() -> false
            points.size == 1 -> {
                map.moveCamera(CameraUpdateFactory.newCenterPosition(points.first(), 15))
                true
            }

            else -> {
                map.moveCamera(CameraUpdateFactory.fitMapPoints(points.toTypedArray(), paddingPx))
                true
            }
        }
    }.getOrDefault(false)
