package com.naury.trubbi.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 원형 프로필 아바타.
 *
 * 저장된 사진 → 지정한 대체 이미지 → 이름 첫 글자 → 사람 아이콘 순으로 내려간다.
 *
 * @param photoUri 저장된 프로필 사진
 * @param name 첫 글자를 딸 이름
 * @param size 지름
 * @param fallbackImageRes 사진이 없을 때 쓸 드로어블 (마스코트 등)
 */
@Composable
fun ProfileAvatar(
    photoUri: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    fallbackImageRes: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalTrubbiColors.current
    val label = stringResource(R.string.cd_profile)
    val avatarBg = if (colors.isDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val base = modifier
        .size(size)
        .clip(CircleShape)
        .background(avatarBg)
    val clickable = if (onClick != null) base.clickable { onClick() } else base
    Box(clickable, contentAlignment = Alignment.Center) {
        when {
            !photoUri.isNullOrBlank() -> AsyncImage(
                model = photoUri,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            fallbackImageRes != null -> Image(
                painter = painterResource(fallbackImageRes),
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(size * 0.12f),
            )
            name.isNotBlank() -> Text(
                name.trim().take(1),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.42f).sp,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            else -> Icon(
                AppIcons.Person,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}
