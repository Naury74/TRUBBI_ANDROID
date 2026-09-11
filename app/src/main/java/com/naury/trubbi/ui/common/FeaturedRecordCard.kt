package com.naury.trubbi.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naury.trubbi.core.i18n.purposeLabel
import com.naury.trubbi.core.util.DateUtils
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.domain.model.RecordType
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 기록 하나를 크게 보여 주는 스포트라이트 카드.
 *
 * 홈과 앨범의 ‘대표 기록’ 줄이 함께 쓴다.
 *
 * @param record 표시할 기록
 * @param onClick 카드 탭
 * @param ratio 가로:세로 비율
 */
@Composable
fun FeaturedRecordCard(
    record: RecordWithMedia,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ratio: Float = 3f / 2f,
) {
    val shape = RoundedCornerShape(28.dp)
    val stacked = record.record.type == RecordType.PHOTO.name && record.media.size > 1
    Box(modifier.fillMaxWidth().aspectRatio(ratio)) {
        // 묶음 촬영이면 뒤에 카드가 겹쳐 보이게 해서 한 장이 아님을 드러낸다.
        if (stacked) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth(0.78f).height(24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, LocalTrubbiColors.current.separator, RoundedCornerShape(16.dp)),
            )
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 6.dp).fillMaxWidth(0.88f).height(24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, LocalTrubbiColors.current.separator, RoundedCornerShape(16.dp)),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .then(if (stacked) Modifier.padding(top = 13.dp) else Modifier)
                .clip(shape)
                .pressable(onClick = onClick),
        ) {
            RecordThumb(record, Modifier.fillMaxSize())
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))),
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(purposeLabel(record.record.purpose), style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp)) {
                Text(
                    record.record.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    DateUtils.formatDate(record.record.jobDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}
