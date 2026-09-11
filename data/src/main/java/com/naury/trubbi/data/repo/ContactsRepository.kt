package com.naury.trubbi.data.repo

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.naury.trubbi.core.util.DateUtils
import com.naury.trubbi.data.local.ContactEntity
import com.naury.trubbi.data.local.TrubbiDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 거래처·기사 연락처 저장소.
 *
 * 기기 주소록을 조회할 때마다 읽지 않고 앱 DB 에 복사해 둔다. 앱 전용 메모·태그를 붙일 수 있고,
 * 조회 시점마다 `READ_CONTACTS` 권한이 필요하지 않다.
 * 주소록에서 가져온 항목은 원본 id 를 보관해 중복 저장을 막는다.
 *
 * @property context 주소록 조회용 ContentResolver
 * @property dao 연락처 테이블
 */
@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrubbiDao,
) {
    fun observeContacts(): Flow<List<ContactEntity>> = dao.observeContacts()

    suspend fun delete(id: String) = dao.deleteContact(id)

    suspend fun addManual(
        name: String,
        company: String?,
        type: String?,
        phones: List<String>,
        memo: String?,
    ) {
        val now = DateUtils.now()
        dao.insertContact(
            ContactEntity(
                contactId = UUID.randomUUID().toString(),
                deviceContactId = null,
                name = name.trim(),
                company = company?.ifBlank { null },
                type = type,
                phones = phones.filter { it.isNotBlank() },
                memo = memo?.ifBlank { null },
                tags = emptyList(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /**
     * 기기 주소록에서 고른 연락처를 읽어 저장한다. `READ_CONTACTS` 권한이 필요하다.
     *
     * @param uri 연락처 선택기가 돌려준 uri
     */
    suspend fun importFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var deviceId: String? = null
        var name = ""
        resolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                if (idIdx >= 0) deviceId = c.getString(idIdx)
                if (nameIdx >= 0) c.getString(nameIdx)?.let { name = it }
            }
        }
        if (deviceId == null) return@withContext false

        val phones = mutableListOf<String>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(deviceId),
            null,
        )?.use { p ->
            val numIdx = p.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (p.moveToNext()) {
                if (numIdx >= 0) p.getString(numIdx)?.let { phones.add(it) }
            }
        }

        val now = DateUtils.now()
        dao.insertContact(
            ContactEntity(
                contactId = UUID.randomUUID().toString(),
                deviceContactId = deviceId,
                name = name,
                company = null,
                type = null,
                phones = phones.distinct(),
                memo = null,
                tags = emptyList(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        true
    }
}
