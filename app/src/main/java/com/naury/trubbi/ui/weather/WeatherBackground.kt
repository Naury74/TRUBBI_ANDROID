package com.naury.trubbi.ui.weather

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.naury.trubbi.core.weather.WeatherCondition
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 홈 상단 날씨 배경.
 *
 * `assets/weather/` 에 `{날씨}_{day|night}.json` 이름의 Lottie 가 있으면 재생하고,
 * 없으면 직접 그리는 배경으로 넘어간다. 애니메이션 파일이 하나도 없어도 화면은 성립한다.
 */

/**
 * 날씨·시간대에 맞는 배경을 그린다.
 *
 * @param condition 지금 날씨
 * @param isDay 낮이면 true. 같은 날씨라도 밤은 색과 요소가 다르다
 * @param modifier 바깥 배치용
 */
@Composable
fun WeatherBackground(
    condition: WeatherCondition,
    isDay: Boolean,
    modifier: Modifier = Modifier,
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset(weatherAssetName(condition, isDay)),
    )
    if (composition != null) {
        val hour = remember { LocalTime.now().hour }
        val sine = remember { CubicBezierEasing(0.37f, 0f, 0.63f, 1f) }
        val transition = rememberInfiniteTransition(label = "lottieSky")
        val travel by transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(48000, easing = LinearEasing), RepeatMode.Restart),
            label = "travel",
        )
        val travelBack by transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(83000, easing = LinearEasing), RepeatMode.Restart),
            label = "travelBack",
        )
        val sway by transition.animateFloat(
            -1f,
            1f,
            infiniteRepeatable(tween(11000, easing = sine), RepeatMode.Reverse),
            label = "sway",
        )
        val bob by transition.animateFloat(
            -1f,
            1f,
            infiniteRepeatable(tween(7300, easing = sine), RepeatMode.Reverse),
            label = "bob",
        )
        val bob2 by transition.animateFloat(
            -1f,
            1f,
            infiniteRepeatable(tween(4700, easing = sine), RepeatMode.Reverse),
            label = "bob2",
        )
        val breathe by transition.animateFloat(
            0.97f,
            1.03f,
            infiniteRepeatable(tween(6400, easing = sine), RepeatMode.Reverse),
            label = "breathe",
        )
        val fall by transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart),
            label = "fall",
        )
        val flash by transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
            label = "flash",
        )
        val driftFx by transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
            label = "driftFx",
        )
        val rng = remember(condition) { Random(7) }
        val drops = remember(condition) {
            val n = if (condition == WeatherCondition.SNOW) 48 else 80
            if (condition in listOf(WeatherCondition.RAIN, WeatherCondition.THUNDER, WeatherCondition.SNOW)) {
                List(n) { Triple(rng.nextFloat(), rng.nextFloat(), 0.45f + rng.nextFloat()) }
            } else {
                emptyList()
            }
        }
        val stars = remember(condition) { List(46) { Triple(rng.nextFloat(), rng.nextFloat() * 0.8f, rng.nextFloat()) } }
        // assets 에 `bg_{날씨}_{day|night}.json` 을 넣으면 전체 배경 Lottie 로도 쓸 수 있다.
        val bgComposition by rememberLottieComposition(
            LottieCompositionSpec.Asset(weatherAssetName(condition, isDay).replace("weather/", "weather/bg_")),
        )
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(gradientForWeather(condition, isDay, hour))),
        ) {
            val w = maxWidth
            val h = maxHeight
            val bg = bgComposition
            if (bg != null) {
                val compAr = bg.bounds.width().toFloat() / bg.bounds.height().coerceAtLeast(1).toFloat()
                val strip = compAr > 1.6f
                when {
                    condition == WeatherCondition.RAIN || condition == WeatherCondition.THUNDER || condition == WeatherCondition.SNOW ->
                        LottieAnimation(
                            composition = bg,
                            iterations = LottieConstants.IterateForever,
                            renderMode = com.airbnb.lottie.RenderMode.HARDWARE,
                            contentScale = if (strip) ContentScale.FillBounds else ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.88f },
                        )
                    (condition == WeatherCondition.CLOUDS || condition == WeatherCondition.FOG || condition == WeatherCondition.CLEAR) && strip ->
                        LottieAnimation(
                            composition = bg,
                            iterations = LottieConstants.IterateForever,
                            renderMode = com.airbnb.lottie.RenderMode.HARDWARE,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(compAr)
                                .offset(y = h * 0.03f)
                                .graphicsLayer { alpha = 0.8f },
                        )
                    else ->
                        LottieAnimation(
                            composition = bg,
                            iterations = LottieConstants.IterateForever,
                            renderMode = com.airbnb.lottie.RenderMode.HARDWARE,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.9f },
                        )
                }
                if (condition == WeatherCondition.THUNDER) {
                    Canvas(Modifier.fillMaxSize()) { drawFlash(flash) }
                }
            } else {
                // Lottie 가 없을 때 직접 그리는 효과.
                Canvas(Modifier.fillMaxSize()) {
                    when (condition) {
                        WeatherCondition.RAIN -> drawRain(drops, fall)
                        WeatherCondition.THUNDER -> {
                            drawRain(drops, fall)
                            drawFlash(flash)
                        }
                        WeatherCondition.SNOW -> drawSnow(drops, fall, driftFx)
                        WeatherCondition.FOG -> drawFog(driftFx)
                        WeatherCondition.CLEAR -> if (!isDay) drawStars(stars, (bob + 1f) / 2f)
                        WeatherCondition.CLOUDS -> {}
                    }
                }
            }
            when (condition) {
                WeatherCondition.CLOUDS, WeatherCondition.FOG -> {
                    val yBase = if (condition == WeatherCondition.FOG) 0.12f else 0.055f
                    val backSize = w * 0.42f
                    val backPhase = (travelBack + 0.45f) % 1f
                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        renderMode = com.airbnb.lottie.RenderMode.HARDWARE,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(backSize)
                            .offset(x = -backSize + (w + backSize) * backPhase, y = h * (yBase + 0.07f) + h * 0.008f * bob2)
                            .graphicsLayer { alpha = 0.42f },
                    )
                    val frontSize = w * 0.62f
                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        renderMode = com.airbnb.lottie.RenderMode.HARDWARE,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(frontSize)
                            .offset(x = -frontSize + (w + frontSize) * travel, y = h * yBase + h * 0.012f * bob)
                            .graphicsLayer { alpha = 0.88f },
                    )
                }
                WeatherCondition.CLEAR -> {
                    val elem = w * 0.56f
                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        renderMode = com.airbnb.lottie.RenderMode.HARDWARE,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(elem)
                            .offset(x = w - elem * 1.02f + w * 0.012f * sway, y = h * 0.04f + h * 0.010f * bob)
                            .graphicsLayer {
                                scaleX = breathe
                                scaleY = breathe
                                alpha = 0.92f
                            },
                    )
                }
                else -> {
                    val elem = w * 0.52f
                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        renderMode = com.airbnb.lottie.RenderMode.HARDWARE,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(elem)
                            .offset(x = w - elem * 1.06f + w * 0.025f * sway, y = h * 0.05f + h * 0.012f * bob)
                            .graphicsLayer { alpha = 0.92f },
                    )
                }
            }
        }
    } else {
        WeatherBackgroundIllustrated(condition, isDay, modifier)
    }
}

