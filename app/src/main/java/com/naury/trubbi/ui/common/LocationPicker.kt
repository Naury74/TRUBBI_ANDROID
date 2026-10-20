package com.naury.trubbi.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KListItem
import com.naury.kinetica.ui.KListLeading
import com.naury.kinetica.ui.KSearchBar
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KVariant
import com.naury.trubbi.R
import com.naury.trubbi.core.location.CapturedLocation
import com.naury.trubbi.core.location.PlaceCandidate
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import kotlinx.coroutines.delay

/**
 * 지도 가운데 핀으로 위치를 고르는 전체 화면.
 *
 * 기록에 위치가 틀리게 들어갔거나 없을 때 고치는 데 쓴다. 지도를 움직이면 가운데 핀 아래 주소가 바뀌고, 위쪽
 * 검색창으로 장소를 찾아 바로 옮길 수도 있다.
 *
 * @param start 처음 보여 줄 위치. 없으면 서울시청 부근에서 시작한다
 * @param search 장소 검색
 * @param describe 좌표 → 주소
 * @param onPick 고른 위치(좌표와 주소)
 */
@Composable
fun LocationPicker(
    start: MapPoint?,
    search: suspend (query: String, nearLat: Double?, nearLng: Double?) -> List<PlaceCandidate>,
    describe: suspend (lat: Double, lng: Double) -> CapturedLocation,
    onPick: (CapturedLocation) -> Unit,
    onDismiss: () -> Unit,
) {
    val origin = start ?: DEFAULT_START
    var center by remember { mutableStateOf(origin) }
    var focus by remember { mutableStateOf<MapFocus>(MapFocus.Point(origin.lat, origin.lng, 17)) }
    var picked by remember { mutableStateOf<CapturedLocation?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceCandidate>>(emptyList()) }
    val focusManager = LocalFocusManager.current

    // 지도가 멈추면 잠깐 기다렸다가 주소를 찾는다. 끄는 동안 매번 찾지 않는다.
    LaunchedEffect(center) {
        picked = null
        delay(DESCRIBE_DEBOUNCE_MS)
        picked = runCatching { describe(center.lat, center.lng) }.getOrNull()
    }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        results = runCatching { search(query.trim(), center.lat, center.lng) }.getOrDefault(emptyList())
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            KakaoMapView(
                markers = emptyList(),
                route = emptyList(),
                focus = focus,
                onCameraIdle = { center = it },
                modifier = Modifier.fillMaxSize(),
            )
            // 가운데 고정 핀. 핀 끝이 화면 정중앙에 오도록 반만큼 올린다.
            Icon(
                AppIcons.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center).size(PIN_SIZE.dp).offset(y = (-PIN_SIZE / 2).dp),
            )

            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
                KSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column {
                        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            KIconButton(AppIcons.ArrowBackIos, stringResource(R.string.action_back), onDismiss, variant = KVariant.Text, size = 44.dp)
                            KSearchBar(
                                query = query,
                                onQueryChange = { query = it },
                                onSearch = { query = it },
                                placeholder = stringResource(R.string.location_pick_search),
                                showCancel = false,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (results.isNotEmpty()) {
                            LazyColumn(Modifier.heightIn(max = 320.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                                items(results, key = { "${it.latitude},${it.longitude},${it.name}" }) { p ->
                                    KListItem(
                                        headline = p.name,
                                        supporting = p.address,
                                        leading = KListLeading.Icon(AppIcons.LocationOn, tint = MaterialTheme.colorScheme.primary),
                                        onClick = {
                                            focus = MapFocus.Point(p.latitude, p.longitude, 17)
                                            center = MapPoint(p.latitude, p.longitude)
                                            query = ""
                                            focusManager.clearFocus()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            KSurface(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        stringResource(R.string.location_pick_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalTrubbiColors.current.secondaryLabel,
                    )
                    Text(
                        picked?.let { it.building ?: it.address } ?: stringResource(R.string.location_pick_hint),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                    )
                    KButton(
                        stringResource(R.string.location_pick_confirm),
                        { onPick(picked ?: CapturedLocation(center.lat, center.lng, null)) },
                        icon = AppIcons.Check,
                        fullWidth = true,
                    )
                }
            }
        }
    }
}

private val DEFAULT_START = MapPoint(37.5665, 126.9780)
private const val PIN_SIZE = 44
private const val DESCRIBE_DEBOUNCE_MS = 400L
private const val SEARCH_DEBOUNCE_MS = 300L
