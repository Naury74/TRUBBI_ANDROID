package com.naury.trubbi.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.purposeLabel
import com.naury.trubbi.domain.model.RecordType
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassIconButton
import com.naury.trubbi.ui.designsystem.glass
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 타임라인을 좁히는 검색창과 칩 줄, 그리고 다중 선택 중에 뜨는 하단 도크.
 */
/** Album-style filter bar (type + 용도 chips), pinned to the top while searching. */
@Composable
internal fun FilterStickyRow(
    typeFilter: String?,
    purposeFilter: String?,
    purposes: List<String>,
    onType: (String?) -> Unit,
    onPurpose: (String?) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("PHOTO", "VIDEO", "DOCUMENT", "BARCODE", "MEMO", "FAVORITE")) { t ->
                CategoryChip(label = typeFilterLabel(t), selected = typeFilter == t) { onType(t) }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(purposes) { p ->
                CategoryChip(label = purposeLabel(p), selected = purposeFilter == p) { onPurpose(p) }
            }
        }
    }
}

@Composable
internal fun CategoryChip(label: String, selected: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    val base = if (selected) {
        Modifier.background(Color.White, shape)
    } else {
        Modifier.glass(shape, elevation = 3.dp)
    }
    Box(
        base.pressable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun typeFilterLabel(t: String): String = when (t) {
    "PHOTO" -> stringResource(RecordType.PHOTO.labelRes)
    "VIDEO" -> stringResource(RecordType.VIDEO.labelRes)
    "DOCUMENT" -> stringResource(RecordType.DOCUMENT.labelRes)
    "BARCODE" -> stringResource(RecordType.BARCODE.labelRes)
    "MEMO" -> stringResource(RecordType.MEMO.labelRes)
    "FAVORITE" -> stringResource(R.string.album_filter_favorite)
    else -> t
}

@Composable
internal fun SearchField(
    query: String,
    focusRequester: FocusRequester,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier.fillMaxWidth().glass(RoundedCornerShape(percent = 50)).focusRequester(focusRequester),
        singleLine = true,
        shape = RoundedCornerShape(percent = 50),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
        ),
        placeholder = { Text(stringResource(R.string.home_search_hint)) },
        leadingIcon = { androidx.compose.material3.Icon(AppIcons.Search, contentDescription = null) },
        trailingIcon = {
            androidx.compose.material3.IconButton(onClick = onClose) {
                androidx.compose.material3.Icon(AppIcons.Close, contentDescription = stringResource(R.string.home_close_search))
            }
        },
    )
}

/**
 * 여러 개를 고르는 중에 아래에서 올라오는 도구 막대.
 *
 * @param count 고른 개수
 * @param onClose 선택을 끝낼 때
 * @param onShare 공유를 누를 때
 * @param onFavorite 즐겨찾기를 누를 때
 * @param onDelete 삭제를 누를 때
 * @param modifier 바깥 배치용
 */
@Composable
internal fun HomeSelectionDock(
    count: Int,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SpringPop(0) { GlassIconButton(AppIcons.Close, onClose, stringResource(R.string.action_close), size = 46.dp) }
        SpringPop(45) {
            Box(
                Modifier.glass(RoundedCornerShape(percent = 50)).padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    stringResource(R.string.selected_count, count),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        SpringPop(90) { GlassIconButton(AppIcons.Share, onShare, stringResource(R.string.action_share), size = 46.dp) }
        SpringPop(135) {
            GlassIconButton(AppIcons.Star, onFavorite, stringResource(R.string.action_favorite), size = 46.dp, tint = LocalTrubbiColors.current.orange)
        }
        SpringPop(180) {
            GlassIconButton(AppIcons.Delete, onDelete, stringResource(R.string.action_delete), size = 46.dp, tint = LocalTrubbiColors.current.red)
        }
    }
}

/**
 * 지연 시간을 두고 시작하는 등장 효과.
 *
 * @param delayMillis 시작 지연(ms). 항목마다 값을 늘리면 순차적으로 나타난다
 * @param content 나타낼 내용
 */
@Composable
private fun SpringPop(delayMillis: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis.toLong())
        shown = true
    }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f),
        label = "springPop",
    )
    Box(
        Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = scale.coerceIn(0f, 1f)
        },
    ) { content() }
}