private fun key(condition: WeatherCondition) = when (condition) {
    WeatherCondition.CLEAR -> "clear"
    WeatherCondition.CLOUDS -> "clouds"
    WeatherCondition.RAIN -> "rain"
    WeatherCondition.SNOW -> "snow"
    WeatherCondition.THUNDER -> "thunder"
    WeatherCondition.FOG -> "fog"
}

private fun weatherAssetName(condition: WeatherCondition, isDay: Boolean): String =
    "weather/${key(condition)}_${if (isDay) "day" else "night"}.json"

/** Lottie 가 없을 때 캔버스에 직접 그리는 배경. */
@Composable
private fun WeatherBackgroundIllustrated(
    condition: WeatherCondition,
    isDay: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "weather")
    val fall by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart),
        label = "fall",
    )
    val drift by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    val pulse by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(3200), RepeatMode.Reverse),
        label = "pulse",
    )
    val flash by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "flash",
    )

    val rng = remember(condition) { Random(7) }
    val drops = remember(condition) {
        val n = if (condition == WeatherCondition.SNOW) 48 else 80
        if (condition in listOf(WeatherCondition.RAIN, WeatherCondition.THUNDER, WeatherCondition.SNOW)) {
            List(n) { Triple(rng.nextFloat(), rng.nextFloat(), 0.45f + rng.nextFloat()) }
        } else {
            emptyList()
        }
    }
    // 구름: (시작 x, y, 크기, 깊이).
    val clouds = remember(condition) {
        List(4) { i -> listOf(rng.nextFloat(), 0.12f + rng.nextFloat() * 0.5f, 0.7f + rng.nextFloat() * 0.7f, if (i < 2) 0.5f else 1f) }
    }
    val stars = remember(condition) { List(46) { Triple(rng.nextFloat(), rng.nextFloat() * 0.8f, rng.nextFloat()) } }
    val hour = remember { LocalTime.now().hour }

    Box(modifier.background(Brush.verticalGradient(gradientForWeather(condition, isDay, hour)))) {
        Canvas(Modifier.fillMaxSize()) {
            when (condition) {
                WeatherCondition.CLEAR -> if (isDay) {
                    drawSun(pulse, drift)
                } else {
                    drawStars(stars, pulse)
                    drawMoon()
                }
                WeatherCondition.CLOUDS -> {
                    if (isDay) drawSun(pulse * 0.5f, drift, dim = true)
                    drawClouds(clouds, drift)
                }
                WeatherCondition.FOG -> drawFog(drift)
                WeatherCondition.RAIN -> {
                    drawClouds(clouds, drift, alpha = 0.16f)
                    drawRain(drops, fall)
                }
                WeatherCondition.SNOW -> drawSnow(drops, fall, drift)
                WeatherCondition.THUNDER -> {
                    drawClouds(clouds, drift, alpha = 0.14f)
                    drawRain(drops, fall)
                    drawFlash(flash)
                }
            }
            drawVignette()
        }
    }
}

