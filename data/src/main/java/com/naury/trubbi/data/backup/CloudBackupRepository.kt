package com.naury.trubbi.data.backup

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore `users/{uid}/backup_meta/current` 를 그대로 옮긴 값.
 *
 * 백업 파일은 Storage 에 있어 크기·시각을 확인하려면 내려받아야 한다.
 * 표시용 메타는 Firestore 에 따로 기록해 다운로드 없이 읽는다.
 *
 * @property lastBackupAt 마지막 백업 시각(epoch ms)
 * @property sizeBytes 백업 ZIP 크기(바이트)
 */
data class CloudBackupMeta(val lastBackupAt: Long, val sizeBytes: Long)

/**
 * 클라우드 백업 — [BackupManager] 가 만든 ZIP 을 통째로 Storage 에 올리고 메타를 Firestore 에 적는다.
 *
 * 기록을 문서 단위로 동기화하지 않고 ZIP 하나로 업로드한다. 충돌 병합이 없는 대신,
 * 복원은 마지막 백업 시점 전체를 되돌리는 방식이다.
 *
 * 로그인이 필요하며, 로그아웃 상태에서는 실패 [Result] 를 반환한다.
 * 설정 화면의 로컬 파일 백업·복원은 이 경로와 무관하게 동작한다.
 *
 * @property context 임시 ZIP 을 둘 캐시 경로
 * @property auth 현재 로그인 사용자 확인
 * @property storage 백업 ZIP 을 두는 Cloud Storage
 * @property firestore 백업 메타를 두는 Firestore
 * @property backupManager 실제 ZIP 을 만들고 되돌리는 쪽
 * @property strings 오류 문구용 리소스 접근자
 */
@Singleton
class CloudBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    private val firestore: FirebaseFirestore,
    private val backupManager: BackupManager,
    private val strings: StringProvider,
) {
    /** 로그인 상태인지. 클라우드 백업 사용 가능 여부와 같다. */
    fun isSignedIn(): Boolean = auth.currentUser != null

    private fun backupRef(uid: String) =
        storage.reference.child("users/$uid/backup/app_backup.zip")

    private fun metaDoc(uid: String) =
        firestore.collection("users").document(uid).collection("backup_meta").document("current")

    /** 마지막 백업 메타를 구독한다. 백업이 없거나 로그아웃 상태면 null 이 흐른다. */
    fun observeMeta(): Flow<CloudBackupMeta?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val reg = metaDoc(uid).addSnapshotListener { snap, _ ->
            val meta = snap?.takeIf { it.exists() }?.let {
                CloudBackupMeta(it.getLong("lastBackupAt") ?: 0L, it.getLong("sizeBytes") ?: 0L)
            }
            trySend(meta)
        }
        awaitClose { reg.remove() }
    }

    /**
     * 지금 기기의 기록을 ZIP 으로 만들어 올린다.
     *
     * @return 성공하면 백업 시각(epoch ms)
     */
    suspend fun backup(): Result<Long> = runCatching {
        val uid = auth.currentUser?.uid ?: error(strings[R.string.auth_sign_in_required])
        val tmp = File(context.cacheDir, "cloud_backup.zip")
        when (val r = backupManager.exportToFile(tmp)) {
            is BackupResult.Failed -> throw r.cause
            else -> Unit
        }
        backupRef(uid).putFile(android.net.Uri.fromFile(tmp)).await()
        val now = System.currentTimeMillis()
        runCatching {
            metaDoc(uid).set(
                mapOf("lastBackupAt" to now, "sizeBytes" to tmp.length()),
                SetOptions.merge(),
            ).await()
        }
        tmp.delete()
        now
    }

    /**
     * 클라우드에 올려 둔 ZIP 을 받아 되돌린다.
     *
     * 기존 행을 삭제하지 않고 같은 id 만 덮어쓰므로, 복원 후에도 로컬에만 있던 기록이 유지된다.
     */
    suspend fun restore(): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: error(strings[R.string.auth_sign_in_required])
        val tmp = File(context.cacheDir, "cloud_restore.zip")
        backupRef(uid).getFile(tmp).await()
        when (val r = backupManager.importFromFile(tmp)) {
            is BackupResult.Failed -> {
                tmp.delete()
                throw r.cause
            }
            else -> Unit
        }
        tmp.delete()
    }
}
