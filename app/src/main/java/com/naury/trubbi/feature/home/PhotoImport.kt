package com.naury.trubbi.feature.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.LocalAppToast
import com.naury.trubbi.ui.designsystem.TrubbiConfirmDialog

/**
 * 앨범에서 사진 가져오기. 부르면 고르는 화면이 뜨는 함수를 돌려준다.
 *
 * 사진 선택은 시스템 문서 선택기(SAF)로 한다. 안드로이드 사진 선택기(Photo Picker)는 권한이 있어도 촬영 위치를
 * 지운 사본을 넘겨, 가져온 사진을 장소로 찾을 수 없었다. 문서 선택기는 "사진 위치 정보" 권한이 있으면 원본을 준다.
 *
 * 그 권한은 처음 한 번만, 왜 필요한지 먼저 설명하고 묻는다. 시스템 창이 "사진 및 동영상 접근"처럼 넓게 보여서
 * 설명 없이 띄우면 앨범 전체를 달라는 것으로 오해하기 쉽다. 거절해도 가져오기는 그대로 한다(위치만 빠진다).
 *
 * @param library 가져오기를 맡을 목록 컨트롤러
 * @param onOpenRecord 한 장만 가져왔을 때 그 기록을 연다
 */
@Composable
internal fun rememberPhotoImport(library: HomeLibraryController, onOpenRecord: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val res = LocalResources.current
    val toast = LocalAppToast.current
    var askRationale by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        library.importPhotos(uris.take(IMPORT_MAX)) { ids ->
            if (ids.size == 1) {
                onOpenRecord(ids.first())
            } else {
                toast?.show(res.getQuantityString(R.plurals.import_photos_done, ids.size, ids.size))
            }
        }
    }
    val launchPicker = { picker.launch(arrayOf(MIME_IMAGES)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { launchPicker() }

    if (askRationale) {
        TrubbiConfirmDialog(
            title = stringResource(R.string.import_location_title),
            message = stringResource(R.string.import_location_body),
            confirmText = stringResource(R.string.import_location_allow),
            dismissText = stringResource(R.string.import_location_skip),
            icon = AppIcons.LocationOn,
            onConfirm = {
                askRationale = false
                markAsked(context)
                permission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
            },
            onDismiss = {
                askRationale = false
                markAsked(context)
                launchPicker()
            },
        )
    }

    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
        when {
            granted || wasAsked(context) -> launchPicker()
            else -> askRationale = true
        }
    }
}

private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

private fun wasAsked(context: Context) = prefs(context).getBoolean(KEY_ASKED, false)

private fun markAsked(context: Context) = prefs(context).edit { putBoolean(KEY_ASKED, true) }

/** 한 번에 가져올 수 있는 사진 수. */
private const val IMPORT_MAX = 30
private const val MIME_IMAGES = "image/*"
private const val PREFS = "photo_import"
private const val KEY_ASKED = "media_location_asked"