/**
 * 그 날씨의 대표 하늘색.
 *
 * @param condition 날씨
 * @param isDay 낮이면 true
 * @param hour 시각(0~23)
 */
fun weatherSkyColor(condition: WeatherCondition, isDay: Boolean, hour: Int = LocalTime.now().hour): Color =
    gradientForWeather(condition, isDay, hour).first()

private fun gradientForWeather(condition: WeatherCondition, isDay: Boolean, hour: Int): List<Color> {
    val dawn = isDay && hour in 5..7
    val sunset = isDay && hour in 18..20
    return when (condition) {
        WeatherCondition.CLEAR -> when {
            !isDay -> listOf(Color(0xFF060B1F), Color(0xFF14213F), Color(0xFF2A3A66)) // night
            dawn -> listOf(Color(0xFF3A4A8C), Color(0xFFE89A6B), Color(0xFFFBD9B0)) // dawn
            sunset -> listOf(Color(0xFF2A2A55), Color(0xFFB5476A), Color(0xFFF0894E)) // sunset
            else -> listOf(Color(0xFF2E6FD6), Color(0xFF5B9BE8), Color(0xFFBFE3FF)) // day
        }
        WeatherCondition.CLOUDS -> when {
            !isDay -> listOf(Color(0xFF1E2740), Color(0xFF2C3650), Color(0xFF3C4A66))
            sunset || dawn -> listOf(Color(0xFF6B5C7E), Color(0xFFB08AA6), Color(0xFFE0C9CE))
            else -> listOf(Color(0xFF6E86A6), Color(0xFF93A7C0), Color(0xFFC5D2E0))
        }
        WeatherCondition.RAIN -> if (isDay) {
            listOf(Color(0xFF3C4555), Color(0xFF525C6E), Color(0xFF6E7888))
        } else {
            listOf(Color(0xFF20283A), Color(0xFF2E3850), Color(0xFF434E66))
        }
        WeatherCondition.SNOW -> if (isDay) {
            listOf(Color(0xFF7E96B0), Color(0xFFA9BFD6), Color(0xFFDCE8F2))
        } else {
            listOf(Color(0xFF2C374C), Color(0xFF3E4A62), Color(0xFF566178))
        }
        WeatherCondition.THUNDER -> listOf(Color(0xFF161A28), Color(0xFF2A3044), Color(0xFF444C60))
        WeatherCondition.FOG -> if (isDay) {
            listOf(Color(0xFF8A949F), Color(0xFFA7B0BA), Color(0xFFCBD2D8))
        } else {
            listOf(Color(0xFF333A45), Color(0xFF49515D), Color(0xFF646C78))
        }
    }
}

private fun DrawScope.drawVignette() {
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.20f)),
            center = Offset(size.width * 0.5f, size.height * 0.42f),
            radius = size.maxDimension * 0.78f,
        ),
    )
}

private fun DrawScope.drawSun(pulse: Float, drift: Float, dim: Boolean = false) {
    val center = Offset(size.width * 0.8f, size.height * 0.26f)
    val r = size.minDimension * 0.13f
    val k = if (dim) 0.5f else 1f
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFF3C4).copy(alpha = 0.55f * k), Color.Transparent), center = center, radius = r * 3.2f),
        radius = r * 3.2f,
        center = center,
    )
    if (!dim) {
        val rays = 12
        for (i in 0 until rays) {
            val a = (i.toFloat() / rays) * 2f * PI.toFloat() + drift * 2f * PI.toFloat()
            val inner = r * 1.5f
            val outer = r * (2.2f + 0.3f * pulse)
            drawLine(
                Color(0xFFFFE9A8).copy(alpha = 0.22f),
                start = Offset(center.x + cos(a) * inner, center.y + sin(a) * inner),
                end = Offset(center.x + cos(a) * outer, center.y + sin(a) * outer),
                strokeWidth = 3f,
            )
        }
    }
    drawCircle(Color(0xFFFFE08A).copy(alpha = 0.95f * k), radius = r * (0.95f + 0.05f * pulse), center = center)
}

