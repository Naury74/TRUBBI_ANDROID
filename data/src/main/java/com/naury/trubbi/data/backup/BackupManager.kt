package com.naury.trubbi.data.backup

import android.content.Context
import android.net.Uri
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.R
import com.naury.trubbi.data.local.ContactEntity
import com.naury.trubbi.data.local.MediaEntity
import com.naury.trubbi.data.local.RecordEntity
import com.naury.trubbi.data.local.TrubbiDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 백업·복원 결과.
 *
 * 문구가 아니라 숫자와 원인만 들고 온다. 저장 계층이 문구를 만들면 화면이 다르게 표현할 수
 * 없고, 사용자가 언어를 바꿔도 이미 만들어진 문장은 그대로 남는다.
 *
 * 실패를 예외가 아닌 값으로 돌려주므로 호출부가 반드시 결과를 확인하게 된다.
 */
sealed interface BackupResult {

    /**
     * 내보내기 성공.
     *
     * @property records 내보낸 기록 수
     * @property media 함께 넣은 사진·영상 수
     */
    data class Exported(val records: Int, val media: Int) : BackupResult

    /**
     * 되돌리기 성공.
     *
     * @property records 되살린 기록 수
     */
    data class Imported(val records: Int) : BackupResult

    /**
     * 실패.
     *
     * @property cause 원인. 화면이 사용자에게 보일 문구로 옮긴다
     */
    data class Failed(val cause: Throwable) : BackupResult
}

