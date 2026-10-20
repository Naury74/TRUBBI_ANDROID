package com.naury.trubbi.feature.ads

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.naury.kinetica.ui.KSurface
import com.naury.trubbi.R
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 기록 카드와 같은 모양의 네이티브 광고 한 장. 광고를 띄울 수 없거나(동의 전·여정 중·ID 없음) 아직 못 받았으면
 * 자리를 차지하지 않는다.
 *
 * "광고" 표시와 AdChoices 를 늘 보이게 둔다(AdMob 네이티브 광고 정책).
 */
@Composable
fun NativeRecordAd(modifier: Modifier = Modifier) {
    if (!LocalAdsAllowed.current) return
    val unit = LocalAdsController.current?.nativeUnit?.takeIf { it.isNotBlank() } ?: return
    val context = LocalContext.current
    var ad by remember { mutableStateOf<NativeAd?>(null) }
    DisposableEffect(unit) {
        var disposed = false
        AdLoader.Builder(context, unit)
            .forNativeAd { loaded -> if (disposed) loaded.destroy() else ad = loaded }
            .withNativeAdOptions(NativeAdOptions.Builder().setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT).build())
            .build()
            .loadAd(AdRequest.Builder().build())
        onDispose {
            disposed = true
            ad?.destroy()
            ad = null
        }
    }
    val loaded = ad ?: return
    val style = AdStyle(
        text = MaterialTheme.colorScheme.onSurface.toArgb(),
        muted = LocalTrubbiColors.current.secondaryLabel.toArgb(),
        accent = MaterialTheme.colorScheme.primary.toArgb(),
        onAccent = MaterialTheme.colorScheme.onPrimary.toArgb(),
        badge = stringResource(R.string.ad_label),
    )
    KSurface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        AndroidView(
            factory = { buildNativeAdView(it, style) },
            update = { bindNativeAd(it, loaded, style) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 목록 끝에 두는 배너. 화면 폭에 맞춘 적응형 크기로 받는다. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    if (!LocalAdsAllowed.current) return
    val unit = LocalAdsController.current?.bannerUnit?.takeIf { it.isNotBlank() } ?: return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt()
        AndroidView(
            factory = { ctx ->
                AdView(ctx).apply {
                    adUnitId = unit
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                    loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private data class AdStyle(val text: Int, val muted: Int, val accent: Int, val onAccent: Int, val badge: String)

private fun Context.dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

/** 아이콘·제목·"광고"·광고주 / 이미지 / 설명 / 버튼 순서의 네이티브 광고 틀. */
private fun buildNativeAdView(context: Context, style: AdStyle): NativeAdView {
    val root = NativeAdView(context)
    val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
    }
    val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val icon = ImageView(context).apply { id = R.id.ad_icon }
    header.addView(icon, LinearLayout.LayoutParams(context.dp(40), context.dp(40)).apply { marginEnd = context.dp(12) })
    val titles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val headline = TextView(context).apply {
        id = R.id.ad_headline
        setTextColor(style.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    val advertiser = TextView(context).apply {
        id = R.id.ad_advertiser
        setTextColor(style.muted)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        maxLines = 1
    }
    titles.addView(headline)
    titles.addView(advertiser)
    header.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    column.addView(header)

    val media = MediaView(context).apply { id = R.id.ad_media }
    column.addView(media, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(180)).apply { topMargin = context.dp(12) })

    val body = TextView(context).apply {
        id = R.id.ad_body
        setTextColor(style.muted)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }
    column.addView(
        body,
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin =
                context.dp(10)
        },
    )

    val cta = Button(context).apply {
        id = R.id.ad_cta
        isAllCaps = false
        setTextColor(style.onAccent)
        background = GradientDrawable().apply {
            cornerRadius = context.dp(24).toFloat()
            color = ColorStateList.valueOf(style.accent)
        }
    }
    column.addView(cta, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(48)).apply { topMargin = context.dp(12) })

    root.addView(column)
    root.headlineView = headline
    root.advertiserView = advertiser
    root.iconView = icon
    root.mediaView = media
    root.bodyView = body
    root.callToActionView = cta
    return root
}

private fun bindNativeAd(view: NativeAdView, ad: NativeAd, style: AdStyle) {
    (view.headlineView as TextView).text = ad.headline
    // 광고주 이름 앞에 "광고"를 붙여, 기록 카드와 헷갈리지 않게 한다.
    (view.advertiserView as TextView).text = listOfNotNull(style.badge, ad.advertiser).joinToString(" · ")
    (view.bodyView as TextView).apply {
        text = ad.body
        visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    (view.iconView as ImageView).apply {
        setImageDrawable(ad.icon?.drawable)
        visibility = if (ad.icon == null) View.GONE else View.VISIBLE
    }
    view.mediaView?.mediaContent = ad.mediaContent
    (view.callToActionView as Button).apply {
        text = ad.callToAction
        visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    view.setNativeAd(ad)
}
