package com.naury.trubbi.feature.contact

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.trubbi.data.local.ContactEntity
import com.naury.trubbi.data.repo.ContactsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 연락처 화면 상태.
 *
 * @property repo 연락처 저장소
 */
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repo: ContactsRepository,
) : ViewModel() {

    val contacts = repo.observeContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<ContactEntity>())

    fun importFromUri(uri: Uri) = viewModelScope.launch { repo.importFromUri(uri) }

    /**
     * 주소록을 거치지 않고 직접 연락처를 만든다.
     *
     * @param name 이름
     * @param company 회사·소속
     * @param type 분류(화주·기사·정비 등)
     * @param phone 전화번호
     * @param memo 메모
     */
    fun addManual(name: String, company: String, type: String, phone: String, memo: String) =
        viewModelScope.launch {
            repo.addManual(
                name = name,
                company = company,
                type = type.ifBlank { null },
                phones = phone.split(",").map { it.trim() }.filter { it.isNotBlank() },
                memo = memo,
            )
        }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
}