/**
 * 기록·미디어·연락처를 ZIP 한 덩어리로 내보내고 되돌리는 곳.
 *
 * ZIP 은 `database.json` 과 `media/` 로 구성된다. Room 파일을 그대로 복사하지 않고 JSON 으로
 * 직렬화하므로, 스키마 버전이 달라도 이전 백업을 읽을 수 있다.
 *
 * 미디어 uri 는 기기별 절대경로라 이식되지 않는다. 백업에는 파일명만 기록하고, 복원 시
 * 대상 기기의 미디어 폴더 기준으로 uri 를 재구성한다.
 *
 * @property context ContentResolver·저장 경로 접근용 (Hilt 주입)
 * @property dao 읽고 쓸 Room DAO
 * @property strings 결과 문구를 현재 언어로 만들기 위한 리소스 접근자
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrubbiDao,
    private val strings: StringProvider,
) {
    private val mediaDir: File by lazy { File(context.filesDir, "media").apply { mkdirs() } }

    /**
     * 사용자가 고른 위치로 백업 ZIP 을 쓴다.
     *
     * @param target 문서 선택기(`CreateDocument`)가 돌려준 쓰기 가능한 uri
     */
    suspend fun export(target: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val records = dao.allRecordsOnce()
            val media = dao.allMediaOnce()
            val contacts = dao.allContactsOnce()
            val json = buildJson(records, media, contacts)

            context.contentResolver.openOutputStream(target)?.use { os ->
                ZipOutputStream(BufferedOutputStream(os)).use { zos ->
                    zos.putNextEntry(ZipEntry("database.json"))
                    zos.write(json.toString().toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    media.forEach { m ->
                        val f = fileOf(m.uri)
                        if (f != null && f.exists()) {
                            zos.putNextEntry(ZipEntry("media/${f.name}"))
                            f.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            } ?: error(strings[R.string.backup_cannot_open_target])
            BackupResult.Exported(records.size, media.size)
        }.getOrElse { BackupResult.Failed(it) }
    }

    /**
     * 사용자가 고른 백업 ZIP 을 읽어 되돌린다.
     *
     * 기존 행을 삭제하지 않고 같은 id 만 덮어쓴다. 서로 다른 기기의 기록이 합쳐지며,
     * 복원 전에 있던 기록은 사라지지 않는다.
     *
     * @param source 문서 선택기(`OpenDocument`)가 돌려준 읽기 가능한 uri
     */
    suspend fun import(source: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            var jsonText: String? = null
            context.contentResolver.openInputStream(source)?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        when {
                            name == "database.json" -> jsonText = zis.readBytes().toString(Charsets.UTF_8)
                            name.startsWith("media/") -> {
                                val fileName = name.removePrefix("media/")
                                if (fileName.isNotBlank()) {
                                    val out = File(mediaDir, fileName)
                                    FileOutputStream(out).use { zis.copyTo(it) }
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: error(strings[R.string.backup_cannot_open_file])

            val text = jsonText ?: error(strings[R.string.backup_empty])
            val restored = restoreFromJson(JSONObject(text))
            BackupResult.Imported(restored)
        }.getOrElse { BackupResult.Failed(it) }
    }

    /**
     * 백업 ZIP 을 로컬 파일로 쓴다. 클라우드 업로드 전 단계에서 쓰인다.
     *
     * @param file 만들어 둘 임시 파일. 업로드가 끝나면 호출부가 지운다
     */
    suspend fun exportToFile(file: File): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val records = dao.allRecordsOnce()
            val media = dao.allMediaOnce()
            val contacts = dao.allContactsOnce()
            val json = buildJson(records, media, contacts)
            FileOutputStream(file).use { os ->
                ZipOutputStream(BufferedOutputStream(os)).use { zos ->
                    zos.putNextEntry(ZipEntry("database.json"))
                    zos.write(json.toString().toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                    media.forEach { m ->
                        val f = fileOf(m.uri)
                        if (f != null && f.exists()) {
                            zos.putNextEntry(ZipEntry("media/${f.name}"))
                            f.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            }
            BackupResult.Exported(records.size, media.size)
        }.getOrElse { BackupResult.Failed(it) }
    }

    /**
     * 내려받아 둔 백업 ZIP 파일에서 되돌린다. 클라우드 복원의 뒷단계.
     *
     * @param file 클라우드에서 받아 놓은 ZIP
     */
    suspend fun importFromFile(file: File): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            var jsonText: String? = null
            FileInputStream(file).use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        when {
                            name == "database.json" -> jsonText = zis.readBytes().toString(Charsets.UTF_8)
                            name.startsWith("media/") -> {
                                val fileName = name.removePrefix("media/")
                                if (fileName.isNotBlank()) {
                                    val out = File(mediaDir, fileName)
                                    FileOutputStream(out).use { zis.copyTo(it) }
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            val text = jsonText ?: error(strings[R.string.backup_empty])
            val restored = restoreFromJson(JSONObject(text))
            BackupResult.Imported(restored)
        }.getOrElse { BackupResult.Failed(it) }
    }

    /** 기록 한 행을 백업 JSON 으로. 비어 있는 칸은 JSON null 로 남긴다. */
    private fun recordJson(r: RecordEntity): JSONObject = JSONObject().apply {
        fun putOrNull(key: String, value: Any?) = put(key, value ?: JSONObject.NULL)
        put("recordId", r.recordId)
        put("type", r.type)
        put("title", r.title)
        put("purpose", r.purpose)
        putOrNull("memo", r.memo)
        put("jobDate", r.jobDate)
        put("createdAt", r.createdAt)
        put("updatedAt", r.updatedAt)
        putOrNull("latitude", r.latitude)
        putOrNull("longitude", r.longitude)
        putOrNull("locationName", r.locationName)
        putOrNull("locationRegion", r.locationRegion)
        putOrNull("locationPlace", r.locationPlace)
        put("tags", JSONArray(r.tags))
        putOrNull("barcodeValue", r.barcodeValue)
        putOrNull("barcodeFormat", r.barcodeFormat)
        put("isFavorite", r.isFavorite)
        put("isDeleted", r.isDeleted)
        putOrNull("ocrText", r.ocrText)
        putOrNull("aiLabels", r.aiLabels)
    }

    /** 세 테이블을 `version` 이 붙은 JSON 한 덩어리로 만든다. */
    private fun buildJson(
        records: List<RecordEntity>,
        media: List<MediaEntity>,
        contacts: List<ContactEntity>,
    ): JSONObject {
        val root = JSONObject()
        root.put("version", 1)
        root.put("records", JSONArray().apply { records.forEach { put(recordJson(it)) } })
        root.put(
            "media",
            JSONArray().apply {
                media.forEach { m ->
                    val fileName = fileOf(m.uri)?.name.orEmpty()
                    put(
                        JSONObject().apply {
                            put("mediaId", m.mediaId)
                            put("recordId", m.recordId)
                            put("fileName", fileName)
                            put("mimeType", m.mimeType)
                            put("width", m.width ?: JSONObject.NULL)
                            put("height", m.height ?: JSONObject.NULL)
                            put("fileSize", m.fileSize ?: JSONObject.NULL)
                            put("isEdited", m.isEdited)
                            put("createdAt", m.createdAt)
                        },
                    )
                }
            },
        )
        root.put(
            "contacts",
            JSONArray().apply {
                contacts.forEach { co ->
                    put(
                        JSONObject().apply {
                            put("contactId", co.contactId)
                            put("deviceContactId", co.deviceContactId ?: JSONObject.NULL)
                            put("name", co.name)
                            put("company", co.company ?: JSONObject.NULL)
                            put("type", co.type ?: JSONObject.NULL)
                            put("phones", JSONArray(co.phones))
                            put("memo", co.memo ?: JSONObject.NULL)
                            put("tags", JSONArray(co.tags))
                            put("createdAt", co.createdAt)
                            put("updatedAt", co.updatedAt)
                        },
                    )
                }
            },
        )
        return root
    }

    /** JSON 을 DB 로 되돌리고 복원한 기록 수를 돌려준다. */
    private suspend fun restoreFromJson(root: JSONObject): Int {
        val recordsArr = root.optJSONArray("records") ?: JSONArray()
        for (i in 0 until recordsArr.length()) {
            val o = recordsArr.getJSONObject(i)
            dao.insertRecord(
                RecordEntity(
                    recordId = o.getString("recordId"),
                    type = o.getString("type"),
                    title = o.getString("title"),
                    purpose = o.getString("purpose"),
                    memo = o.optStringOrNull("memo"),
                    jobDate = o.getString("jobDate"),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt"),
                    latitude = if (o.isNull("latitude")) null else o.getDouble("latitude"),
                    longitude = if (o.isNull("longitude")) null else o.getDouble("longitude"),
                    locationName = o.optStringOrNull("locationName"),
                    locationRegion = o.optStringOrNull("locationRegion"),
                    locationPlace = o.optStringOrNull("locationPlace"),
                    tags = o.optJSONArray("tags").toStringList(),
                    barcodeValue = o.optStringOrNull("barcodeValue"),
                    barcodeFormat = o.optStringOrNull("barcodeFormat"),
                    isFavorite = o.optBoolean("isFavorite", false),
                    isDeleted = o.optBoolean("isDeleted", false),
                    ocrText = o.optStringOrNull("ocrText"),
                    aiLabels = o.optStringOrNull("aiLabels"),
                ),
            )
        }

        val mediaArr = root.optJSONArray("media") ?: JSONArray()
        for (i in 0 until mediaArr.length()) {
            val o = mediaArr.getJSONObject(i)
            val fileName = o.optString("fileName")
            val newUri = if (fileName.isNotBlank()) Uri.fromFile(File(mediaDir, fileName)).toString() else ""
            dao.insertMedia(
                MediaEntity(
                    mediaId = o.getString("mediaId"),
                    recordId = o.getString("recordId"),
                    uri = newUri,
                    thumbnailUri = null,
                    mimeType = o.optString("mimeType", "image/jpeg"),
                    width = if (o.isNull("width")) null else o.optInt("width"),
                    height = if (o.isNull("height")) null else o.optInt("height"),
                    fileSize = if (o.isNull("fileSize")) null else o.optLong("fileSize"),
                    isEdited = o.optBoolean("isEdited", false),
                    originalUri = null,
                    createdAt = o.getLong("createdAt"),
                ),
            )
        }

        val contactsArr = root.optJSONArray("contacts") ?: JSONArray()
        for (i in 0 until contactsArr.length()) {
            val o = contactsArr.getJSONObject(i)
            dao.insertContact(
                ContactEntity(
                    contactId = o.getString("contactId"),
                    deviceContactId = o.optStringOrNull("deviceContactId"),
                    name = o.getString("name"),
                    company = o.optStringOrNull("company"),
                    type = o.optStringOrNull("type"),
                    phones = o.optJSONArray("phones").toStringList(),
                    memo = o.optStringOrNull("memo"),
                    tags = o.optJSONArray("tags").toStringList(),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt"),
                ),
            )
        }
        return recordsArr.length()
    }

    private fun fileOf(uriString: String): File? {
        val path = Uri.parse(uriString).path ?: return null
        return File(path)
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = ArrayList<String>(length())
        for (i in 0 until length()) list.add(getString(i))
        return list
    }
}
