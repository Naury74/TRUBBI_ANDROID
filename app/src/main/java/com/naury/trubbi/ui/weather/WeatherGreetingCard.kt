package com.naury.trubbi.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.core.weather.Weather
import com.naury.trubbi.core.weather.WeatherCondition
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.pressable

private fun iconFor(condition: WeatherCondition) = when (condition) {
    WeatherCondition.CLEAR -> AppIcons.WeatherClear
    WeatherCondition.CLOUDS -> AppIcons.WeatherClouds
    WeatherCondition.RAIN -> AppIcons.WeatherRain
    WeatherCondition.SNOW -> AppIcons.WeatherSnow
    WeatherCondition.THUNDER -> AppIcons.WeatherThunder
    WeatherCondition.FOG -> AppIcons.WeatherFog
}

/**
 * 홈 상단 히어로 카드 — 날씨에 맞춘 애니메이션 배경 위에 인사말과 현재 기온·상태·위치를 얹는다.
 *
 * 탭하면 날씨를 다시 불러온다.
 *
 * @param greeting 시간대·날씨에 맞춘 인사말
 * @param subtitle 인사말 아래 한 줄
 */
@Composable
fun WeatherGreetingCard(
    greeting: String,
    subtitle: String,
    weather: Weather?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    val condition = weather?.condition ?: WeatherCondition.CLOUDS
    Box(
        modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(shape)
            .pressable(onClick = onRefresh),
    ) {
        WeatherBackground(condition = condition, isDay = weather?.isDay ?: true, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.42f)))),
        )
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        weather?.place ?: stringResource(R.string.weather_current_location),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (weather != null) {
                        Text(stringResource(weather.labelRes), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
                    }
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(iconFor(condition), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    if (weather != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${weather.temperatureC}°",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }
            }
            Column {
                Text(
                    greeting,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
