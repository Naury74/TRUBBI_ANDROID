package com.naury.trubbi.feature.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.TrubbiConfirmDialog
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 인증 화면 공통 장식 — 단계 골격, 소셜 로그인 줄, 진행·오류 표시.
 */
/**
 * 입력 단계 하나를 그리는 뼈대.
 *
 * 가입 폼을 한 화면에 모두 배치하지 않고 단계당 입력칸 하나만 노출한다.
 */
@Composable
internal fun StepScaffold(
    flowTitle: String,
    stepText: String,
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    primaryText: String,
    primaryBusy: Boolean,
    onPrimary: () -> Unit,
    field: @Composable () -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Icon(
                    AppIcons.ArrowBackIos,
                    contentDescription = stringResource(R.string.action_back),
                    tint = OnDark,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(percent = 50)).pressable(onClick = onBack).padding(4.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            // 현재 과정(로그인/회원가입) + 진행도 칩.
            Text(flowTitle, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = OnDark)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(percent = 50)).background(OnDark.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(stepText, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = OnDark)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = OnDark)
        subtitle?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = OnDarkDim, maxLines = 1)
        }
        Spacer(Modifier.height(22.dp))
        field()
        Spacer(Modifier.height(18.dp))
        PillButton(primaryText, onPrimary, Modifier.fillMaxWidth(), loading = primaryBusy, enabled = !primaryBusy)
        footer?.invoke()
    }
}

@Composable
internal fun SwitchPrompt(question: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(question, style = MaterialTheme.typography.bodyMedium, color = OnDarkDim)
        Spacer(Modifier.width(6.dp))
        Text(
            action,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = OnDark,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable(onClick = onAction).padding(4.dp),
        )
    }
}

@Composable
internal fun SocialDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(OnDark.copy(alpha = 0.25f)))
        Text(
            stringResource(R.string.account_social_sign_in),
            style = MaterialTheme.typography.labelMedium,
            color = OnDarkDim,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(OnDark.copy(alpha = 0.25f)))
    }
}

@Composable
internal fun SocialRow(onGoogle: () -> Unit, onKakao: () -> Unit, onNaver: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)) {
        BrandCircle(R.drawable.ic_brand_kakao, stringResource(R.string.account_kakao_login), border = false, onClick = onKakao)
        BrandCircle(R.drawable.ic_brand_naver, stringResource(R.string.account_naver_login), border = false, onClick = onNaver)
        BrandCircle(R.drawable.ic_brand_google, stringResource(R.string.account_google_login), border = true, onClick = onGoogle)
    }
}

@Composable
private fun BrandCircle(resId: Int, desc: String, border: Boolean, onClick: () -> Unit) {
    val c = LocalTrubbiColors.current
    val shape = RoundedCornerShape(percent = 50)
    Image(
        painter = painterResource(resId),
        contentDescription = desc,
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        modifier = Modifier
            .size(56.dp)
            .clip(shape)
            .then(if (border) Modifier.border(1.dp, c.separator, shape) else Modifier)
            .pressable(onClick = onClick),
    )
}

/** Full-screen blocking progress overlay (stringResource(R.string.auth_signing_in) 등). */
@Composable
internal fun AuthProgressHost(viewModel: AccountViewModel) {
    val label by viewModel.progress.collectAsState()
    label?.let { text ->
        Box(
            Modifier.fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
                .pressable(enabled = true) { /* consume taps while busy */ },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 30.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                com.naury.kinetica.ui.KLoadingIndicator(size = 38.dp)
                Spacer(Modifier.height(16.dp))
                Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.account_please_wait), style = MaterialTheme.typography.bodySmall, color = LocalTrubbiColors.current.secondaryLabel)
            }
        }
    }
}

/** 뷰모델이 올린 인증 결과·오류 팝업을 띄운다. */
@Composable
internal fun AuthDialogHost(viewModel: AccountViewModel) {
    val dlg by viewModel.dialog.collectAsState()
    dlg?.let { d ->
        if (d.dismissText != null && d.onConfirm != null) {
            TrubbiConfirmDialog(
                title = d.title,
                message = d.message,
                onDismiss = { viewModel.dismissDialog() },
                onConfirm = {
                    viewModel.dismissDialog()
                    d.onConfirm.invoke()
                },
                confirmText = d.confirmText ?: stringResource(R.string.action_confirm),
                dismissText = d.dismissText,
                icon = if (d.isError) AppIcons.Warning else AppIcons.Check,
            )
        } else {
            com.naury.trubbi.ui.designsystem.TrubbiStatusDialog(
                title = d.title,
                message = d.message,
                icon = if (d.isError) AppIcons.Warning else AppIcons.Check,
                buttonText = d.confirmText ?: stringResource(R.string.action_confirm),
                onDismiss = { viewModel.dismissDialog() },
            )
        }
    }
}

// ── 로그인 후 계정 정보

@Composable
internal fun LaunchedEffectMessage(message: String?, onShown: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            onShown()
        }
    }
}

internal val OnDark = androidx.compose.ui.graphics.Color.White

internal val OnDarkDim = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f)
