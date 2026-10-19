package com.naury.trubbi.feature.contact
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.trubbi.R
import com.naury.trubbi.data.local.ContactEntity
import com.naury.trubbi.domain.model.RecordType
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassTopBar
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.TrubbiPopupCard
import com.naury.trubbi.ui.designsystem.liquidGlass
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 연락처 화면 — 거래처·기사 연락처를 모아 둔다.
 *
 * @param onBack 뒤로 갈 때
 * @param contentPadding 시스템 바를 피하기 위한 여백
 * @param viewModel 연락처 상태
 */
@Composable
fun ContactsScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.contacts_share_title)
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<ContactEntity?>(null) }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) viewModel.importFromUri(uri)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickContact.launch(null)
    }
    val onImport = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            pickContact.launch(null)
        } else {
            permission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        GlassTopBar(
            title = stringResource(R.string.contacts_title),
            onBack = onBack,
            actions = {
                Icon(
                    AppIcons.Add,
                    contentDescription = stringResource(R.string.contacts_add_manual),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { showAdd = true },
                )
            },
        )
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(stringResource(R.string.contacts_import_device), onClick = { onImport() }, modifier = Modifier.weight(1f))
            PillButton(
                stringResource(R.string.contacts_add_manual),
                onClick = { showAdd = true },
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (contacts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.contacts_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(contacts, key = { it.contactId }) { contact ->
                    ContactRow(contact, onClick = { actionTarget = contact })
                }
            }
        }
    }

    if (showAdd) {
        AddContactDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, company, type, phone, memo ->
                viewModel.addManual(name, company, type, phone, memo)
                showAdd = false
            },
        )
    }

    actionTarget?.let { contact ->
        ContactActionDialog(
            contact = contact,
            onDismiss = { actionTarget = null },
            onCall = { number ->
                context.dial(number)
                actionTarget = null
            },
            onSms = { number ->
                context.sms(number)
                actionTarget = null
            },
            onExport = {
                context.exportToDevice(contact)
                actionTarget = null
            },
            onShare = {
                context.shareContact(contact, shareTitle)
                actionTarget = null
            },
            onDelete = {
                viewModel.delete(contact.contactId)
                actionTarget = null
            },
        )
    }
}

@Composable
private fun ContactRow(contact: ContactEntity, onClick: () -> Unit) {
    val c = LocalTrubbiColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                contact.name.ifBlank { stringResource(R.string.profile_no_name) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val sub = listOfNotNull(contact.company, contact.phones.firstOrNull()).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel)
            }
        }
        if (!contact.type.isNullOrBlank()) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    contact.type.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ContactActionDialog(
    contact: ContactEntity,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val number = contact.phones.firstOrNull()
    TrubbiPopupCard(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                contact.name.ifBlank { stringResource(R.string.profile_no_name) },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            PopupClose(onDismiss)
        }
        if (number != null) {
            ActionRow(AppIcons.Phone, stringResource(R.string.contacts_call)) { onCall(number) }
            ActionRow(AppIcons.Message, stringResource(R.string.contacts_message)) { onSms(number) }
        }
        ActionRow(AppIcons.Save, stringResource(R.string.contacts_export_device)) { onExport() }
        ActionRow(AppIcons.Share, stringResource(R.string.action_share)) { onShare() }
        ActionRow(AppIcons.Delete, stringResource(R.string.action_delete), destructive = true) { onDelete() }
    }
}

@Composable
private fun PopupClose(onDismiss: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AppIcons.Close,
            contentDescription = stringResource(R.string.action_close),
            tint = LocalTrubbiColors.current.secondaryLabel,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val c = LocalTrubbiColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 11.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (destructive) c.red else MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (destructive) c.red else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, company: String, type: String, phone: String, memo: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(16.dp)
    TrubbiPopupCard(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.contacts_add_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            PopupClose(onDismiss)
        }
        Column(
            Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(name, {
                name = it
            }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(company, {
                company = it
            }, label = { Text(stringResource(R.string.contacts_field_company)) }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(type, {
                type = it
            }, label = { Text(stringResource(R.string.contacts_field_type)) }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, {
                phone = it
            }, label = { Text(stringResource(R.string.contacts_field_phones)) }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(memo, {
                memo = it
            }, label = { Text(stringResource(RecordType.MEMO.labelRes)) }, shape = shape, modifier = Modifier.fillMaxWidth())
        }
        PillButton(stringResource(R.string.action_save), onClick = {
            onConfirm(name, company, type, phone, memo)
        }, modifier = Modifier.fillMaxWidth(), icon = AppIcons.Save)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable { onDismiss() }.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.action_cancel),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── 전화·문자·공유 인텐트
private fun Context.dial(number: String) =
    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))

private fun Context.sms(number: String) =
    startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")))

private fun Context.exportToDevice(contact: ContactEntity) {
    val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
        type = ContactsContract.RawContacts.CONTENT_TYPE
        putExtra(ContactsContract.Intents.Insert.NAME, contact.name)
        contact.phones.firstOrNull()?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
        contact.company?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
    }
    startActivity(intent)
}

private fun Context.shareContact(contact: ContactEntity, chooserTitle: String) {
    val text = buildString {
        appendLine(contact.name)
        contact.company?.let { appendLine(it) }
        contact.phones.forEach { appendLine(it) }
    }.trim()
    startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            chooserTitle,
        ),
    )
}
