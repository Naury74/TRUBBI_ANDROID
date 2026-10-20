package com.naury.trubbi.ui.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R

private const val BRAND_NAME = "TRUBBI"

/** 워드마크 벡터의 가로:세로 비율(tools/brand/make_brand.py 가 만든 viewport). */
private const val WORDMARK_RATIO = 3971f / 756f

/**
 * 세로로 쌓은 로고 — 위에 마크(파란 배지), 아래에 TRUBBI 글자. 스플래시·소개·로그인 화면에 쓴다.
 *
 * @param modifier 너비를 정해 넘긴다. 마크는 너비의 40%, 글자는 너비 전체를 쓴다
 * @param wordmarkColor 글자 색. 어두운 바탕이면 흰색
 */
@Composable
fun TrubbiLogo(modifier: Modifier = Modifier, wordmarkColor: Color = MaterialTheme.colorScheme.onBackground) {
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = BRAND_NAME },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Image(painterResource(R.drawable.brand_mark), contentDescription = null, modifier = Modifier.fillMaxWidth(0.4f).aspectRatio(1f))
        Image(
            painterResource(R.drawable.brand_wordmark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(wordmarkColor),
            modifier = Modifier.fillMaxWidth().aspectRatio(WORDMARK_RATIO),
        )
    }
}

/**
 * 가로 로고 — 작은 마크와 글자. 화면 머리(날씨 배너 등)에 쓴다.
 *
 * @param height 글자 높이. 마크는 이보다 조금 크게 그린다
 * @param color 글자 색
 */
@Composable
fun TrubbiLogoInline(modifier: Modifier = Modifier, height: Dp = 18.dp, color: Color = Color.White) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = BRAND_NAME },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(painterResource(R.drawable.brand_mark), contentDescription = null, modifier = Modifier.size(height * 1.5f))
        Image(
            painterResource(R.drawable.brand_wordmark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier.height(height).aspectRatio(WORDMARK_RATIO),
        )
    }
}
