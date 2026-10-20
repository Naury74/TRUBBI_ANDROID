package com.naury.trubbi.ui.weather

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.naury.trubbi.R
import com.naury.trubbi.core.location.PlaceNames
import com.naury.trubbi.core.weather.Weather
import com.naury.trubbi.core.weather.WeatherCondition
import com.naury.trubbi.ui.common.ProfileAvatar
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassIconButton
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/** 헤더에 넣을 동네 이름("중구 명동"). 헤더 폭에 전체 주소가 들어가지 않는다. */
private fun shortAddress(place: String?, fallback: String): String = PlaceNames.region(place) ?: fallback

private fun headerIcon(condition: WeatherCondition) = when (condition) {
    WeatherCondition.CLEAR -> AppIcons.WeatherClear
    WeatherCondition.CLOUDS -> AppIcons.WeatherClouds
    WeatherCondition.RAIN -> AppIcons.WeatherRain
    WeatherCondition.SNOW -> AppIcons.WeatherSnow
    WeatherCondition.THUNDER -> AppIcons.WeatherThunder
    WeatherCondition.FOG -> AppIcons.WeatherFog
}

/** 가입 경로를 사람이 읽는 문구로. */
@StringRes
private fun membershipLabelRes(providers: List<String>): Int = when {
    providers.any { it.contains("google", true) } -> R.string.membership_google
    providers.any { it.contains("kakao", true) } -> R.string.membership_kakao
    providers.any { it.contains("naver", true) } -> R.string.membership_naver
    providers.any { it.contains("password", true) } -> R.string.membership_email
    else -> R.string.membership_default
}

private fun isSnsAccount(providers: List<String>): Boolean =
    providers.any { it.contains("google", true) || it.contains("kakao", true) || it.contains("naver", true) }

/**
 * 스크롤에 따라 접히는 홈 헤더.
 *
 * 접히면 기온과 로고는 사라지고 현재 위치만 남는다.
 *
 * @param collapse 0=완전히 펼침, 1=완전히 접힘
 * @param topPadding 상태바 인셋
 */
@Composable
fun WeatherHeader(
    collapse: Float,
    topPadding: Dp,
    weather: Weather?,
    name: String,
    profilePhoto: String?,
    email: String?,
    providers: List<String>,
    emailVerified: Boolean,
    greeting: String?,
    onAvatar: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = collapse.coerceIn(0f, 1f)
    val condition = weather?.condition ?: WeatherCondition.CLOUDS
    val bannerHeight = lerp(204.dp, 58.dp, progress)

    Box(modifier.fillMaxWidth().height(bannerHeight + topPadding)) {
        // 접히면 로고가 사라지고 그 자리를 위치가 차지한다.
        com.naury.trubbi.ui.designsystem.TrubbiLogoInline(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = topPadding + 12.dp, start = 20.dp)
                .graphicsLayer { alpha = 1f - progress },
        )

        Row(
            Modifier.align(Alignment.TopEnd).padding(top = topPadding + 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 설정은 아래 탭 바와 프로필 메뉴에 있어 여기서는 뺀다(버튼 셋이 몰려 답답했다).
            GlassIconButton(AppIcons.Search, onSearch, stringResource(R.string.action_search), size = 40.dp)
            Spacer(Modifier.width(8.dp))
            var showProfile by remember { mutableStateOf(false) }
            Box {
                ProfileAvatar(photoUri = profilePhoto, name = name, size = 40.dp, onClick = { showProfile = true })
                DropdownMenu(expanded = showProfile, onDismissRequest = { showProfile = false }) {
                    ProfilePopupContent(
                        name = name,
                        profilePhoto = profilePhoto,
                        email = email,
                        providers = providers,
                        emailVerified = emailVerified,
                        onChangePhoto = {
                            showProfile = false
                            onAvatar()
                        },
                        onSettings = {
                            showProfile = false
                            onSettings()
                        },
                    )
                }
            }
        }

        // 위치는 접히는 동안 로고 자리로 서서히 올라가 끝까지 남는다.
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = lerp(topPadding + 58.dp, topPadding + 16.dp, progress), start = 20.dp, end = 120.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.LocationOn, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(lerp(15.dp, 19.dp, progress)))
            Spacer(Modifier.width(4.dp))
            Text(
                shortAddress(weather?.place, stringResource(R.string.weather_no_location)),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = (14f + 4f * progress).sp),
                color = Color.White.copy(alpha = 0.9f + 0.1f * progress),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 인사 → 기온·날씨. 접히면 함께 사라진다.
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = topPadding + 86.dp, start = 20.dp, end = 20.dp)
                .graphicsLayer { alpha = 1f - progress * 1.4f },
        ) {
            greeting?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (weather != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${weather.temperatureC}°",
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 52.sp, lineHeight = 56.sp),
                        color = Color.White,
                    )
                    Spacer(Modifier.width(12.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Color.White.copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(headerIcon(condition), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(weather.labelRes), style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            }
        }
    }
}

/** 프로필을 탭하면 뜨는 팝업 — 이름·가입 유형·이메일 인증 상태와 사진 변경·설정. */
@Composable
private fun ProfilePopupContent(
    name: String,
    profilePhoto: String?,
    email: String?,
    providers: List<String>,
    emailVerified: Boolean,
    onChangePhoto: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = LocalTrubbiColors.current
    val sns = isSnsAccount(providers)
    Column(Modifier.widthIn(min = 240.dp, max = 300.dp).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(photoUri = profilePhoto, name = name, size = 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name.ifBlank { stringResource(R.string.profile_no_name) },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(membershipLabelRes(providers)),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = colors.separator)
        Spacer(Modifier.height(12.dp))

        InfoRow(
            icon = if (sns) AppIcons.Person else AppIcons.Mail,
            label = stringResource(R.string.profile_account_type),
            value = stringResource(if (sns) R.string.profile_type_sns else R.string.profile_type_email),
        )
        if (!email.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Mail, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_verified_email), style = MaterialTheme.typography.labelSmall, color = colors.secondaryLabel)
                    Text(
                        email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                VerifiedBadge(emailVerified)
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = colors.separator)
        Spacer(Modifier.height(6.dp))

        ActionRow(AppIcons.PhotoCamera, stringResource(R.string.profile_change_photo), onChangePhoto)
        ActionRow(AppIcons.Settings, stringResource(R.string.action_settings), onSettings)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    val colors = LocalTrubbiColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.secondaryLabel)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun VerifiedBadge(verified: Boolean) {
    val colors = LocalTrubbiColors.current
    val color = if (verified) colors.green else colors.orange
    Row(
        Modifier.padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (verified) AppIcons.Check else AppIcons.Info, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            stringResource(if (verified) R.string.profile_verified else R.string.profile_unverified),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
