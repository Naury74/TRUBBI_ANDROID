package com.naury.trubbi.feature.account

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.data.auth.TrubbiUser
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.TrubbiConfirmDialog
import com.naury.trubbi.ui.designsystem.glass
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 로그인한 뒤 보이는 계정 정보 화면.
 */
@Composable
internal fun AccountInfo(user: TrubbiUser, viewModel: AccountViewModel) {
    val c = LocalTrubbiColors.current
    val meta by viewModel.cloudMeta.collectAsState()
    val backupBusy by viewModel.backupBusy.collectAsState()
    val autoBackup by viewModel.autoBackup.collectAsState()
    val wifiOnly by viewModel.backupWifiOnly.collectAsState()
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA) }
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.updatePhoto(uri) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 프로필 사진 — 탭하면 갤러리에서 변경(Firebase Storage 동기화).
            Box(contentAlignment = Alignment.BottomEnd) {
                com.naury.trubbi.ui.common.ProfileAvatar(
                    photoUri = user.photoUrl,
                    name = user.displayName.orEmpty(),
                    size = 56.dp,
                    onClick = {
                        photoPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                )
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(percent = 50)).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp)) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user.displayName?.ifBlank {
                        stringResource(R.string.profile_no_name)
                    } ?: stringResource(R.string.profile_no_name),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                user.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, maxLines = 1) }
                if (user.providers.isNotEmpty()) {
                    val linked = user.providers.map { providerLabel(it) }.joinToString(", ")
                    Text(
                        stringResource(R.string.account_providers, linked),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.secondaryLabel,
                    )
                }
            }
            Icon(
                AppIcons.Edit,
                contentDescription = stringResource(R.string.account_rename),
                tint = c.secondaryLabel,
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(percent = 50)).pressable { editingName = true }.padding(7.dp),
            )
        }

        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.account_cloud_backup),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                meta?.let { stringResource(R.string.account_last_backup, fmt.format(Date(it.lastBackupAt)), (it.sizeBytes / 1024).toInt()) }
                    ?: stringResource(R.string.account_no_backup_yet),
                style = MaterialTheme.typography.bodySmall,
                color = c.secondaryLabel,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(R.string.account_backup_now), {
                    viewModel.backupNow()
                }, Modifier.weight(1f), icon = AppIcons.Backup, loading = backupBusy, enabled = !backupBusy)
                PillButton(
                    stringResource(R.string.account_restore),
                    {
                        viewModel.restoreNow()
                    },
                    Modifier.weight(
                        1f,
                    ),
                    icon = AppIcons.Restore,
                    enabled = !backupBusy,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(2.dp))
            ToggleRow(stringResource(R.string.account_auto_backup), stringResource(R.string.account_auto_backup_sub), autoBackup) {
                viewModel.setAutoBackup(it)
            }
            ToggleRow(stringResource(R.string.account_wifi_only), stringResource(R.string.account_wifi_only_sub), wifiOnly) { viewModel.setBackupWifiOnly(it) }
        }

        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(4.dp)) {
            RowAction(stringResource(R.string.account_sign_out), AppIcons.ArrowBackIos, c.secondaryLabel) { confirmLogout = true }
            RowAction(stringResource(R.string.account_delete), AppIcons.Delete, c.red) { confirmDelete = true }
        }
    }

    if (editingName) {
        com.naury.trubbi.ui.designsystem.TrubbiInputDialog(
            title = stringResource(R.string.account_rename),
            label = stringResource(R.string.field_name),
            initial = user.displayName.orEmpty(),
            confirmText = stringResource(R.string.action_save),
            onDismiss = { editingName = false },
            onConfirm = { newName ->
                editingName = false
                viewModel.updateName(newName)
            },
        )
    }
    if (confirmLogout) {
        TrubbiConfirmDialog(
            title = stringResource(R.string.account_sign_out),
            message = stringResource(R.string.account_sign_out_message),
            onDismiss = { confirmLogout = false },
            onConfirm = {
                confirmLogout = false
                viewModel.signOut()
            },
            confirmText = stringResource(R.string.account_sign_out),
        )
    }
    if (confirmDelete) {
        TrubbiConfirmDialog(
            title = stringResource(R.string.account_delete),
            message = stringResource(R.string.account_delete_message),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.deleteAccount {}
            },
            confirmText = stringResource(R.string.account_delete_confirm),
            destructive = true,
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalTrubbiColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel)
        }
        com.naury.kinetica.ui.KSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RowAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = tint)
    }
}

/** 로그인 제공자 id 를 사람이 읽는 이름으로. 모르는 값은 그대로 보여 준다. */
@Composable
private fun providerLabel(id: String): String = when {
    id.contains("google") -> stringResource(R.string.provider_google)
    id.contains("password") -> stringResource(R.string.provider_email)
    id.contains("kakao") -> stringResource(R.string.provider_kakao)
    id.contains("naver") -> stringResource(R.string.provider_naver)
    else -> id
}
