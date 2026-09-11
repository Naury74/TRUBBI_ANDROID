package com.naury.trubbi.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 약관 동의 단계와 약관 본문.
 *
 * 본문을 리소스가 아니라 상수로 두는 이유는 개정 시 문구 전체를 한 번에 갈아 끼우기 위해서다.
 */
@Composable
internal fun TermsStep(
    agreeTerms: Boolean,
    agreePrivacy: Boolean,
    agreeLocation: Boolean,
    agreeAge: Boolean,
    agreeMarketing: Boolean,
    highlightMissing: Boolean,
    onTerms: (Boolean) -> Unit,
    onPrivacy: (Boolean) -> Unit,
    onLocation: (Boolean) -> Unit,
    onAge: (Boolean) -> Unit,
    onMarketing: (Boolean) -> Unit,
    onAll: (Boolean) -> Unit,
    onView: (String, String) -> Unit,
) {
    val c = LocalTrubbiColors.current
    val allChecked = agreeTerms && agreePrivacy && agreeLocation && agreeAge && agreeMarketing
    // onView 는 컴포저블이 아닌 콜백이라 제목 문구를 미리 읽어 둔다.
    val titleTerms = stringResource(R.string.account_terms_service_short)
    val titlePrivacy = stringResource(R.string.account_terms_privacy_short)
    val titleLocation = stringResource(R.string.account_terms_location_short)
    val titleMarketing = stringResource(R.string.account_terms_marketing_short)
    Column {
        ConsentRow(stringResource(R.string.account_agree_all), allChecked, big = true, missing = false, onCheck = { onAll(!allChecked) }, onView = null)
        Box(Modifier.fillMaxWidth().height(1.dp).background(OnDark.copy(alpha = 0.2f)).padding(vertical = 2.dp))
        Spacer(Modifier.height(6.dp))
        ConsentRow(stringResource(R.string.account_terms_service), agreeTerms, missing = highlightMissing && !agreeTerms, onCheck = {
            onTerms(!agreeTerms)
        }, onView = { onView(titleTerms, TERMS_TEXT) })
        ConsentRow(stringResource(R.string.account_terms_privacy), agreePrivacy, missing = highlightMissing && !agreePrivacy, onCheck = {
            onPrivacy(!agreePrivacy)
        }, onView = { onView(titlePrivacy, PRIVACY_TEXT) })
        ConsentRow(stringResource(R.string.account_terms_location), agreeLocation, missing = highlightMissing && !agreeLocation, onCheck = {
            onLocation(!agreeLocation)
        }, onView = { onView(titleLocation, LOCATION_TEXT) })
        ConsentRow(stringResource(R.string.account_terms_age), agreeAge, missing = highlightMissing && !agreeAge, onCheck = { onAge(!agreeAge) }, onView = null)
        ConsentRow(stringResource(R.string.account_terms_marketing), agreeMarketing, missing = false, onCheck = {
            onMarketing(!agreeMarketing)
        }, onView = { onView(titleMarketing, MARKETING_TEXT) })
        if (highlightMissing) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.account_terms_required_note),
                style = MaterialTheme.typography.labelMedium,
                color = c.red,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * 약관 전문을 스크롤로 보여 주는 팝업.
 *
 * @param title 약관 제목
 * @param body 약관 본문
 * @param onDismiss 닫을 때
 */
@Composable
internal fun TermsViewerDialog(title: String, body: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    AppIcons.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = LocalTrubbiColors.current.secondaryLabel,
                    modifier = Modifier.size(26.dp).clip(RoundedCornerShape(percent = 50)).pressable(onClick = onDismiss).padding(3.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(16.dp))
            PillButton(stringResource(R.string.action_confirm), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ConsentRow(label: String, checked: Boolean, missing: Boolean, big: Boolean = false, onCheck: () -> Unit, onView: (() -> Unit)?) {
    val c = LocalTrubbiColors.current
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onCheck).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (checked) AppIcons.CheckCircle else AppIcons.RadioButtonUnchecked,
            contentDescription = null,
            tint = when {
                checked -> MaterialTheme.colorScheme.primary
                missing -> c.red
                else -> OnDarkDim
            },
            modifier = Modifier.size(if (big) 24.dp else 22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = (if (big) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium).copy(
                fontWeight = if (big) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (missing) c.red else OnDark,
            modifier = Modifier.weight(1f),
        )
        if (onView != null) {
            Text(
                stringResource(R.string.action_view),
                style = MaterialTheme.typography.labelMedium,
                color = OnDarkDim,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).pressable(onClick = onView).padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

// ── 약관 본문 (TRUBBI 표준 약관 — 사업자 정보/연락처는 출시 전 확정 값으로 채워주세요)
// 약관 원문은 국내 법령 기준으로 작성된 문서라 번역본이 준비되기 전까지 한국어로만 둔다.
// (문구를 임의로 옮기면 법적 효력이 달라질 수 있어 리소스로 빼지 않았다.)
private const val TERMS_TEXT = """TRUBBI 서비스 이용약관 (시행일 2026.06.09)

제1조(목적)
이 약관은 TRUBBI(이하 “회사”)가 제공하는 운송 업무 기록·증빙 관리·클라우드 백업 등 일체의 모바일 서비스(이하 “서비스”)의 이용과 관련하여 회사와 회원의 권리·의무 및 책임사항을 규정함을 목적으로 합니다.

제2조(정의)
1. “회원”이란 이 약관에 동의하고 회사와 이용계약을 체결한 자를 말합니다.
2. “계정”이란 회원 식별과 서비스 이용을 위해 회원이 설정한 이메일 및 인증 수단을 말합니다.
3. “콘텐츠”란 회원이 서비스에 입력·업로드한 운행 기록, 사진, 영수증, 메모, 위치정보 등을 말합니다.

제3조(약관의 효력 및 변경)
1. 이 약관은 서비스 화면에 게시함으로써 효력이 발생합니다.
2. 회사는 관계 법령을 위반하지 않는 범위에서 약관을 변경할 수 있으며, 변경 시 적용일자 및 사유를 명시하여 최소 7일 전(회원에게 불리하거나 중대한 변경은 30일 전)부터 공지합니다.

제4조(이용계약의 성립)
이용계약은 회원이 약관에 동의하고 가입 신청을 한 후 회사가 이를 승낙함으로써 성립합니다. 회사는 타인 명의 도용, 허위 정보 기재, 만 14세 미만 등의 경우 승낙을 거부하거나 사후 해지할 수 있습니다.

제5조(서비스의 제공 및 변경)
1. 회사는 여정 기록, 상·하차 및 증빙 관리, 비용 정산, 리포트 생성, 클라우드 백업·복원 등을 제공합니다.
2. 회사는 운영상·기술상 필요에 따라 서비스 내용을 변경할 수 있으며, 중요한 변경은 사전 공지합니다.

제6조(서비스의 중단)
회사는 시스템 점검·교체·고장, 통신 두절, 천재지변 등 불가항력이 있는 경우 서비스 제공을 일시 중단할 수 있습니다.

제7조(회원의 의무)
1. 회원은 계정 정보를 스스로 관리할 책임이 있으며, 이를 제3자에게 이용하게 해서는 안 됩니다.
2. 회원은 관계 법령, 이 약관, 이용안내 및 회사가 통지하는 사항을 준수해야 하며, 타인의 권리를 침해하거나 서비스 운영을 방해하는 행위를 해서는 안 됩니다.

제8조(콘텐츠의 권리)
회원이 입력·업로드한 콘텐츠의 권리는 회원에게 있습니다. 회사는 서비스 제공·백업·복원에 필요한 범위에서만 콘텐츠를 저장·처리하며, 이를 회원의 동의 없이 외부에 공개하지 않습니다.

제9조(계약 해지 및 이용 제한)
1. 회원은 언제든지 앱 내 ‘회원탈퇴’를 통해 이용계약을 해지할 수 있습니다.
2. 회사는 회원이 이 약관을 위반한 경우 사전 통지 후 이용을 제한하거나 계약을 해지할 수 있습니다.

제10조(책임의 한계)
회사는 무료로 제공되는 서비스 이용과 관련하여 관계 법령에 특별한 규정이 없는 한 책임을 지지 않습니다. 회원의 기기 사정, 회원이 입력한 정보의 정확성 등으로 발생한 손해에 대해 회사는 고의·중과실이 없는 한 책임을 지지 않습니다.

제11조(준거법 및 관할)
이 약관은 대한민국 법령에 따라 해석되며, 서비스 이용과 관련한 분쟁은 민사소송법상 관할 법원을 제1심 관할로 합니다.

부칙: 이 약관은 2026.06.09부터 시행합니다."""

private const val PRIVACY_TEXT = """개인정보 수집·이용 동의 (개인정보처리방침 요약, 시행일 2026.06.09)

1. 수집하는 개인정보 항목
 - 필수: 이메일 주소, 비밀번호(암호화 저장), 이름(닉네임)
 - 서비스 이용 과정에서 생성: 위치정보, 촬영 사진·영수증 이미지, 운행/여정 기록, 비용 내역, 기기·앱 식별 정보, 접속 로그

2. 수집·이용 목적
 - 회원 식별 및 로그인, 이메일 인증
 - 운송 업무 기록의 저장·표시 및 클라우드 백업·복원
 - 증빙 관리, 비용 정산, 리포트 생성 등 서비스 제공
 - 서비스 개선, 오류 분석 및 고객 문의 대응

3. 보유 및 이용 기간
 - 회원 탈퇴 시 지체 없이 파기합니다. 다만 관계 법령에 따라 보존이 필요한 경우 해당 기간 동안 보관합니다.

4. 처리 위탁 및 국외 이전
 - 회사는 안정적인 인증·저장·백업을 위해 Google LLC(Firebase Authentication, Cloud Firestore, Cloud Storage)에 데이터 처리를 위탁하며, 데이터는 Google의 데이터센터(국외 포함)에 저장될 수 있습니다.

5. 동의를 거부할 권리
 - 회원은 필수 항목 수집·이용 동의를 거부할 수 있으나, 이 경우 회원가입 및 서비스 이용이 제한됩니다.

6. 정보주체의 권리
 - 회원은 자신의 개인정보 열람·정정·삭제·처리정지를 요청할 수 있으며, 앱 내 설정 또는 고객센터를 통해 행사할 수 있습니다."""

private const val LOCATION_TEXT = """위치기반서비스 이용약관 (시행일 2026.06.09)

제1조(목적)
이 약관은 회사가 제공하는 위치기반서비스와 관련하여 회사와 개인위치정보주체(회원) 간의 권리·의무를 규정합니다.

제2조(이용 목적)
회사는 다음 목적으로 회원의 위치정보를 이용합니다.
 - 상·하차지 도착 자동 기록 및 도착 알림
 - 여정 경로 기록 및 지도 표시
 - 증빙 사진의 촬영 위치 표기

제3조(개인위치정보의 보유)
회사는 위치기반서비스 제공 목적 달성 시까지 위치정보를 보유하며, 회원 탈퇴 또는 목적 달성 시 지체 없이 파기합니다. 다만 위치정보의 이용·제공 사실 확인자료는 위치정보의 보호 및 이용 등에 관한 법률에 따라 자동 기록·보존됩니다.

제4조(권리의 행사)
회원은 위치정보 수집·이용·제공에 대한 동의의 전부 또는 일부를 철회할 수 있으며, 언제든지 위치정보 수집의 일시 중지를 요구할 수 있습니다. 기기의 위치 권한을 해제하면 위치 기반 기능은 제한됩니다.

제5조(동의의 효과)
이 약관에 동의하지 않아도 회원가입은 가능하나, 도착 기록·경로 기록 등 위치 기반 기능 이용이 제한될 수 있습니다."""

private const val MARKETING_TEXT = """마케팅 정보 수신 동의 (선택)

회사는 동의한 회원에게 신규 기능, 업데이트, 이벤트, 혜택 등의 정보를 이메일 및 앱 푸시 알림으로 발송할 수 있습니다.

 - 본 동의는 선택 사항이며, 동의하지 않아도 서비스 이용에 제한이 없습니다.
 - 동의 후에도 앱 설정 또는 수신 메시지 내 수신거부를 통해 언제든지 철회할 수 있습니다.
 - 야간(21시~익일 08시)에는 광고성 정보를 전송하지 않습니다."""