private fun DrawScope.drawMoon() {
    val center = Offset(size.width * 0.78f, size.height * 0.24f)
    val r = size.minDimension * 0.1f
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFCBD6F0).copy(alpha = 0.4f), Color.Transparent), center = center, radius = r * 3f),
        radius = r * 3f,
        center = center,
    )
    drawCircle(Color(0xFFE8EEFB).copy(alpha = 0.92f), radius = r, center = center)
    drawCircle(Color(0xFF14213F), radius = r * 0.92f, center = Offset(center.x + r * 0.42f, center.y - r * 0.12f))
}

private fun DrawScope.drawStars(stars: List<Triple<Float, Float, Float>>, pulse: Float) {
    stars.forEachIndexed { i, (fx, fy, seed) ->
        val tw = if (i % 2 == 0) pulse else 1f - pulse
        val a = 0.2f + 0.7f * (0.3f + 0.7f * seed) * tw
        val cx = fx * size.width
        val cy = fy * size.height
        drawCircle(Color.White.copy(alpha = a * 0.4f), radius = (1.6f + 2.4f * seed), center = Offset(cx, cy))
        drawCircle(Color.White.copy(alpha = a), radius = (0.7f + 1.3f * seed), center = Offset(cx, cy))
    }
}

private fun DrawScope.drawClouds(clouds: List<List<Float>>, drift: Float, alpha: Float = 0.24f) {
    clouds.forEach { (startX, y, scale, depth) ->
        val speed = 0.7f + depth * 0.6f
        val baseX = (((startX + drift * speed) % 1.4f) - 0.2f) * size.width
        val cy = y * size.height
        val cw = size.width * 0.42f * scale
        val ch = cw * 0.5f
        val a = alpha * (0.6f + 0.4f * depth)
        drawOval(Color.White.copy(alpha = a * 0.7f), topLeft = Offset(baseX, cy), size = Size(cw, ch))
        drawOval(Color.White.copy(alpha = a), topLeft = Offset(baseX + cw * 0.22f, cy - ch * 0.32f), size = Size(cw * 0.66f, ch * 0.95f))
        drawOval(Color.White.copy(alpha = a * 0.85f), topLeft = Offset(baseX + cw * 0.5f, cy - ch * 0.05f), size = Size(cw * 0.5f, ch * 0.8f))
    }
}

private fun DrawScope.drawFog(drift: Float) {
    drawRect(Color.White.copy(alpha = 0.06f))
    for (i in 0..4) {
        val y = (0.14f + 0.18f * i) * size.height
        val off = (((drift + i * 0.2f) % 1f) - 0.5f) * size.width * 0.5f
        drawOval(
            Color.White.copy(alpha = 0.09f),
            topLeft = Offset(off - size.width * 0.25f, y),
            size = Size(size.width * 1.5f, size.height * 0.2f),
        )
    }
}

private fun DrawScope.drawRain(drops: List<Triple<Float, Float, Float>>, fall: Float) {
    val len = size.height * 0.055f
    drops.forEach { (x, y0, speed) ->
        val y = ((y0 + fall * (1.4f * speed)) % 1f) * size.height
        val px = x * size.width
        drawLine(
            Color.White.copy(alpha = 0.16f + 0.18f * speed),
            start = Offset(px, y),
            end = Offset(px - len * 0.22f, y + len),
            strokeWidth = 1.4f + speed,
        )
    }
}

private fun DrawScope.drawSnow(drops: List<Triple<Float, Float, Float>>, fall: Float, drift: Float) {
    drops.forEach { (x, y0, speed) ->
        val y = ((y0 + fall * (0.6f * speed)) % 1f) * size.height
        val px = x * size.width + sin((y0 + drift) * 2f * PI.toFloat()) * 14f
        drawCircle(Color.White.copy(alpha = 0.55f + 0.35f * speed), radius = 1.6f + 2.6f * speed, center = Offset(px, y))
    }
}

private fun DrawScope.drawFlash(flash: Float) {
    val a = if (flash > 0.9f) (flash - 0.9f) / 0.1f * 0.5f else 0f
    if (a > 0f) drawRect(Color.White.copy(alpha = a))
}
