package com.naury.trubbi.feature.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.ml.ReceiptParser
import com.naury.trubbi.domain.model.ExpenseCategory
import com.naury.trubbi.domain.model.IssueType
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.TrubbiSheet
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 비용 입력 시트.
 *
 * 영수증이 있으면 OCR 로 추출한 금액·상호·분류가 초기값으로 채워진다.
 *
 * @param prefill 영수증에서 읽어 낸 값. 없으면 빈 폼으로 시작한다
 * @param receiptRecordId 근거가 된 영수증 기록 id
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseSheet(
    prefill: ReceiptParser.Parsed? = null,
    receiptRecordId: String? = null,
    onSave: (category: ExpenseCategory, amount: Long, vendor: String?, memo: String?, receiptRecordId: String?) -> Unit,
    onCaptureReceipt: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val colors = LocalTrubbiColors.current
    var category by remember { mutableStateOf(prefill?.category ?: ExpenseCategory.FUEL) }
    var amountText by remember { mutableStateOf(prefill?.amount?.toString().orEmpty()) }
    var vendor by remember { mutableStateOf(prefill?.vendor.orEmpty()) }
    var memo by remember { mutableStateOf("") }
    val amount = amountText.toLongOrNull() ?: 0L

    TrubbiSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 22.dp).padding(bottom = 28.dp).imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.expense_add_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (prefill != null && (prefill.amount != null || prefill.vendor != null)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (colors.isDark) 0.28f else 0.14f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            stringResource(R.string.expense_receipt_detected),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExpenseCategory.entries.forEach { cat ->
                    val selected = cat == category
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            )
                            .pressable { category = cat }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                    ) {
                        Text(
                            cat.label(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // 숫자 키패드로 받고, 옆에 천 단위로 끊은 값을 보여 줘 자릿수 실수를 줄인다.
            SheetField(
                placeholder = stringResource(R.string.expense_amount_hint),
                value = amountText,
                onValue = { input -> amountText = input.filter { it.isDigit() }.take(9) },
                keyboardType = KeyboardType.Number,
                trailing = if (amount > 0) stringResource(R.string.amount_krw, "%,d".format(amount)) else null,
            )
            Spacer(Modifier.height(10.dp))
            SheetField(placeholder = stringResource(R.string.expense_vendor_hint), value = vendor, onValue = { vendor = it })
            Spacer(Modifier.height(10.dp))
            SheetField(placeholder = stringResource(R.string.expense_memo_hint), value = memo, onValue = { memo = it })

            Spacer(Modifier.height(18.dp))
            if (onCaptureReceipt != null) {
                PillButton(
                    text = stringResource(R.string.expense_capture_receipt),
                    icon = AppIcons.PhotoCamera,
                    onClick = onCaptureReceipt,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
            }
            PillButton(
                text = if (amount > 0) {
                    stringResource(R.string.expense_save_amount, "%,d".format(amount))
                } else {
                    stringResource(R.string.action_save)
                },
                icon = AppIcons.Wallet,
                onClick = {
                    if (amount > 0) {
                        onSave(category, amount, vendor.ifBlank { null }, memo.ifBlank { null }, receiptRecordId)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 이슈 입력 시트 — 종류 칩과 상황 설명.
 *
 * 발생 시각과 위치는 저장 시점에 리포지토리가 알아서 붙인다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IssueSheet(
    onSave: (IssueType, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalTrubbiColors.current
    var type by remember { mutableStateOf(IssueType.DAMAGE) }
    var description by remember { mutableStateOf("") }

    TrubbiSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 22.dp).padding(bottom = 28.dp).imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.issue_add_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.issue_auto_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryLabel,
            )
            Spacer(Modifier.height(14.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IssueType.entries.forEach { t ->
                    val selected = t == type
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                if (selected) colors.red else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .pressable { type = t }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                    ) {
                        Text(
                            t.label(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (colors.isDark) 0.55f else 0.8f))
                    .padding(14.dp)
                    .heightIn(min = 84.dp),
            ) {
                if (description.isEmpty()) {
                    Text(
                        stringResource(R.string.issue_description_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryLabel,
                    )
                }
                BasicTextField(
                    value = description,
                    onValueChange = { description = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(18.dp))
            PillButton(
                text = stringResource(R.string.issue_add_title),
                icon = AppIcons.Warning,
                onClick = { if (description.isNotBlank()) onSave(type, description.trim()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SheetField(
    placeholder: String,
    value: String,
    onValue: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: String? = null,
) {
    val colors = LocalTrubbiColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (colors.isDark) 0.55f else 0.8f))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = colors.secondaryLabel)
            }
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
