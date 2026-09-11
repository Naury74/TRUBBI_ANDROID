package com.naury.trubbi.data.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.naury.trubbi.core.voice.VoicePreferences
import com.naury.trubbi.core.weather.Weather
import com.naury.trubbi.core.weather.weatherFromCode
import com.naury.trubbi.data.R
import com.naury.trubbi.domain.model.Labeled
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 공유 시 이미지에 새길 워터마크 항목 기본 선택값(사용자가 앱 설정에서 지정). 기본은 날짜만. */
data class WatermarkDefaults(
    val date: Boolean = true,
    val purpose: Boolean = false,
    val location: Boolean = false,
    val name: Boolean = false,
    val custom: Boolean = false,
    val customText: String = "",
)

/**
 * 사진 저장 품질. 값이 낮을수록 파일이 작아진다.
 *
 * @property labelRes 설정 화면 표시 이름
 * @property jpegQuality JPEG 압축 품질(0~100)
 */
enum class PhotoQuality(@StringRes override val labelRes: Int, val jpegQuality: Int) : Labeled {
    ORIGINAL(R.string.quality_original, 100),
    NORMAL(R.string.quality_normal, 90),
    SMALL(R.string.quality_small, 70),
}

data class TrubbiSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultPurpose: String = "ETC",
    val saveLocation: Boolean = false,
    val autoSave: Boolean = true,
    val editAfterCapture: Boolean = false,
    val photoQuality: PhotoQuality = PhotoQuality.NORMAL,
)

/**
 * 홈·설정 상단에 보이는 최소한의 프로필. 로그인 계정과는 별개로 기기에만 저장된다.
 *
 * @property name 표시 이름
 * @property photoUri 프로필 사진 uri
 * @property onboarded 첫 실행 안내를 끝냈는지
 */
data class UserProfile(
    val name: String = "",
    val photoUri: String? = null,
    val onboarded: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "trubbi_settings")

