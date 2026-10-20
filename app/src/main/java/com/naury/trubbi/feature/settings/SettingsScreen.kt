package com.naury.trubbi.feature.settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.i18n.purposeLabel
import com.naury.trubbi.core.util.DateUtils
import com.naury.trubbi.data.settings.PhotoQuality
import com.naury.trubbi.data.settings.ThemeMode
import com.naury.trubbi.ui.common.ProfileAvatar
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.LocalAppToast
import com.naury.trubbi.ui.designsystem.NavRow
import com.naury.trubbi.ui.designsystem.SectionCard
import com.naury.trubbi.ui.designsystem.SegmentedControl
import com.naury.trubbi.ui.designsystem.TrubbiLargeTitle
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 설정 화면.
 *
 * @param onOpenContacts 연락처로
 * @param onOpenPurposes 용도 관리로
 * @param onOpenAccount 계정·백업으로
 * @param onBack 뒤로 갈 때. 하단 탭으로 열었으면 null(뒤로 버튼 없음)
 * @param contentPadding 시스템 바를 피하기 위한 여백
 * @param viewModel 설정 상태
 */
@Composable
fun SettingsScreen(
    onOpenContacts: () -> Unit,
    onOpenPurposes: () -> Unit,
    onOpenAccount: () -> Unit,
    onBack: (() -> Unit)?,
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ads = com.naury.trubbi.feature.ads.LocalAdsController.current
    val adPrivacyRequired by (ads?.privacyOptionsRequired ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) })
        .collectAsStateWithLifecycle()
    val purposes by viewModel.purposes.collectAsStateWithLifecycle()
    val defaultPurposeLabel = purposeLabel(settings.defaultPurpose)
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val watermarkDefaults by viewModel.watermarkDefaults.collectAsStateWithLifecycle()
    val arrivalRadius by viewModel.arrivalRadius.collectAsStateWithLifecycle()
    val voiceGuide by viewModel.voiceGuide.collectAsStateWithLifecycle()
    val cloudVoice by viewModel.cloudVoice.collectAsStateWithLifecycle()
    val floatingControl by viewModel.floatingControl.collectAsStateWithLifecycle()
    val floatingLauncher by viewModel.floatingLauncher.collectAsStateWithLifecycle()
    val quickNotification by viewModel.quickNotification.collectAsStateWithLifecycle()
    val c = LocalTrubbiColors.current
    val context = LocalContext.current

    val profilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.changeProfilePhoto(uri) }
    var nameField by remember(profile.name) { mutableStateOf(profile.name) }

    val appToast = LocalAppToast.current
    LaunchedEffect(backupStatus) {
        backupStatus?.let { status ->
            appToast?.show(status.message)
            if (!status.inProgress) viewModel.clearMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importBackup) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            // 하단 탭 바(떠 있는 캡슐)에 마지막 항목이 가리지 않게 한다.
            bottom = contentPadding.calculateBottomPadding() + com.naury.trubbi.navigation.TAB_BAR_ROOM,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            TrubbiLargeTitle(
                title = stringResource(R.string.settings_title),
                leading = onBack?.let { back ->
                    {
                        com.naury.trubbi.ui.designsystem.GlassIconButton(AppIcons.ArrowBackIos, back, stringResource(R.string.action_back), size = 40.dp)
                    }
                },
            )
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionCard(title = stringResource(R.string.settings_section_account)) {
                    NavRow(
                        title = stringResource(R.string.settings_sign_in),
                        subtitle = stringResource(R.string.settings_sign_in_sub),
                        leadingIcon = AppIcons.Backup,
                        leadingTint = MaterialTheme.colorScheme.primary,
                        showDivider = false,
                        onClick = onOpenAccount,
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionCard(title = stringResource(R.string.settings_section_profile)) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            ProfileAvatar(
                                photoUri = profile.photoUri,
                                name = profile.name,
                                size = 64.dp,
                                onClick = {
                                    profilePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            )
                            Box(
                                Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    AppIcons.PhotoCamera,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        OutlinedTextField(
                            value = nameField,
                            onValueChange = {
                                nameField = it
                                viewModel.setUserName(it)
                            },
                            label = { Text(stringResource(R.string.field_name)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionCard(title = stringResource(R.string.settings_section_theme)) {
                    Column {
                        NavRow(
                            title = stringResource(R.string.settings_theme_row),
                            leadingIcon = AppIcons.DarkMode,
                            leadingTint = c.secondaryLabel,
                            showChevron = false,
                            showDivider = false,
                            onClick = null,
                        )
                        Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                            SegmentedControl(
                                options = listOf(
                                    stringResource(R.string.theme_system),
                                    stringResource(R.string.theme_light),
                                    stringResource(R.string.theme_dark),
                                ),
                                selectedIndex = when (settings.themeMode) {
                                    ThemeMode.SYSTEM -> 0
                                    ThemeMode.LIGHT -> 1
                                    ThemeMode.DARK -> 2
                                },
                                onSelected = { idx ->
                                    viewModel.setTheme(
                                        when (idx) {
                                            1 -> ThemeMode.LIGHT
                                            2 -> ThemeMode.DARK
                                            else -> ThemeMode.SYSTEM
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionCard(title = stringResource(R.string.settings_section_capture)) {
                    Column {
                        NavRow(
                            title = stringResource(R.string.settings_default_purpose),
                            leadingIcon = AppIcons.AutoAwesome,
                            leadingTint = MaterialTheme.colorScheme.primary,
                            trailingText = defaultPurposeLabel,
                            // 목록에 없던 값이면 indexOf 가 -1 이라 첫 번째부터 시작한다.
                            onClick = {
                                if (purposes.isNotEmpty()) {
                                    val next = purposes[(purposes.indexOf(defaultPurposeLabel) + 1).mod(purposes.size)]
                                    viewModel.setDefaultPurpose(next)
                                }
                            },
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_save_location),
                            icon = AppIcons.LocationOn,
                            tint = c.green,
                            checked = settings.saveLocation,
                            onChange = viewModel::setSaveLocation,
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_autosave),
                            icon = AppIcons.Save,
                            tint = MaterialTheme.colorScheme.primary,
                            checked = settings.autoSave,
                            onChange = viewModel::setAutoSave,
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_edit_after_capture),
                            icon = AppIcons.Edit,
                            tint = c.orange,
                            checked = settings.editAfterCapture,
                            onChange = viewModel::setEditAfterCapture,
                            showDivider = false,
                        )
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionCard(title = stringResource(R.string.settings_section_watermark)) {
                    Column {
                        val wm = watermarkDefaults
                        SwitchRow(
                            title = stringResource(R.string.watermark_field_date),
                            icon = AppIcons.CalendarMonth,
                            tint = MaterialTheme.colorScheme.primary,
                            checked = wm.date,
                            onChange = { viewModel.setWatermarkDefaults(wm.copy(date = it)) },
                        )
                        SwitchRow(
                            title = stringResource(R.string.watermark_field_purpose),
                            icon = AppIcons.Label,
                            tint = c.orange,
                            checked = wm.purpose,
                            onChange = { viewModel.setWatermarkDefaults(wm.copy(purpose = it)) },
                        )
                        SwitchRow(
                            title = stringResource(R.string.watermark_field_location),
                            icon = AppIcons.LocationOn,
                            tint = c.green,
                            checked = wm.location,
                            onChange = { viewModel.setWatermarkDefaults(wm.copy(location = it)) },
                        )
                        SwitchRow(
                            title = stringResource(R.string.watermark_field_name),
                            icon = AppIcons.Edit,
                            tint = MaterialTheme.colorScheme.primary,
                            checked = wm.name,
                            onChange = { viewModel.setWatermarkDefaults(wm.copy(name = it)) },
                        )
                        SwitchRow(
                            title = stringResource(R.string.watermark_field_custom),
                            icon = AppIcons.EditNote,
                            tint = c.orange,
                            checked = wm.custom,
                            onChange = { viewModel.setWatermarkDefaults(wm.copy(custom = it)) },
                            showDivider = false,
                        )
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionCard(title = stringResource(R.string.settings_section_journey)) {
                    Column {
                        NavRow(
                            title = stringResource(R.string.settings_arrival_radius),
                            subtitle = stringResource(R.string.settings_arrival_radius_sub),
                            leadingIcon = AppIcons.LocationOn,
                            leadingTint = MaterialTheme.colorScheme.primary,
                            trailingText = if (arrivalRadius >= 1000) "1km" else "${arrivalRadius}m",
                            onClick = { viewModel.cycleArrivalRadius() },
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_voice_guide),
                            icon = AppIcons.VolumeOn,
                            tint = c.green,
                            checked = voiceGuide,
                            onChange = viewModel::setVoiceGuide,
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_cloud_voice),
                            icon = AppIcons.VolumeOn,
                            tint = c.green,
                            checked = cloudVoice,
                            onChange = viewModel::setCloudVoice,
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_floating_bubble),
                            subtitle = stringResource(R.string.settings_floating_bubble_sub),
                            icon = AppIcons.CameraAlt,
                            tint = MaterialTheme.colorScheme.primary,
                            checked = floatingLauncher,
                            onChange = { on ->
                                if (on && android.provider.Settings.canDrawOverlays(context).not()) {
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                android.net.Uri.parse("package:${context.packageName}"),
                                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                    // 권한 부여 후 다시 켜달라는 의미로 일단 끔 상태 유지.
                                    viewModel.setFloatingLauncher(false)
                                } else {
                                    viewModel.setFloatingLauncher(on)
                                    if (on) {
                                        com.naury.trubbi.widget.FloatingLauncherService.start(context)
                                    } else {
                                        com.naury.trubbi.widget.FloatingLauncherService.stop(context)
                                    }
                                }
                            },
                        )
                        // 하위 옵션 — 플로팅 버블이 켜져 있을 때만 활성.
                        SwitchRow(
                            title = stringResource(R.string.settings_journey_control),
                            subtitle = stringResource(R.string.settings_journey_control_sub),
                            icon = AppIcons.Truck,
                            tint = c.accent2,
                            checked = floatingControl,
                            enabled = floatingLauncher,
                            indent = true,
                            onChange = { viewModel.setFloatingControl(it) },
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_quick_notification),
                            subtitle = stringResource(R.string.settings_quick_notification_sub),
                            icon = AppIcons.Description,
                            tint = c.green,
                            checked = quickNotification,
                            onChange = { on ->
                                viewModel.setQuickNotification(on)
                                if (on) {
                                    com.naury.trubbi.widget.QuickNotifier.show(context)
                                } else {
                                    com.naury.trubbi.widget.QuickNotifier.hide(context)
                                }
                            },
                            showDivider = false,
                        )
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionCard(title = stringResource(R.string.settings_section_purposes)) {
                    NavRow(
                        title = stringResource(R.string.settings_manage_purposes),
                        subtitle = stringResource(R.string.settings_manage_purposes_sub, purposes.size),
                        leadingIcon = AppIcons.Label,
                        leadingTint = MaterialTheme.colorScheme.primary,
                        showDivider = false,
                        onClick = onOpenPurposes,
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionCard(title = stringResource(R.string.settings_section_photo_quality)) {
                    Column {
                        NavRow(
                            title = stringResource(R.string.settings_save_quality),
                            leadingIcon = AppIcons.HighQuality,
                            leadingTint = c.secondaryLabel,
                            showChevron = false,
                            showDivider = false,
                            onClick = null,
                        )
                        Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                            SegmentedControl(
                                options = PhotoQuality.entries.map { it.label() },
                                selectedIndex = settings.photoQuality.ordinal,
                                onSelected = { idx -> viewModel.setPhotoQuality(PhotoQuality.entries[idx]) },
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionCard(title = stringResource(R.string.settings_section_backup)) {
                    Column {
                        NavRow(
                            title = stringResource(R.string.settings_export_backup),
                            subtitle = stringResource(R.string.settings_export_backup_sub),
                            leadingIcon = AppIcons.Backup,
                            leadingTint = MaterialTheme.colorScheme.primary,
                            onClick = { exportLauncher.launch("trubbi_backup_${DateUtils.todayIso()}.zip") },
                        )
                        NavRow(
                            title = stringResource(R.string.settings_import_backup),
                            subtitle = stringResource(R.string.settings_import_backup_sub),
                            leadingIcon = AppIcons.Restore,
                            leadingTint = c.green,
                            showDivider = false,
                            onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        )
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionCard(title = stringResource(R.string.settings_section_manage)) {
                    Column {
                        NavRow(
                            title = stringResource(R.string.settings_contacts),
                            subtitle = stringResource(R.string.settings_contacts_sub),
                            leadingIcon = AppIcons.Contacts,
                            leadingTint = MaterialTheme.colorScheme.primary,
                            onClick = onOpenContacts,
                        )
                        NavRow(
                            title = stringResource(R.string.settings_widgets),
                            subtitle = stringResource(R.string.settings_widgets_sub),
                            leadingIcon = AppIcons.Widgets,
                            leadingTint = c.secondaryLabel,
                            showChevron = false,
                            showDivider = !adPrivacyRequired,
                            onClick = null,
                        )
                        // 동의가 필요한 지역에서만 보인다(UMP 가 정한다).
                        if (adPrivacyRequired) {
                            NavRow(
                                title = stringResource(R.string.settings_ad_privacy),
                                subtitle = stringResource(R.string.settings_ad_privacy_sub),
                                leadingIcon = AppIcons.Lock,
                                leadingTint = c.secondaryLabel,
                                showDivider = false,
                                onClick = { (context as? android.app.Activity)?.let { ads?.showPrivacyOptions(it) } },
                            )
                        }
                    }
                }
            }
        }

        item(key = "ad_banner") { com.naury.trubbi.feature.ads.BannerAd(Modifier.padding(horizontal = 16.dp)) }

        item {
            Text(
                stringResource(R.string.settings_footer),
                style = MaterialTheme.typography.labelMedium,
                color = c.secondaryLabel,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    icon: ImageVector,
    tint: Color,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    showDivider: Boolean = true,
    subtitle: String? = null,
    enabled: Boolean = true,
    indent: Boolean = false,
) {
    val c = LocalTrubbiColors.current
    Column(Modifier.alpha(if (enabled) 1f else 0.45f)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = if (indent) 28.dp else 16.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.secondaryLabel,
                    )
                }
            }
            com.naury.kinetica.ui.KSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 58.dp)
                    .height(0.6.dp)
                    .background(c.separator),
            )
        }
    }
}
