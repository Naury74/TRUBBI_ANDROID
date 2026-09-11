package com.naury.trubbi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 기록·미디어·연락처를 다루는 DAO.
 *
 * 삭제는 `isDeleted` 플래그로만 처리한다. 실제 행 삭제는 휴지통 비우기 경로에서만 일어난다.
 */
@Dao
interface TrubbiDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: RecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaEntity)

    @Upsert
    suspend fun upsertRecord(record: RecordEntity)

    @Transaction
    @Query("SELECT * FROM records WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeRecords(): Flow<List<RecordWithMedia>>

    @Transaction
    @Query("SELECT * FROM records WHERE isDeleted = :deleted ORDER BY createdAt DESC")
    fun observeRecordsByDeleted(deleted: Boolean): Flow<List<RecordWithMedia>>

    @Transaction
    @Query("SELECT * FROM records WHERE recordId = :id LIMIT 1")
    fun observeRecord(id: String): Flow<RecordWithMedia?>

    @Transaction
    @Query("SELECT * FROM records WHERE isDeleted = 0 AND jobDate = :jobDate ORDER BY createdAt DESC")
    fun observeRecordsByDate(jobDate: String): Flow<List<RecordWithMedia>>

    @Transaction
    @Query(
        """
        SELECT * FROM records
        WHERE isDeleted = 0 AND (
            title LIKE '%' || :q || '%'
            OR memo LIKE '%' || :q || '%'
            OR tags LIKE '%' || :q || '%'
            OR locationName LIKE '%' || :q || '%'
            OR barcodeValue LIKE '%' || :q || '%'
            OR purpose LIKE '%' || :q || '%'
            OR ocrText LIKE '%' || :q || '%'
            OR aiLabels LIKE '%' || :q || '%'
        )
        ORDER BY createdAt DESC
        """,
    )
    fun search(q: String): Flow<List<RecordWithMedia>>

    @Query(
        """
        SELECT jobDate AS jobDate, COUNT(*) AS count FROM records
        WHERE isDeleted = 0 AND jobDate BETWEEN :start AND :end
        GROUP BY jobDate
        """,
    )
    fun observeDateCounts(start: String, end: String): Flow<List<DateCount>>

    @Query("UPDATE records SET isFavorite = :fav, updatedAt = :now WHERE recordId = :id")
    suspend fun setFavorite(id: String, fav: Boolean, now: Long)

    @Query("UPDATE records SET latitude = :lat, longitude = :lng, locationName = :name, updatedAt = :now WHERE recordId = :id")
    suspend fun updateLocation(id: String, lat: Double?, lng: Double?, name: String?, now: Long)

    @Query("UPDATE records SET memo = :memo, updatedAt = :now WHERE recordId = :id")
    suspend fun updateMemo(id: String, memo: String?, now: Long)

    @Query("UPDATE records SET title = :title, memo = :memo, updatedAt = :now WHERE recordId = :id")
    suspend fun updateMemoAndTitle(id: String, title: String, memo: String?, now: Long)

    @Query("UPDATE records SET tags = :tags, updatedAt = :now WHERE recordId = :id")
    suspend fun updateTags(id: String, tags: List<String>, now: Long)

    @Query("UPDATE records SET title = :title, purpose = :purpose, jobDate = :jobDate, updatedAt = :now WHERE recordId = :id")
    suspend fun updateMeta(id: String, title: String, purpose: String, jobDate: String, now: Long)

    @Query("UPDATE records SET isDeleted = 1, updatedAt = :now WHERE recordId = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE records SET isDeleted = 0, updatedAt = :now WHERE recordId = :id")
    suspend fun restore(id: String, now: Long)

    @Query("DELETE FROM records WHERE recordId = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM media WHERE mediaId = :id")
    suspend fun deleteMedia(id: String)

    @Query("SELECT COUNT(*) FROM records WHERE isDeleted = 0 AND jobDate = :jobDate AND type = :type")
    fun countByDateAndType(jobDate: String, type: String): Flow<Int>

    @Query("UPDATE records SET ocrText = :ocrText, aiLabels = :aiLabels, tags = :tags, updatedAt = :now WHERE recordId = :id")
    suspend fun updateAnalysis(id: String, ocrText: String?, aiLabels: String?, tags: List<String>, now: Long)

    // ── 연락처
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE ASC")
    fun observeContacts(): Flow<List<ContactEntity>>

    @Query("DELETE FROM contacts WHERE contactId = :id")
    suspend fun deleteContact(id: String)

    @Query("SELECT * FROM records WHERE isDeleted = 0 AND type = 'BARCODE' AND barcodeValue IS NOT NULL ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestBarcode(): RecordEntity?

    @Query("SELECT * FROM records WHERE recordId = :id LIMIT 1")
    suspend fun recordOnce(id: String): RecordEntity?

    @Transaction
    @Query("SELECT * FROM records WHERE isDeleted = 0 AND type = 'BARCODE' AND barcodeValue IS NOT NULL ORDER BY createdAt DESC")
    fun observeBarcodes(): kotlinx.coroutines.flow.Flow<List<RecordWithMedia>>

    @Query("SELECT * FROM records WHERE isDeleted = 0")
    suspend fun allRecordsOnce(): List<RecordEntity>

    @Query("SELECT * FROM media")
    suspend fun allMediaOnce(): List<MediaEntity>

    @Query("SELECT * FROM contacts")
    suspend fun allContactsOnce(): List<ContactEntity>
}