/**
 * 앱 설정 저장소. DataStore Preferences 한 파일에 전부 들어간다.
 *
 * 설정 항목마다 `Flow<T>` 읽기와 `set…` 쓰기를 한 쌍으로 둔다. 값이 어느 경로에서 바뀌든
 * 구독 중인 화면에 그대로 전달된다.
 *
 * 기본값은 쓰기 시점이 아니라 읽는 쪽(`?: 기본값`)에 둔다. 최초 실행 시 초기화 쓰기가 필요 없고,
 * 기본값을 변경하면 아직 값을 저장하지 않은 사용자에게 즉시 반영된다.
 *
 * @property context DataStore 와 기본 용도 목록(문자열 배열 리소스) 접근용 (Hilt 주입)
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : VoicePreferences {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val PURPOSE = stringPreferencesKey("default_purpose")
        val LOCATION = booleanPreferencesKey("save_location")
        val AUTOSAVE = booleanPreferencesKey("auto_save")
        val EDIT_AFTER = booleanPreferencesKey("edit_after_capture")
        val QUALITY = intPreferencesKey("photo_quality")
        val PURPOSES = stringPreferencesKey("purposes")
        val GRID_COLUMNS = intPreferencesKey("album_grid_columns")
        val USER_NAME = stringPreferencesKey("user_name")
        val PROFILE_URI = stringPreferencesKey("profile_uri")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val WEATHER_CODE = intPreferencesKey("weather_code")
        val WEATHER_TEMP = intPreferencesKey("weather_temp")
        val WEATHER_ISDAY = booleanPreferencesKey("weather_isday")
        val WEATHER_PLACE = stringPreferencesKey("weather_place")
        val ARRIVAL_RADIUS = intPreferencesKey("arrival_radius_m")
        val VOICE_GUIDE = booleanPreferencesKey("voice_guide")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val BACKUP_WIFI_ONLY = booleanPreferencesKey("backup_wifi_only")
        val WELCOME_SEEN = booleanPreferencesKey("welcome_seen")
        val CLOUD_VOICE = booleanPreferencesKey("cloud_voice")
        val FLOATING_CONTROL = booleanPreferencesKey("floating_control")
        val FLOATING_LAUNCHER = booleanPreferencesKey("floating_launcher")
        val QUICK_NOTIFICATION = booleanPreferencesKey("quick_notification")
        val TERMS_AGREED = booleanPreferencesKey("terms_agreed")
        val WM_DATE = booleanPreferencesKey("wm_date")
        val WM_PURPOSE = booleanPreferencesKey("wm_purpose")
        val WM_LOCATION = booleanPreferencesKey("wm_location")
        val WM_NAME = booleanPreferencesKey("wm_name")
        val WM_CUSTOM = booleanPreferencesKey("wm_custom")
        val WM_CUSTOM_TEXT = stringPreferencesKey("wm_custom_text")
    }

    /** 공유 워터마크 기본 선택값(앱 설정에서 변경, 공유 화면 초기값으로 사용). */
    val watermarkDefaults: Flow<WatermarkDefaults> = context.dataStore.data.map { p ->
        WatermarkDefaults(
            date = p[Keys.WM_DATE] ?: true,
            purpose = p[Keys.WM_PURPOSE] ?: false,
            location = p[Keys.WM_LOCATION] ?: false,
            name = p[Keys.WM_NAME] ?: false,
            custom = p[Keys.WM_CUSTOM] ?: false,
            customText = p[Keys.WM_CUSTOM_TEXT] ?: "",
        )
    }

    suspend fun setWatermarkDefaults(w: WatermarkDefaults) =
        context.dataStore.edit { p ->
            p[Keys.WM_DATE] = w.date
            p[Keys.WM_PURPOSE] = w.purpose
            p[Keys.WM_LOCATION] = w.location
            p[Keys.WM_NAME] = w.name
            p[Keys.WM_CUSTOM] = w.custom
            if (w.customText.isBlank()) p.remove(Keys.WM_CUSTOM_TEXT) else p[Keys.WM_CUSTOM_TEXT] = w.customText
        }.let {}

    /** 현재 버전 약관에 동의했는지. 로그인 뒤 동의 화면을 띄울지 결정한다. */
    val termsAgreed: Flow<Boolean> = context.dataStore.data.map { it[Keys.TERMS_AGREED] ?: false }
    suspend fun setTermsAgreed(on: Boolean) =
        context.dataStore.edit { it[Keys.TERMS_AGREED] = on }.let {}

    /** 다른 앱 위에 뜨는 조작 버튼. 오버레이 권한이 필요하다. 기본 꺼짐. */
    val floatingControl: Flow<Boolean> = context.dataStore.data.map { it[Keys.FLOATING_CONTROL] ?: false }
    suspend fun setFloatingControl(on: Boolean) =
        context.dataStore.edit { it[Keys.FLOATING_CONTROL] = on }.let {}

    /** Always-on floating quick-launcher bubble (앱 열기·촬영·문서·바코드·메모). Needs overlay permission. Default off. */
    val floatingLauncher: Flow<Boolean> = context.dataStore.data.map { it[Keys.FLOATING_LAUNCHER] ?: false }
    suspend fun setFloatingLauncher(on: Boolean) =
        context.dataStore.edit { it[Keys.FLOATING_LAUNCHER] = on }.let {}

    /** Persistent quick-record notification (촬영·문서·바코드·메모 actions in the shade). No overlay perm needed. Default off. */
    val quickNotification: Flow<Boolean> = context.dataStore.data.map { it[Keys.QUICK_NOTIFICATION] ?: false }
    suspend fun setQuickNotification(on: Boolean) =
        context.dataStore.edit { it[Keys.QUICK_NOTIFICATION] = on }.let {}

    /** 첫 실행 환영 화면을 이미 봤는지. */
    val welcomeSeen: Flow<Boolean> = context.dataStore.data.map { it[Keys.WELCOME_SEEN] ?: false }
    suspend fun setWelcomeSeen() =
        context.dataStore.edit { it[Keys.WELCOME_SEEN] = true }.let {}

    /** 클라우드 음성 사용 여부. 기본 true. false 면 기기 TTS 폴백 없이 무음으로 동작한다. */
    val cloudVoice: Flow<Boolean> = context.dataStore.data.map { it[Keys.CLOUD_VOICE] ?: true }

    /** [VoicePreferences] 구현 — 음성 계층이 저장소 타입을 알지 않도록 인터페이스로만 노출한다. */
    override suspend fun useCloudVoice(): Boolean = cloudVoice.first()
    suspend fun setCloudVoice(on: Boolean) =
        context.dataStore.edit { it[Keys.CLOUD_VOICE] = on }.let {}

    /** 자동 클라우드 백업 사용 여부. 기본 꺼짐. */
    val autoBackup: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_BACKUP] ?: false }
    suspend fun setAutoBackup(on: Boolean) =
        context.dataStore.edit { it[Keys.AUTO_BACKUP] = on }.let {}

    /** 자동 백업을 종량제가 아닌 망에서만 실행할지. 기본 true. */
    val backupWifiOnly: Flow<Boolean> = context.dataStore.data.map { it[Keys.BACKUP_WIFI_ONLY] ?: true }
    suspend fun setBackupWifiOnly(on: Boolean) =
        context.dataStore.edit { it[Keys.BACKUP_WIFI_ONLY] = on }.let {}

    /** 도착 알림을 띄울 반경(m). */
    val arrivalRadiusM: Flow<Int> = context.dataStore.data.map { it[Keys.ARRIVAL_RADIUS] ?: 300 }

    suspend fun setArrivalRadius(m: Int) =
        context.dataStore.edit { it[Keys.ARRIVAL_RADIUS] = m }.let {}

    /** 여정 음성 안내 사용 여부. */
    val voiceGuide: Flow<Boolean> = context.dataStore.data.map { it[Keys.VOICE_GUIDE] ?: true }

    suspend fun setVoiceGuide(on: Boolean) =
        context.dataStore.edit { it[Keys.VOICE_GUIDE] = on }.let {}

    /** 마지막으로 조회한 날씨. 새 값을 받아오는 동안 이 값이 먼저 표시된다. */
    val lastWeather: Flow<Weather?> = context.dataStore.data.map { p ->
        val code = p[Keys.WEATHER_CODE] ?: return@map null
        val temp = p[Keys.WEATHER_TEMP] ?: return@map null
        weatherFromCode(code, p[Keys.WEATHER_ISDAY] ?: true, temp, p[Keys.WEATHER_PLACE])
    }

    suspend fun saveWeather(w: Weather) =
        context.dataStore.edit { p ->
            p[Keys.WEATHER_CODE] = w.code
            p[Keys.WEATHER_TEMP] = w.temperatureC
            p[Keys.WEATHER_ISDAY] = w.isDay
            // 다른 모듈의 val 이라 스마트 캐스트가 안 된다. 한 번 읽어 두고 쓴다.
            val place = w.place
            if (place.isNullOrBlank()) p.remove(Keys.WEATHER_PLACE) else p[Keys.WEATHER_PLACE] = place
        }.let {}

    /** 이름·사진 프로필. 첫 실행 안내를 마치기 전에는 비어 있다. */
    val profile: Flow<UserProfile> = context.dataStore.data.map { p ->
        UserProfile(
            name = p[Keys.USER_NAME] ?: "",
            photoUri = p[Keys.PROFILE_URI],
            onboarded = p[Keys.ONBOARDED] ?: false,
        )
    }

    suspend fun setUserName(name: String) =
        context.dataStore.edit { it[Keys.USER_NAME] = name.trim() }.let {}

    suspend fun setProfilePhoto(uri: String?) =
        context.dataStore.edit { p ->
            if (uri.isNullOrBlank()) p.remove(Keys.PROFILE_URI) else p[Keys.PROFILE_URI] = uri
        }.let {}

    /** 이름·사진을 저장하고 첫 실행 안내를 끝난 것으로 표시한다. */
    suspend fun completeOnboarding(name: String, photoUri: String?) =
        context.dataStore.edit { p ->
            p[Keys.USER_NAME] = name.trim()
            if (!photoUri.isNullOrBlank()) p[Keys.PROFILE_URI] = photoUri
            p[Keys.ONBOARDED] = true
        }.let {}

    /** 앨범 그리드 열 수. 0 이면 화면 너비에 따라 자동 계산한다. */
    val gridColumns: Flow<Int> = context.dataStore.data.map { it[Keys.GRID_COLUMNS] ?: 0 }

    suspend fun setGridColumns(n: Int) {
        context.dataStore.edit { it[Keys.GRID_COLUMNS] = n.coerceIn(0, 6) }
    }

    /** 사용자가 편집하는 용도 목록. 아직 저장된 게 없으면 기본값을 돌려준다. */
    val purposes: Flow<List<String>> = context.dataStore.data.map { p ->
        p[Keys.PURPOSES]
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: defaultPurposes()
    }

    /** 첫 실행 때 채워 넣을 기본 용도. 기기 언어에 맞춰 리소스에서 읽는다. */
    fun defaultPurposes(): List<String> =
        context.resources.getStringArray(R.array.default_purposes).toList()

    /** 여정의 도착·작업 증빙과 직접 묶여 있어 지울 수 없는 용도. */
    fun protectedPurposes(): Set<String> =
        context.resources.getStringArray(R.array.protected_purposes).toSet()

    private suspend fun savePurposes(list: List<String>) {
        context.dataStore.edit { it[Keys.PURPOSES] = list.joinToString("\n") }
    }

    suspend fun addPurpose(label: String) {
        val clean = label.trim()
        if (clean.isBlank()) return
        val current = purposes.firstValue()
        if (current.any { it.equals(clean, ignoreCase = true) }) return
        savePurposes(current + clean)
    }

    suspend fun removePurpose(label: String) {
        // 여정 증빙 분류와 묶여 있는 용도는 지우지 못하게 막는다.
        if (label.trim() in protectedPurposes()) return
        val current = purposes.firstValue()
        savePurposes(current.filterNot { it == label })
    }

    /**
     * 용도 목록 전체를 교체한다. 순서 변경에서 호출한다.
     *
     * @param list 새 목록. 공백·중복은 걸러 내고, 전부 비면 무시한다
     */
    suspend fun setPurposes(list: List<String>) {
        val clean = list.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (clean.isEmpty()) return
        savePurposes(clean)
    }

    /**
     * 용도 하나의 위치를 옮기고 나머지 순서는 유지한다.
     *
     * @param from 옮길 항목의 현재 위치
     * @param to 옮겨 갈 위치. 범위를 벗어나면 아무 일도 하지 않는다
     */
    suspend fun movePurpose(from: Int, to: Int) {
        val current = purposes.firstValue().toMutableList()
        if (from !in current.indices || to !in current.indices || from == to) return
        val item = current.removeAt(from)
        current.add(to, item)
        savePurposes(current)
    }

    suspend fun resetPurposes() {
        context.dataStore.edit { it.remove(Keys.PURPOSES) }
    }

    private suspend fun Flow<List<String>>.firstValue(): List<String> = first()

    val settings: Flow<TrubbiSettings> = context.dataStore.data.map { p ->
        TrubbiSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            defaultPurpose = p[Keys.PURPOSE] ?: "ETC",
            saveLocation = p[Keys.LOCATION] ?: false,
            autoSave = p[Keys.AUTOSAVE] ?: true,
            editAfterCapture = p[Keys.EDIT_AFTER] ?: false,
            photoQuality = PhotoQuality.entries.getOrElse(p[Keys.QUALITY] ?: 1) { PhotoQuality.NORMAL },
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[Keys.THEME] = mode.name }.let {}

    suspend fun setDefaultPurpose(purpose: String) =
        context.dataStore.edit { it[Keys.PURPOSE] = purpose }.let {}

    suspend fun setSaveLocation(value: Boolean) =
        context.dataStore.edit { it[Keys.LOCATION] = value }.let {}

    suspend fun setAutoSave(value: Boolean) =
        context.dataStore.edit { it[Keys.AUTOSAVE] = value }.let {}

    suspend fun setEditAfterCapture(value: Boolean) =
        context.dataStore.edit { it[Keys.EDIT_AFTER] = value }.let {}

    suspend fun setPhotoQuality(quality: PhotoQuality) =
        context.dataStore.edit { it[Keys.QUALITY] = quality.ordinal }.let {}
}
